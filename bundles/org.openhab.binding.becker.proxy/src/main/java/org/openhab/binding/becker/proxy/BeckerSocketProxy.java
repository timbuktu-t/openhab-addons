package org.openhab.binding.becker.proxy;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("serial")
@WebServlet
public final class BeckerSocketProxy extends WebSocketServlet {

    private final Logger logger = LoggerFactory.getLogger(BeckerSocketProxy.class);

    public static final String PROXYTO = "proxyTo";
    public static final String FILTER = "filter";

    private static final Pattern ID_CAPTURE_PATTERN = Pattern.compile(".*\"id\":\\s*(\\d+).*");
    private static final Charset ENCODING = StandardCharsets.UTF_8;

    private URI proxyTo;
    private Pattern filter;
    private WebSocketClient webSocketClient;
    private Set<String> filteredIds = new HashSet<>();

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            proxyTo = new URI(getInitParameter(PROXYTO));
            filter = Pattern.compile(getInitParameter(FILTER));
            webSocketClient = new WebSocketClient();
            webSocketClient.start();
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        try {
            webSocketClient.stop();
            webSocketClient = null;
        } catch (Exception e) {
            // empty
        }
    }

    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.getPolicy().setIdleTimeout(60000);
        factory.setCreator(new Creator());
    }

    public final class Creator implements WebSocketCreator {

        @Override
        public Object createWebSocket(ServletUpgradeRequest request, ServletUpgradeResponse response) {
            for (String subprotocol : request.getSubProtocols()) {
                if ("binary".equals(subprotocol)) {
                    response.setAcceptedSubProtocol(subprotocol);
                    ServerSocket serverSocket = new ServerSocket();
                    ClientSocket clientSocket = new ClientSocket();
                    serverSocket.clientSocket = clientSocket;
                    clientSocket.serverSocket = serverSocket;
                    try {
                        ClientUpgradeRequest upgrade = new ClientUpgradeRequest();
                        upgrade.setHeader("Origin", request.getHeader("Origin"));
                        upgrade.setSubProtocols("binary");
                        webSocketClient.connect(serverSocket, proxyTo, upgrade);
                        serverSocket.connectLatch.await();
                        return clientSocket;
                    } catch (IOException | InterruptedException e) {
                        logger.error("[SERVER] Connection failed with {}", e, e);
                    }
                }
            }
            return null;
        }
    }

    @WebSocket
    public final class ClientSocket {

        private ServerSocket serverSocket;
        private Session session;

        @OnWebSocketConnect
        public void onConnect(Session session) {
            logger.info("[CLIENT] Connected");
            this.session = session;
        }

        @OnWebSocketClose
        public void onClose(int statusCode, String reason) {
            logger.info("[CLIENT] Disconnected with code {} and reason {}", statusCode, reason);
            serverSocket.session.close(statusCode, reason);
        }

        @OnWebSocketMessage
        public void onMessage(String message) {
            try {
                if (filter.matcher(message).matches()) {
                    Matcher matcher = ID_CAPTURE_PATTERN.matcher(message);
                    if (matcher.matches()) {
                        filteredIds.add(matcher.group(1));
                    }
                } else {
                    logger.info("[CLIENT-MESSAGE] {}", message);
                }
                serverSocket.session.getRemote().sendString(message);
            } catch (IOException e) {
                logger.error("[CLIENT] Sending failed with {}", e, e);
            }
        }
    }

    @WebSocket
    public final class ServerSocket {

        private ClientSocket clientSocket;
        private Session session;
        private CountDownLatch connectLatch = new CountDownLatch(1);

        @OnWebSocketConnect
        public void onConnect(Session session) {
            logger.info("[SERVER] Connected");
            this.session = session;
            this.connectLatch.countDown();
        }

        @OnWebSocketClose
        public void onClose(int statusCode, String reason) {
            logger.info("[SERVER] Disconnected with code {} and reason {}", statusCode, reason);
            clientSocket.session.close(statusCode, reason);
        }

        @OnWebSocketMessage
        public void onMessage(byte buf[], int offset, int length) {
            try {
                String message = new String(buf, offset, length, ENCODING);
                Matcher matcher = ID_CAPTURE_PATTERN.matcher(message);
                if (!matcher.matches() || !filteredIds.remove(matcher.group(1))) {
                    logger.info("[SERVER-MESSAGE] {}", message);
                }
                clientSocket.session.getRemote().sendBytes(ByteBuffer.wrap(buf, offset, length));
            } catch (IOException e) {
                logger.error("[SERVER] Sending failed with {}", e);
            }
        }
    }
}