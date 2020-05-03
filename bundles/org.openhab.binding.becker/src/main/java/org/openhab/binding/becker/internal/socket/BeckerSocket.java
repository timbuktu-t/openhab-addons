package org.openhab.binding.becker.internal.socket;

import static org.openhab.binding.becker.internal.BeckerBindingConstants.*;
import static org.openhab.binding.becker.internal.util.BeckerUtil.*;

import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.openhab.binding.becker.internal.handler.BeckerBridgeHandler;
import org.openhab.binding.becker.internal.socket.BeckerSocketQueue.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

// TODO (3) add JavaDoc and package-info

// callback from socket are scheduled to reduce load on socket thread pool
// future is present while connecting, session is present while connected
// upon state changes context is notified before future is completed

@NonNullByDefault
public final class BeckerSocket {

    private final Logger logger = LoggerFactory.getLogger(BeckerSocket.class);
    private final Gson gson = new Gson();
    private final JsonParser parser = new JsonParser();

    private final BeckerBridgeHandler bridgeHandler;
    private final BeckerSocketQueue queue;

    private @Nullable CompletableFuture<@Nullable Void> future;
    private @Nullable ScheduledFuture<?> eviction = null;
    private @Nullable Session session;

    public BeckerSocket(BeckerBridgeHandler bridgeHandler) {
        this.bridgeHandler = bridgeHandler;
        this.queue = new BeckerSocketQueue(bridgeHandler);
    }

    public synchronized void initialize() {
        if (eviction == null) {
            eviction = bridgeHandler.scheduler().scheduleAtFixedRate(queue::evictDated,
                    bridgeHandler.config().queueTimeout * 1000, bridgeHandler.config().queueTimeoutInterval,
                    TimeUnit.MILLISECONDS);
        }
    }

    public synchronized void dispose() {
        if (eviction != null) {
            nonNull(eviction).cancel(true);
            eviction = null;
        }
        disconnect();
    }

    public synchronized CompletableFuture<@Nullable Void> connect() {
        if (session != null) {
            logger.debug("Attempt to connect while connected");
            return CompletableFuture.completedFuture(null);
        } else if (future != null) {
            logger.debug("Attempt to connect while connecting");
            return nonNull(future);
        } else {
            CompletableFuture<@Nullable Void> future = this.future = new CompletableFuture<>();
            try {
                logger.debug("Connecting to {}:{}", bridgeHandler.config().host, bridgeHandler.config().port);
                URI uri = new URI(
                        String.format(TRANSPORT_URI_PATTERN, bridgeHandler.config().host, bridgeHandler.config().port));
                ClientUpgradeRequest request = new ClientUpgradeRequest();
                request.setHeader("Origin", TRANSPORT_ORIGIN);
                request.setSubProtocols("binary");
                bridgeHandler.webSocketClient().connect(new Socket(), uri, request);
            } catch (IOException | URISyntaxException e) {
                logger.debug("Connection failed due to {}", e, e);
                disconnect(e);
            }
            return future;
        }
    }

    public void disconnect() {
        disconnect(new BeckerSocketDisconnectException("Cancelled due to disconnect"));
    }

    public synchronized void disconnect(Throwable t) {
        if (session != null) {
            logger.debug("Disconnecting with {}", t);
            nonNull(session).close();
            session = null;
            queue.evictAll(t);
            bridgeHandler.onDisconnect(t);
        } else if (future != null) {
            logger.debug("Disconnecting while connecting with {}", t);
            nonNull(future).completeExceptionally(t);
            future = null;
            bridgeHandler.onDisconnect(t);
        }
    }

    public synchronized <R extends BeckerCommand.Result> CompletableFuture<R> send(BeckerCommand<R> command) {
        BeckerSocketQueue.Entry<R> entry = queue.enqueue(command);
        if (session == null) {
            logger.debug("Attempt to send command {} while disconnected or connecting", command);
            queue.evict(entry.id, new CancellationException("Cancelled due to missing connection"));
        } else {
            logger.debug("Sending command {}", command);
            JsonObject json = new JsonObject();
            json.addProperty("jsonrpc", JSONRPC_VERSION);
            json.addProperty("method", command.method);
            json.addProperty("id", entry.id);
            JsonElement params = gson.toJsonTree(command);
            if (params.getAsJsonObject().size() > 0) {
                json.add("params", params);
            }
            String message = json.toString();
            logger.debug("Sending message {}", message);
            nonNull(session).getRemote().sendBytes(TRANSPORT_ENCODING.encode(message + "\0"), new WriteCallback() {
                @Override
                public void writeSuccess() {
                    logger.debug("Sending of command {} complete", command);
                }

                @Override
                public void writeFailed(@Nullable Throwable t) {
                    logger.debug("Sending of command {} failed due to {}", command, t, t);
                    Throwable cause = nonNullElse(t, new SocketException("Sending failed with unspecified cause"));
                    queue.evict(entry.id, cause);
                    disconnect(cause);
                }
            });
        }
        return entry.future;
    }

    private <R extends BeckerCommand.Result> void receive(String message) {
        logger.debug("Receiving message {}", message);
        try {
            JsonObject json = parser.parse(message).getAsJsonObject();
            if (json.has("id")) {
                long id = json.get("id").getAsLong();
                @SuppressWarnings("unchecked")
                BeckerSocketQueue.Entry<R> entry = (Entry<R>) queue.dequeue(id);
                if (entry != null) {
                    try {
                        if (json.has("error")) {
                            Exception e = gson.fromJson(json.get("error"), Error.class).asException();
                            logger.warn("Failing command {} with {}", entry.command, e, e);
                            entry.future.completeExceptionally(e);
                        } else {
                            R result = json.has("result") ? gson.fromJson(json.get("result"), entry.command.resultType)
                                    : entry.command.resultType.newInstance();
                            logger.debug("Completing command {} with {}", entry.command, result);
                            entry.future.complete(result);
                        }
                    } catch (JsonSyntaxException | InstantiationException | IllegalAccessException e) {
                        logger.debug("Failing command {} with {}", entry.command, e, e);
                        entry.future.completeExceptionally(e);
                    }
                } else {
                    logger.debug("Received unmatched result {}", message);
                }
            } else {
                logger.warn("Received unexpected message {}", message);
            }
        } catch (ClassCastException | IllegalStateException | JsonSyntaxException e) {
            logger.warn("Received invalid message {}", message, e);
        }
    }

    private static final class Error {

        private int code;
        private @Nullable String message;

        BeckerSocketServerException asException() {
            return new BeckerSocketServerException(code, message);
        }
    }

    // inner class to inhibit direct access to methods
    // sessions are compared to make sure callback does not concern previous session

    @WebSocket
    public final class Socket {

        @OnWebSocketConnect
        public void onConnect(Session session) {
            synchronized (BeckerSocket.this) {
                if (BeckerSocket.this.session != null) {
                    logger.debug("Dropping connection confirmation while connected");
                    session.close();
                    return;
                } else if (future == null) {
                    logger.debug("Dropping connection confirmation while disconnected");
                    return;
                } else {
                    logger.debug("Connected");
                    CompletableFuture<@Nullable Void> future = BeckerSocket.this.future;
                    session.setIdleTimeout(bridgeHandler.config().idleTimeout * 1000L);
                    BeckerSocket.this.session = session;
                    BeckerSocket.this.future = null;
                    bridgeHandler.scheduler().submit(() -> {
                        bridgeHandler.onConnect();
                        nonNull(future).complete(null);
                    });
                }
            }
        }

        // does nothing if closed by client; disconnects if closed by server

        @OnWebSocketClose
        public void onClose(Session session, int statusCode, @Nullable String reason) {
            synchronized (BeckerSocket.this) {
                if (Objects.equals(BeckerSocket.this.session, session)) {
                    logger.debug("Connection closed with {} <{}>", statusCode, reason);
                    bridgeHandler.scheduler().submit(() -> disconnect(
                            new SocketException("Connection was closed unexpectedly [" + statusCode + "]")));
                }
            }
        }

        @OnWebSocketError
        public void onError(@Nullable Session session, Throwable t) {
            synchronized (BeckerSocket.this) {
                if (Objects.equals(BeckerSocket.this.session, session)) {
                    logger.debug("Communication failed due to {}", t, t);
                    bridgeHandler.scheduler().submit(() -> disconnect(t));
                }
            }
        }

        @OnWebSocketMessage
        public void onMessage(@Nullable Session session, byte @Nullable [] buf, int offset, int length) {
            synchronized (BeckerSocket.this) {
                String message = new String(buf, offset, length, TRANSPORT_ENCODING);
                if (Objects.equals(BeckerSocket.this.session, session)) {
                    for (String part : message.split("\0")) {
                        bridgeHandler.scheduler().submit(() -> receive(part));
                    }
                }
            }
        }
    }
}
