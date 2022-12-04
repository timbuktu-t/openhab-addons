package org.openhab.binding.becker.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.openhab.binding.becker.internal.BeckerNullables.nonNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.LogManager;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.becker.internal.handler.BeckerBridgeHandler;
import org.openhab.binding.becker.internal.socket.BeckerSocket;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

// TODO (3) add JavaDoc and package-info

@NonNullByDefault
public final class BeckerSocketTest {

    private static @Nullable ScheduledExecutorService scheduler;

    private @Nullable HttpClient httpClient;
    private @Nullable WebSocketClient webSocketClient;

    private @Nullable BeckerBridgeHandler bridgeHandler;

    @BeforeAll
    public static void initialize() throws Exception {
        LogManager.getLogManager().readConfiguration(BeckerSocketTest.class.getResourceAsStream("/logging.properties"));
        scheduler = nonNull(Executors.newScheduledThreadPool(3));
    }

    @BeforeEach
    public void start() throws Exception {
        httpClient = new HttpClient();
        webSocketClient = new WebSocketClient();
        nonNull(httpClient).start();
        nonNull(webSocketClient).start();

        BeckerConfiguration config = new BeckerConfiguration();
        config.host = "cc41.discworld.local";

        bridgeHandler = nonNull(mock(BeckerBridgeHandler.class));
        when(nonNull(bridgeHandler).scheduler()).thenReturn(nonNull(scheduler));
        when(nonNull(bridgeHandler).webSocketClient()).thenReturn(nonNull(webSocketClient));
        when(nonNull(bridgeHandler).config()).thenReturn(config);
    }

    @AfterEach
    public void stop() throws Exception {
        nonNull(webSocketClient).stop();
        nonNull(httpClient).stop();
    }

    @AfterAll
    public static void dispose() {
        nonNull(scheduler).shutdown();
    }

    @Test
    public void test() throws Exception {

        BeckerSocket socket = new BeckerSocket(nonNull(bridgeHandler));
        socket.initialize();
        socket.connect();

        verify(nonNull(bridgeHandler), timeout(5000)).onConnect();

        System.out.println("Connected");

        JsonObject params = new JsonObject();
        params.addProperty("name", "openhab_" + System.currentTimeMillis());
        JsonElement response = socket.send("rpc_client_register", params);

        System.out.println("rpc_client_register:");
        System.out.println(response);

        params = new JsonObject();
        params.addProperty("list_type", "receivers");
        response = socket.send("deviced.deviced_get_item_list", params);

        System.out.println("deviced_get_item_list (receivers):");
        System.out.println(response);

        params = new JsonObject();
        params.addProperty("list_type", "groups");
        response = socket.send("deviced.deviced_get_item_list", params);

        System.out.println("deviced_get_item_list (groups):");
        System.out.println(response);

        socket.disconnect();
        socket.dispose();
    }
}