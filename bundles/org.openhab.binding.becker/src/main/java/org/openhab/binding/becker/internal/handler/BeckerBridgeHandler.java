package org.openhab.binding.becker.internal.handler;

import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.becker.internal.BeckerConfiguration;

// TODO (3) add JavaDoc and package-info

@NonNullByDefault
public interface BeckerBridgeHandler {
    
    void onConnect();
    void onDisconnect();
    BeckerConfiguration config();
    ScheduledExecutorService scheduler();
    WebSocketClient webSocketClient();
}