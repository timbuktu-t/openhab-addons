package org.openhab.binding.becker.internal.handler;

import static org.openhab.core.thing.ThingStatus.UNKNOWN;

import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.becker.internal.BeckerConfiguration;
import org.openhab.binding.becker.internal.discovery.BeckerDiscoveryService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link BeckerBridgeHandler} is responsible for handling commands, which
 * are sent to one of the channels.
 *
 * @author Stefan Machura - Initial contribution
 */
@NonNullByDefault
public class BeckerBridgeHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(BeckerBridgeHandler.class);
    private final WebSocketClient webSocket;

    private BeckerConfiguration config = new BeckerConfiguration();
    private @Nullable BeckerDiscoveryService discovery;

    public BeckerBridgeHandler(Bridge bridge, WebSocketClient webSocket) {
        super(bridge);
        this.webSocket = webSocket;
    }

    @Override
    public void initialize() {
        if (logger.isDebugEnabled()) {
            for (String key : getConfig().keySet()) {
                // TODO (1) logger.debug
                logger.info("Configuration parameter {} is set to <{}>", key, getConfig().get(key));
            }
        }
        config = getConfigAs(BeckerConfiguration.class);
        /*
         * /
         * socket.initialize();
         * connectionFuture = scheduler.schedule(socket::connect,
         * config().connectionDelay, SECONDS);
         */
        updateStatus(UNKNOWN);
    }

    @Override
    public void dispose() {
        /*
         * if (connectionFuture != null) {
         * nonNull(connectionFuture).cancel(true);
         * connectionFuture = null;
         * }
         * socket.dispose();
         */
    }

    public void onConnect() {
        // TODO (!) implement this
    }

    public void onDisconnect(@Nullable Throwable t) {
        // TODO (!) implement this
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Received command {} for channel {}", command, channelUID);
        // TODO (1) handle command
    }

    // socket is initialized after bridge so config cannot be null here

    public BeckerConfiguration config() {
        return config;
    }

    public ScheduledExecutorService scheduler() {
        return scheduler;
    }

    public WebSocketClient webSocket() {
        return webSocket;
    }

    public void discoveryService(@Nullable BeckerDiscoveryService discovery) {
        this.discovery = discovery;
    }
}
