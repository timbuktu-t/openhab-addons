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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.becker.internal.BeckerConfiguration;
import org.openhab.binding.becker.internal.BeckerDevice;
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
 * The {@link BeckerBridgeHandler} is responsible to maintain the connection to the bridge, periodically refresh device
 * information and provide that information to the {@link BeckerDeviceHandler}.
 *
 * @author Stefan Machura - Initial contribution
 */
@NonNullByDefault
public class BeckerBridgeHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(BeckerBridgeHandler.class);

    public final WebSocketClient webSocket;
    public final BeckerSocket socket = new BeckerSocket(this, scheduler);

    public Map<Integer, BeckerDevice> devices = Collections.unmodifiableMap(Collections.emptyMap());
    public BeckerConfiguration config = new BeckerConfiguration();

    private @Nullable BeckerDiscoveryService discovery;
    private @Nullable ScheduledFuture<?> connectionFuture;
    private @Nullable ScheduledFuture<?> refreshFuture;

    /**
     * Creates a new {@link BeckerBridgeHandler}.
     * 
     * @param bridge the {@link Bridge}
     * @param webSocket the {@link WebSocketClient} to use for communication
     */
    public BeckerBridgeHandler(Bridge bridge, WebSocketClient webSocket) {
        super(bridge);
        this.webSocket = webSocket;
    }

    @Override
    public void initialize() {
        config = getConfigAs(BeckerConfiguration.class);
        connectionFuture = scheduler.schedule(socket::connect, config.connectionDelay, SECONDS);
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

    /**
     * Handles connects. When a connection is established, this method retrieves general information
     * about the bridge and starts periodic polling to keep the connection alive and refresh the list of devices
     * attached to the bridge.
     */
    public void onConnect() {
        refreshDeviceInfo();

        if (refreshFuture == null) {
            refreshFuture = scheduler.scheduleWithFixedDelay(() -> {
                refreshDevices();
            }, 0, config.refreshInterval, SECONDS);
        }
    }

    /**
     * Handles disconnects. When a connection is closed, this method updates the thing status and schedules a
     * reconnection attempt if neccesary.
     * 
     * @param cause the cause or {@code null} if the binding is disposed and should not reconnect
     */
    public void onDisconnect(@Nullable Throwable cause) {
        ScheduledFuture<?> refreshFuture = this.refreshFuture;

        this.refreshFuture = null;

        if (refreshFuture != null) {
            refreshFuture.cancel(true);
        }

        if (cause == null) {
            updateStatus(OFFLINE);
        } else {
            updateStatus(OFFLINE, COMMUNICATION_ERROR, cause.toString());
            this.connectionFuture = scheduler.schedule(socket::connect, config.connectionInterval, SECONDS);
        }
    }

    /**
     * Handles commands. This method does nothing as the bridge currently does not support any channels.
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Received command {} for channel {}", command, channelUID);
    }

    /**
     * Registers this bridge as a new client and refreshes general information like serial number and firmware version.
     */
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

    /**
     * Refreshes the list of devices attached to the bridge and notifies both discovery and all attached things
     * when the list has changed.
     */
    private void refreshDevices() {
        logger.debug("Retrieving devices");
        Map<Integer, BeckerDevice> devices = Stream
                .concat(socket.send(new ReadDeviceList("receivers")).map(r -> List.of(r.devices))
                        .orElse(Collections.emptyList()).stream(),
                        socket.send(new ReadDeviceList("groups")).map(r -> List.of(r.devices))
                                .orElse(Collections.emptyList()).stream())
                .filter(i -> i.id > 0 && "group".equals(i.type)).collect(Collectors.toMap(i -> i.id, i -> i));

        if (!this.devices.equals(devices)) {
            logger.debug("Devices have changed");
            this.devices = devices;

            logger.debug("Refreshing things");
            getThing().getThings().stream().map(t -> (BeckerDeviceHandler) t.getHandler()).filter(t -> t != null)
                    .forEach(t -> t.onRefresh());

            BeckerDiscoveryService discovery = this.discovery;
            if (discovery != null) {
                logger.debug("Refreshing discoveries");
                discovery.onRefresh(this, false);
            }
        }
    }

    /**
     * Sets the discovery service associated with this bridge.
     * 
     * @param discovery the {@link BeckerDiscoveryService} or {@code null} if the binding is disposed
     */
    public void discoveryService(@Nullable BeckerDiscoveryService discovery) {
        this.discovery = discovery;
    }
}
