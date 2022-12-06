package org.openhab.binding.becker.internal.handler;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openhab.binding.becker.internal.BeckerBindingConstants.SUPPORTED_HARDWARE_VARIANTS;
import static org.openhab.binding.becker.internal.BeckerBindingConstants.VENDOR;
import static org.openhab.core.thing.Thing.PROPERTY_FIRMWARE_VERSION;
import static org.openhab.core.thing.Thing.PROPERTY_MODEL_ID;
import static org.openhab.core.thing.Thing.PROPERTY_SERIAL_NUMBER;
import static org.openhab.core.thing.Thing.PROPERTY_VENDOR;
import static org.openhab.core.thing.ThingStatus.OFFLINE;
import static org.openhab.core.thing.ThingStatus.ONLINE;
import static org.openhab.core.thing.ThingStatus.UNKNOWN;
import static org.openhab.core.thing.ThingStatusDetail.COMMUNICATION_ERROR;

import java.net.SocketException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.becker.internal.BeckerConfiguration;
import org.openhab.binding.becker.internal.BeckerDevice;
import org.openhab.binding.becker.internal.command.ReadDeviceInfo;
import org.openhab.binding.becker.internal.command.ReadDeviceList;
import org.openhab.binding.becker.internal.command.ReadFirmwareVersion;
import org.openhab.binding.becker.internal.command.ReadHardwareSerial;
import org.openhab.binding.becker.internal.command.ReadHardwareVariant;
import org.openhab.binding.becker.internal.command.RegisterClient;
import org.openhab.binding.becker.internal.discovery.BeckerDiscoveryService;
import org.openhab.binding.becker.internal.socket.BeckerSocket;
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

    private final BeckerSocket socket = new BeckerSocket(this);

    private Map<Integer, BeckerDevice> devices = Collections.unmodifiableMap(Collections.emptyMap());
    private BeckerConfiguration config = new BeckerConfiguration();
    private int autoRoofWindowTime = 3;

    // TODO (2) replace nullabled with optionals?
    private @Nullable BeckerDiscoveryService discovery;
    private @Nullable ScheduledFuture<?> connectionFuture;
    private @Nullable ScheduledFuture<?> refreshFuture;

    public BeckerBridgeHandler(Bridge bridge, WebSocketClient webSocket) {
        super(bridge);
        this.webSocket = webSocket;
    }

    @Override
    public void initialize() {
        config = getConfigAs(BeckerConfiguration.class);
        connectionFuture = scheduler.schedule(socket::connect, config().connectionDelay, SECONDS);
        updateStatus(UNKNOWN);
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> connectionFuture = this.connectionFuture;

        this.connectionFuture = null;

        if (connectionFuture != null) {
            connectionFuture.cancel(true);
        }

        socket.close();
    }

    public void onConnect() {
        refreshDeviceInfo();

        if (refreshFuture == null) {
            refreshFuture = scheduler.scheduleAtFixedRate(() -> {
                refreshAutoRoofWindowTime();
                refreshDevices();
            }, 0, config().refreshInterval, SECONDS);
        }
    }

    public void onDisconnect(@Nullable Throwable t) {
        ScheduledFuture<?> refreshFuture = this.refreshFuture;

        this.refreshFuture = null;

        if (refreshFuture != null) {
            refreshFuture.cancel(true);
        }

        if (t == null) {
            updateStatus(OFFLINE);
        } else {
            updateStatus(OFFLINE, COMMUNICATION_ERROR, t.toString());
            this.connectionFuture = scheduler.schedule(socket::connect, config().connectionInterval, SECONDS);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Received command {} for channel {}", command, channelUID);

        // TODO (2) implement channel for auto roof-window time
    }

    private void refreshDeviceInfo() {
        logger.debug("Refreshing device information");

        if (!socket.send(new RegisterClient()).map(r -> r.success).orElse(false)) {
            socket.close(new SocketException("Client registration failed"));
            return;
        }

        updateStatus(ONLINE);

        socket.send(new ReadHardwareVariant()).ifPresent(r -> {
            updateProperty(PROPERTY_VENDOR, VENDOR);
            updateProperty(PROPERTY_MODEL_ID, r.variant);

            if (!SUPPORTED_HARDWARE_VARIANTS.contains(r.variant)) {
                logger.warn("Unsupported hardware variant {}", r.variant);
            }
        });

        socket.send(new ReadHardwareSerial()).ifPresent(r -> {
            updateProperty(PROPERTY_SERIAL_NUMBER, r.serialno);
        });

        socket.send(new ReadFirmwareVersion()).ifPresent(r -> {
            updateProperty(PROPERTY_FIRMWARE_VERSION, r.version());
        });
    }

    public void refreshAutoRoofWindowTime() {
        logger.debug("Refreshing auto roof-window time");

        socket.send(new ReadDeviceInfo()).ifPresent(r -> {
            autoRoofWindowTime = r.autoRoofWindowTime;
        });
    }

    private void refreshDevices() {
        logger.debug("Refreshing devices");
        devices = Stream
                .concat(socket.send(new ReadDeviceList("receivers")).map(r -> List.of(r.devices))
                        .orElse(Collections.emptyList()).stream(),
                        socket.send(new ReadDeviceList("groups")).map(r -> List.of(r.devices))
                                .orElse(Collections.emptyList()).stream())
                .filter(i -> i.id > 0 && "group".equals(i.type)).collect(Collectors.toMap(i -> i.id, i -> i));

        logger.debug("Refreshing things");
        getThing().getThings().stream().map(t -> (BeckerDeviceHandler) t.getHandler()).filter(t -> t != null)
                .forEach(t -> t.onRefresh());

        BeckerDiscoveryService discovery = this.discovery;
        if (discovery != null) {
            logger.debug("Refreshing discoveries");
            discovery.onRefresh(this, false);
        }
    }

    public BeckerConfiguration config() {
        return config;
    }

    public ScheduledExecutorService scheduler() {
        return scheduler;
    }

    public WebSocketClient webSocket() {
        return webSocket;
    }

    public Collection<BeckerDevice> devices() {
        return devices.values();
    }

    public @Nullable BeckerDevice devices(int id) {
        return devices.get(id);
    }

    public void discoveryService(@Nullable BeckerDiscoveryService discovery) {
        this.discovery = discovery;
    }
}
