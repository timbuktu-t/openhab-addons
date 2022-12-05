package org.openhab.binding.becker.internal;

import static org.eclipse.jdt.annotation.Checks.applyIfNonNullElse;
import static org.eclipse.jdt.annotation.Checks.requireNonNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.openhab.binding.becker.internal.BeckerBindingConstants.NULL;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.LogManager;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.becker.internal.command.ReadFirmwareVersionCommand;
import org.openhab.binding.becker.internal.command.ReadHardwareSerialCommand;
import org.openhab.binding.becker.internal.command.ReadHardwareVariantCommand;
import org.openhab.binding.becker.internal.command.RegisterClientCommand;
import org.openhab.binding.becker.internal.handler.BeckerBridgeHandler;
import org.openhab.binding.becker.internal.socket.BeckerSocket;

// TODO (3) add JavaDoc and package-info

@NonNullByDefault
public final class BeckerSocketTest {

    private final ScheduledExecutorService scheduler = requireNonNull(Executors.newScheduledThreadPool(3));

    private final HttpClient httpClient = new HttpClient();
    private final WebSocketClient webSocketClient = new WebSocketClient();
    private final BeckerBridgeHandler bridgeHandler = requireNonNull(mock(BeckerBridgeHandler.class));

    @BeforeAll
    public static void initialize() throws Exception {
        LogManager.getLogManager()
                .readConfiguration(BeckerSocketTest.class.getResourceAsStream("/logging.properties"));
    }

    @BeforeEach
    public void start() throws Exception {
        httpClient.start();
        webSocketClient.start();

        BeckerConfiguration config = new BeckerConfiguration();
        config.host = "cc41.discworld.local";

        when(bridgeHandler.scheduler()).thenReturn(scheduler);
        when(bridgeHandler.webSocketClient()).thenReturn(webSocketClient);
        when(bridgeHandler.config()).thenReturn(config);

        requireNonNull(doAnswer(iom -> {
            if (iom.getArgument(0) != null) {
                System.out.format("Disconnected due to %s", iom.getArgument(0).toString());
            } else {
                System.out.println("Disconnected");
            }
            return null;
        }).when(bridgeHandler)).onDisconnect(any());
    }

    @AfterEach
    public void stop() throws Exception {
        webSocketClient.stop();
        httpClient.stop();
        scheduler.shutdown();
    }

    @Test
    public void test() throws Exception {
        try (BeckerSocket socket = new BeckerSocket(bridgeHandler)) {

            socket.connect();

            requireNonNull(verify(bridgeHandler, timeout(5000))).onConnect();

            System.out.println("Connected");

            if (applyIfNonNullElse(socket.send(new RegisterClientCommand()), r -> r.success, false)) {
                System.out.println("Registered");
                System.out.println("Hardware Serial : "
                        + applyIfNonNullElse(socket.send(new ReadHardwareSerialCommand()), r -> r.serialno, NULL));
                System.out.println("Hardware Variant: "
                        + applyIfNonNullElse(socket.send(new ReadHardwareVariantCommand()), r -> r.variant, NULL));
                System.out.println("Firmware Version: "
                        + applyIfNonNullElse(socket.send(new ReadFirmwareVersionCommand()), r -> r.version(), NULL));
            }
        }
    }
}