package org.openhab.binding.becker.internal.socket;

import static org.openhab.binding.becker.internal.BeckerBindingConstants.JSONRPC_VERSION;
import static org.openhab.binding.becker.internal.BeckerBindingConstants.TRANSPORT_ENCODING;
import static org.openhab.binding.becker.internal.BeckerBindingConstants.TRANSPORT_ORIGIN;
import static org.openhab.binding.becker.internal.BeckerNullables.nonNull;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.openhab.binding.becker.internal.handler.BeckerBridgeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

// TODO (3) add JavaDoc and package-info

// this class provides the jsonrpc protocol layer and is used by BeckerConnection to provide the application protocol layer
// callback from socket are scheduled to reduce load on socket thread pool
// 
// future is present while connecting, session is present while connected
// upon state changes context is notified before future is completed
// @author Stefan Machura - Initial contribution
// @author Paul Frank - This class is in parts derived from the kodi binding

@NonNullByDefault
public final class BeckerSocket {

    private final Logger logger = LoggerFactory.getLogger(BeckerSocket.class);

    private final BeckerBridgeHandler bridgeHandler;

    private @Nullable Future<Session> sessionFuture = null;
    private @Nullable Session session;
    private @Nullable JsonObject response = null;
    private CountDownLatch responseLatch = new CountDownLatch(1);;
    private long lastMessageId = -1;

    public BeckerSocket(BeckerBridgeHandler bridgeHandler) {
        this.bridgeHandler = bridgeHandler;
    }

    public synchronized void initialize() {
    }

    public synchronized void dispose() {
        disconnect();
    }

    public synchronized void connect() {

        if (sessionFuture != null || session != null) {
            logger.debug("Attempt to connect while connected or connecting");
            return;
        }

        try {
            URI uri = new URI("ws", null, bridgeHandler.config().host, bridgeHandler.config().port, "/jrpc", null,
                    null);
            logger.debug("Connecting to {}", uri);
            ClientUpgradeRequest request = new ClientUpgradeRequest();
            request.setHeader("Origin", TRANSPORT_ORIGIN);
            request.setSubProtocols("binary");
            sessionFuture = bridgeHandler.webSocketClient().connect(new Socket(), uri, request);
        } catch (IOException | URISyntaxException e) {
            logger.debug("Connection failed due to {}", e, e);
            disconnect();
        }
    }

    public synchronized void disconnect() {

        if (session != null) {
            logger.debug("Closing session");
            nonNull(session).close();
            session = null;
            bridgeHandler.onDisconnect();
        }

        if (sessionFuture != null && !nonNull(sessionFuture).isDone()) {
            logger.debug("Cancelling connection attempt");
            nonNull(sessionFuture).cancel(true);
            sessionFuture = null;
            bridgeHandler.onDisconnect();
        }
    }

    // return result on success or null on failure

    public synchronized @Nullable JsonElement send(String method, JsonObject params) {
        if (session == null) {
            logger.debug("Attempt to send method '{}' while disconnected or connecting", method);
            return null;
        }

        try {
            logger.debug("Sending method '{}'", method);
            JsonObject json = new JsonObject();
            json.addProperty("jsonrpc", JSONRPC_VERSION);
            json.addProperty("method", method);
            json.addProperty("id", ++lastMessageId);
            if (params != null) {
                json.add("params", params);
            }
            String message = json.toString();

            logger.debug("Sending message '{}'", message);
            response = null;
            responseLatch = new CountDownLatch(1);
            nonNull(session).getRemote().sendBytes(TRANSPORT_ENCODING.encode(message + "\0"));

            logger.debug("Waiting for response");
            if (responseLatch.await(bridgeHandler.config().requestTimeout, TimeUnit.SECONDS)) {
                logger.debug("Receiving response '{}'", response);
                if (response != null && nonNull(response).has("result")) {
                    return nonNull(response).get("result");
                }
                return null;
            } else {
                logger.debug("Timeout waiting for response");
                return null;
            }
        } catch (IOException | InterruptedException e) {
            logger.debug("Sending failed due to {}", e, e);
            disconnect();
            return null;
        }
    }

    private void receive(String message) {
        logger.debug("Receiving message {}", message);
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            if (json.has("id")) {
                long id = json.get("id").getAsLong();
                if (id == lastMessageId) {
                    response = json;
                    responseLatch.countDown();
                } else {
                    logger.debug("Ignoring message with unexpected id {}", id);
                }
            } else {
                logger.warn("Ignoring unexpected message {}", message);
            }
        } catch (ClassCastException | IllegalStateException | JsonSyntaxException e) {
            logger.warn("Ignoring invalid message {}", message, e);
        }
    }

    // inner class to inhibit direct access to methods
    // sessions are compared to make sure callback does not concern previous session

    @WebSocket
    public final class Socket {

        @OnWebSocketConnect
        public void onConnect(Session session) {
            if (BeckerSocket.this.session != null) {
                logger.debug("Dropping connection confirmation while connected");
                session.close();
                return;
            } else if (sessionFuture == null) {
                logger.debug("Dropping connection confirmation while disconnected");
                return;
            }

            logger.debug("Connected");
            session.setIdleTimeout(bridgeHandler.config().idleTimeout * 1000L);
            BeckerSocket.this.session = session;
            BeckerSocket.this.sessionFuture = null;
            bridgeHandler.scheduler().submit(() -> bridgeHandler.onConnect());
        }

        @OnWebSocketClose
        public void onClose(Session session, int code, @Nullable String message) {
            if (Objects.equals(BeckerSocket.this.session, session)) {
                logger.debug("Connection closed with code {} and reason '{}'", code, message);
                bridgeHandler.scheduler().submit(() -> disconnect());
            }
        }

        @OnWebSocketError
        public void onError(@Nullable Session session, Throwable t) {
            if (Objects.equals(BeckerSocket.this.session, session)) {
                logger.debug("Communication failed due to {}", t, t);
                bridgeHandler.scheduler().submit(() -> disconnect());
            }
        }

        @OnWebSocketMessage
        public void onMessage(@Nullable Session session, byte @Nullable [] buf, int offset, int length) {
            String message = new String(buf, offset, length, TRANSPORT_ENCODING);
            if (Objects.equals(BeckerSocket.this.session, session)) {
                for (String part : message.split("\0")) {
                    if (part != null) {
                        bridgeHandler.scheduler().submit(() -> receive(part));
                    }
                }
            }
        }
    }
}