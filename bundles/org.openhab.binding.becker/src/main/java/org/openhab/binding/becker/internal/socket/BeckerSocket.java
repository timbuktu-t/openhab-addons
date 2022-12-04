package org.openhab.binding.becker.internal.socket;

import static org.eclipse.jdt.annotation.Checks.applyIfNonNull;
import static org.eclipse.jdt.annotation.Checks.ifNonNull;
import static org.eclipse.jdt.annotation.Checks.requireNonNull;
import static org.openhab.binding.becker.internal.BeckerBindingConstants.JSONRPC_VERSION;
import static org.openhab.binding.becker.internal.BeckerBindingConstants.TRANSPORT_ENCODING;
import static org.openhab.binding.becker.internal.BeckerBindingConstants.TRANSPORT_ORIGIN;

import java.io.IOException;
import java.net.SocketException;
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

import com.google.gson.Gson;
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
public final class BeckerSocket implements AutoCloseable {

    private final @NonNullByDefault({}) Logger logger = LoggerFactory.getLogger(BeckerSocket.class);
    private final Gson gson = new Gson();

    private final BeckerBridgeHandler bridge;

    private @Nullable Future<?> sessionFuture = null;
    private @Nullable Session session = null;
    private @Nullable JsonObject response = null;
    private CountDownLatch responseLatch = new CountDownLatch(1);;
    private long lastMessageId = -1;

    public BeckerSocket(BeckerBridgeHandler bridge) {
        this.bridge = bridge;
    }

    public synchronized void connect() {
        if (session != null || sessionFuture != null) {
            logger.debug("Attempt to connect while connected, connecting or disconnecting");
            return;
        }

        try {
            URI uri = new URI(String.format("ws://%s:%d/jrpc", bridge.config().host, bridge.config().port));
            logger.debug("Connecting to {}", uri);
            ClientUpgradeRequest request = new ClientUpgradeRequest();
            request.setHeader("Origin", TRANSPORT_ORIGIN);
            request.setSubProtocols("binary");
            sessionFuture = bridge.webSocketClient().connect(new Socket(), uri, request);
        } catch (IOException | URISyntaxException e) {
            logger.debug("Connection failed due to {}", e, e);
            close(e);
        }
    }

    @Override
    public void close() {
        close(null);
    }

    // no throwable disconnected explicitly to avoid reconnection attempts

    private synchronized void close(@Nullable Throwable t) {
        logger.debug(t == null ? "Disconnecting" : "Disconnecting due to {}", t);

        ifNonNull(session, s -> {
            logger.debug("Closing session");
            s.close();
            session = null;
            bridge.onDisconnect(t);
        });

        ifNonNull(sessionFuture, f -> {
            if (!f.isDone()) {
                logger.debug("Cancelling connection attempt");
                f.cancel(true);
                sessionFuture = null;
                bridge.onDisconnect(t);
            }
        });
    }

    // return received result on success or null on missing result and failure

    public synchronized <R extends BeckerCommand.Result> @Nullable R send(BeckerCommand<R> command) {
        try {
            if (session == null) {
                logger.debug("Attempt to send {} while disconnected", command);
                return null;
            }

            logger.debug("Sending command {}", command);
            JsonObject json = new JsonObject();
            json.addProperty("jsonrpc", JSONRPC_VERSION);
            json.addProperty("method", command.method);
            json.addProperty("id", ++lastMessageId);
            JsonElement params = gson.toJsonTree(command);
            if (params.getAsJsonObject().size() > 0) {
                json.add("params", params);
            }

            logger.trace("Sending message '{}'", json);
            response = null;
            responseLatch = new CountDownLatch(1);
            requireNonNull(session).getRemote().sendBytes(TRANSPORT_ENCODING.encode(json + "\0"));
            logger.trace("Waiting for response");
            if (responseLatch.await(bridge.config().requestTimeout, TimeUnit.SECONDS)) {
                @Nullable
                R result = applyIfNonNull(requireNonNull(response).get("result"),
                        r -> gson.fromJson(r, command.resultType));
                logger.debug("Completing command {} with {}", command, result);
                return result;
            } else {
                logger.debug("Timeout waiting for response");
                return null;
            }
        } catch (IOException | InterruptedException e) {
            logger.debug("Sending failed due to {}", e, e);
            close(e);
            return null;
        } catch (ClassCastException | JsonSyntaxException e) {
            logger.warn("Received invalid message {}", response, e);
            return null;
        }
    }

    private void receive(String message) {
        logger.trace("Receiving message {}", message);

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
            session.setIdleTimeout(bridge.config().idleTimeout * 1000L);
            BeckerSocket.this.session = session;
            BeckerSocket.this.sessionFuture = null;
            bridge.scheduler().submit(() -> bridge.onConnect());
        }

        @OnWebSocketClose
        public void onClose(Session session, int code, @Nullable String message) {
            if (Objects.equals(BeckerSocket.this.session, session)) {
                final String cause = String.format("Connection closed with code %d and reason '%s'", code, message);
                logger.debug(cause);
                bridge.scheduler().submit(() -> close(new SocketException(cause)));
            }
        }

        @OnWebSocketError
        public void onError(@Nullable Session session, Throwable t) {
            if (Objects.equals(BeckerSocket.this.session, session)) {
                logger.debug("Communication failed due to {}", t, t);
                bridge.scheduler().submit(() -> close(t));
            }
        }

        @OnWebSocketMessage
        public void onMessage(@Nullable Session session, byte @Nullable [] buf, int offset, int length) {
            String message = new String(buf, offset, length, TRANSPORT_ENCODING);
            if (Objects.equals(BeckerSocket.this.session, session)) {
                for (String part : message.split("\0")) {
                    if (part != null) {
                        bridge.scheduler().submit(() -> receive(part));
                    }
                }
            }
        }
    }
}