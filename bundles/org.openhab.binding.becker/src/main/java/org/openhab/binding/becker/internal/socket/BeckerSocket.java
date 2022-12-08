/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.becker.internal.socket;

import static org.openhab.binding.becker.internal.BeckerBindingConstants.JSONRPC_VERSION;
import static org.openhab.binding.becker.internal.BeckerBindingConstants.TRANSPORT_ENCODING;
import static org.openhab.binding.becker.internal.BeckerBindingConstants.TRANSPORT_ORIGIN;

import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.api.CloseException;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.openhab.binding.becker.internal.handler.BeckerBridgeHandler;
import org.openhab.core.thing.Bridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * The {@link BeckerSocket} is responsible for the websocket connection and provides the JSONRPC protocol layer to the
 * {@link BeckerBridgeHandler}, allowing execution of {@link BeckerCommand} and returning their results.
 * 
 * @author Stefan Machura - Initial contribution
 */
@NonNullByDefault
public final class BeckerSocket implements AutoCloseable {

    private final Logger logger = LoggerFactory.getLogger(BeckerSocket.class);
    private final Gson gson = new Gson();

    private final BeckerBridgeHandler bridge;
    private final ScheduledExecutorService scheduler;

    // session future is present while connecting
    private @Nullable Future<?> sessionFuture = null;
    // session is present while connected
    private @Nullable Session session = null;
    private @Nullable JsonObject response = null;
    private CountDownLatch responseLatch = new CountDownLatch(1);;
    private long lastMessageId = -1;

    /**
     * Creates a new {@link BeckerBridgeHandler}.
     * 
     * @param bridge the {@link Bridge} thing
     * @param scheduler the {@link ScheduledExecutorService} to use for decoupling
     */
    public BeckerSocket(BeckerBridgeHandler bridge, ScheduledExecutorService scheduler) {
        this.bridge = bridge;
        this.scheduler = scheduler;
    }

    /**
     * Connects this socket to the configured host if it is disconnected. Does nothing if the socket is connected or
     * connecting.
     */
    public synchronized void connect() {
        if (session != null || sessionFuture != null) {
            logger.debug("Attempt to connect while connected, connecting or disconnecting");
            return;
        }

        try {
            URI uri = new URI(String.format("ws://%s:%d/jrpc", bridge.config.host, bridge.config.port));
            logger.debug("Connecting to {}", uri);
            ClientUpgradeRequest request = new ClientUpgradeRequest();
            request.setHeader("Origin", TRANSPORT_ORIGIN);
            request.setSubProtocols("binary");
            bridge.webSocket.setConnectTimeout(bridge.config.connectionTimeout * 1000L);
            sessionFuture = bridge.webSocket.connect(new Socket(), uri, request);
        } catch (IOException | URISyntaxException e) {
            logger.debug("Connection failed due to {}", e, e);
            close(e);
        }
    }

    /**
     * Closes this socket and disconnects from the host. Use this method if the binding is being
     * disposed and should not reconnect.
     */
    @Override
    public void close() {
        close(null);
    }

    /**
     * Closes this socket and disconnects from the host.
     * 
     * @param cause the cause or {@code null} if the binding is disposed and should not reconnect
     */
    public void close(@Nullable Throwable cause) {
        logger.debug("Disconnecting due to {}", cause, cause);

        Session session = this.session;
        Future<?> sessionFuture = this.sessionFuture;

        this.session = null;
        this.sessionFuture = null;

        if (session != null) {
            logger.debug("Closing connection");
            session.close();
        }

        if (sessionFuture != null) {
            if (!sessionFuture.isDone()) {
                logger.debug("Cancelling connection attempt");
                sessionFuture.cancel(true);
            }
        }

        logger.debug("Disconnected");
        bridge.onDisconnect(cause);
    }

    /**
     * Executes a {@link BeckerCommand} and returns its result. Returns an empty {@link Optional} if the command could
     * not be executed or the result is empty. This method waits after sending until it is notified by the receiving
     * thread that a response has arrived.
     * 
     * @param <R> the type of result
     * @param command the {@link BeckerCommand} to execute
     * @return the result or an empty {@link Optional}
     */
    public synchronized <R extends BeckerCommand.Result> Optional<R> send(BeckerCommand<@NonNull R> command) {
        logger.debug("Sending command {}", command);

        Session session = this.session;

        if (session == null) {
            logger.debug("Attempt to send {} while disconnected", command);
            return Optional.empty();
        }

        JsonObject json = new JsonObject();
        json.addProperty("jsonrpc", JSONRPC_VERSION);
        json.addProperty("method", command.method);
        json.addProperty("id", ++lastMessageId);
        JsonElement params = gson.toJsonTree(command);
        if (params.getAsJsonObject().size() > 0) {
            json.add("params", params);
        }

        try {

            logger.trace("Sending message '{}'", json);
            response = null;
            responseLatch = new CountDownLatch(1);
            session.getRemote().sendBytes(TRANSPORT_ENCODING.encode(json + "\0"));

            logger.trace("Waiting for response");
            if (responseLatch.await(bridge.config.requestTimeout, TimeUnit.SECONDS)) {
                @Nullable
                JsonObject response = this.response;
                if (response != null && response.has("result")) {
                    @Nullable
                    R result = gson.fromJson(response.get("result"), command.resultType);
                    logger.debug("Completing command {} with {}", command, result);
                    return Optional.ofNullable(result);
                }
                logger.warn("Received response without result");
                return Optional.empty();
            } else {
                logger.debug("Timeout waiting for response");
                return Optional.empty();
            }
        } catch (IOException | InterruptedException e) {
            logger.debug("Sending failed due to {}", e, e);
            close(e);
            return Optional.empty();
        } catch (ClassCastException | JsonSyntaxException e) {
            logger.warn("Received invalid message {}", response, e);
            return Optional.empty();
        }
    }

    /**
     * Receives a message from the websocket and notifies the sending thread that a response has arrived.
     * 
     * @param message the message
     */
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

    /**
     * The {@link Socket} is responsible to receive callbacks from the websocket implementation. It is implemented as
     * inner class to inhibit direct access to its methods. This implementation decouples callbacks from the binding
     * using the {@link ScheduledExecutorService} to reduce load on the thread pool.
     */
    @WebSocket
    public final class Socket {

        /**
         * Handles connects. Notifies the bridge that a connection is established.
         * 
         * @param session the websocket session
         */
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
            session.setIdleTimeout(bridge.config.idleTimeout * 1000L);
            BeckerSocket.this.session = session;
            BeckerSocket.this.sessionFuture = null;
            scheduler.submit(() -> bridge.onConnect());
        }

        /**
         * Handles disconnect caused by the remote server. Closes the current connection.
         * 
         * @param session the websocket session
         */
        @OnWebSocketClose
        public void onClose(Session session, int code, @Nullable String message) {
            if (Objects.equals(BeckerSocket.this.session, session)) {
                String cause = new StringBuilder().append(code).append(" ").append(message).toString();
                logger.debug("Connection closed with '{}'", cause);
                scheduler.submit(() -> close(new SocketException(cause)));
            }
        }

        /**
         * Handles disconnect caused by communication failures. Closes the current connection.
         * 
         * @param session the websocket session
         */
        @OnWebSocketError
        public void onError(@Nullable Session session, Throwable t) {
            if (Objects.equals(BeckerSocket.this.session, session)) {
                logger.debug("Communication failed due to {}", t, t);
                if (!(t instanceof CloseException)) {
                    scheduler.submit(() -> close(t));
                }
            }
        }

        /**
         * Handles incoming messages. Becker uses binary websockets to transport multiple JSONRPC commands or responses
         * in each message so this methods converts the incoming messages into text responses for further processing.
         * 
         * @param session the websocket session
         * @param buf the message buffer
         * @param offset the offset of the message in the message buffer
         * @param length the length of the message
         */
        @OnWebSocketMessage
        public void onMessage(@Nullable Session session, byte[] buf, int offset, int length) {
            String message = new String(buf, offset, length, TRANSPORT_ENCODING);
            if (Objects.equals(BeckerSocket.this.session, session)) {
                for (String part : message.split("\0")) {
                    scheduler.submit(() -> receive(part));
                }
            }
        }
    }
}
