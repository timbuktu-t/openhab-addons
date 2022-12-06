package org.openhab.binding.becker.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
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
import org.openhab.binding.becker.internal.command.ReadDeviceInfo;
import org.openhab.binding.becker.internal.command.ReadFirmwareVersion;
import org.openhab.binding.becker.internal.command.ReadHardwareSerial;
import org.openhab.binding.becker.internal.command.ReadHardwareVariant;
import org.openhab.binding.becker.internal.command.RegisterClient;
import org.openhab.binding.becker.internal.handler.BeckerBridgeHandler;
import org.openhab.binding.becker.internal.socket.BeckerSocket;

@NonNullByDefault
public final class BeckerSocketTest {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    private final HttpClient http = new HttpClient();
    private final WebSocketClient webSocket = new WebSocketClient();
    private final BeckerBridgeHandler bridge = mock(BeckerBridgeHandler.class);

    @BeforeAll
    public static void initialize() throws Exception {
        LogManager.getLogManager().readConfiguration(BeckerSocketTest.class.getResourceAsStream("/logging.properties"));
    }

    @BeforeEach
    public void start() throws Exception {
        http.start();
        webSocket.start();

        BeckerConfiguration config = new BeckerConfiguration();
        config.host = "cc41.discworld.local";

        when(bridge.scheduler()).thenReturn(scheduler);
        when(bridge.webSocket()).thenReturn(webSocket);
        when(bridge.config()).thenReturn(config);

        doAnswer(iom -> {
            if (iom.getArgument(0) != null) {
                System.out.format("Disconnected due to %s", iom.getArgument(0).toString());
            } else {
                System.out.println("Disconnected");
            }
            return null;
        }).when(bridge).onDisconnect(any());
    }

    @AfterEach
    public void stop() throws Exception {
        webSocket.stop();
        http.stop();
        scheduler.shutdown();
    }

    @Test
    public void test() throws Exception {
        try (BeckerSocket socket = new BeckerSocket(bridge)) {

            socket.connect();

            verify(bridge, timeout(5000)).onConnect();

            System.out.println("Connected");

            Optional.ofNullable(socket.send(new RegisterClient())).ifPresent(r -> {
                if (r.success == Boolean.TRUE) {
                    System.out.println("Registered");
                    System.out.format("Hardware Serial : %s%n", socket.send(new ReadHardwareSerial()));
                    System.out.format("Hardware Variant: %s%n", socket.send(new ReadHardwareVariant()));
                    System.out.format("Firmware Version: %s%n", socket.send(new ReadFirmwareVersion()));
                    System.out.format("Device Name     : %s%n", socket.send(new ReadDeviceInfo()));
                }
            });
        }
    }
}
