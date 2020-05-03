/**
 * Copyright (c) 2014,2019 by the respective copyright holders.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.becker.internal.handler;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.smarthome.core.thing.Thing.*;
import static org.eclipse.smarthome.core.thing.ThingStatus.*;
import static org.eclipse.smarthome.core.thing.ThingStatusDetail.COMMUNICATION_ERROR;
import static org.openhab.binding.becker.internal.BeckerBindingConstants.*;
import static org.openhab.binding.becker.internal.util.BeckerUtil.*;

import java.net.SocketException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.smarthome.core.library.types.DateTimeType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.becker.internal.BeckerConfiguration;
import org.openhab.binding.becker.internal.command.ReadDeviceInfoCommand;
import org.openhab.binding.becker.internal.command.ReadDevicesCommand;
import org.openhab.binding.becker.internal.command.ReadFirmwareVersionCommand;
import org.openhab.binding.becker.internal.command.ReadHardwareSerialCommand;
import org.openhab.binding.becker.internal.command.ReadHardwareVariantCommand;
import org.openhab.binding.becker.internal.command.ReadLatestNotificationCommand;
import org.openhab.binding.becker.internal.command.ReadNotificationCommand;
import org.openhab.binding.becker.internal.command.RegisterClientCommand;
import org.openhab.binding.becker.internal.discovery.BeckerDiscoveryService;
import org.openhab.binding.becker.internal.model.BeckerDevice;
import org.openhab.binding.becker.internal.socket.BeckerSocket;
import org.openhab.binding.becker.internal.socket.BeckerSocketDisconnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link BeckerBridgeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Stefan Machura - Initial contribution
 */
@NonNullByDefault
public class BeckerBridgeHandler extends BaseBridgeHandler {

    // TODO (3) beautify all code, i.e. add empty lines, avoid line breaks, other tweaks optimizations

    private final Logger logger = LoggerFactory.getLogger(BeckerBridgeHandler.class);
    private final WebSocketClient webSocketClient;

    final BeckerSocket socket = new BeckerSocket(this);

    Map<Integer, @Nullable BeckerDevice> devices = Collections.unmodifiableMap(Collections.emptyMap());

    private @Nullable BeckerDiscoveryService discoveryService;
    private @Nullable BeckerConfiguration config;
    private @Nullable ScheduledFuture<?> connectionFuture;
    private @Nullable ScheduledFuture<?> refreshFuture;
    private int autoRoofWindowTime = 5;

    public BeckerBridgeHandler(Bridge bridge, WebSocketClient webSocketClient) {
        super(bridge);
        this.webSocketClient = webSocketClient;
    }

    @Override
    public void initialize() {
        if (logger.isDebugEnabled()) {
            for (String key : getConfig().keySet()) {
                logger.debug("Configuration parameter {} is set to <{}>", key, getConfig().get(key));
            }
        }
        config = getConfigAs(BeckerConfiguration.class);
        socket.initialize();
        connectionFuture = scheduler.schedule(socket::connect, config().connectionDelay, SECONDS);
        updateStatus(UNKNOWN);
    }

    @Override
    public void dispose() {
        if (connectionFuture != null) {
            nonNull(connectionFuture).cancel(true);
            connectionFuture = null;
        }
        socket.dispose();
    }

    public void onConnect() {
        refreshClientRegistration();
        refreshHardwareVersion();
        refreshSoftwareVersion();
        if (refreshFuture == null) {
            refreshFuture = scheduler.scheduleAtFixedRate(() -> {
                refreshNotification();
                refreshAutoRoofWindowTime();
                refreshItems();
            }, 0, config().refreshInterval, SECONDS);
        }
    }

    // called when disconnected while connecting or connected; unwrap throwable to show original cause

    public void onDisconnect(Throwable t) {
        if (refreshFuture != null) {
            nonNull(refreshFuture).cancel(true);
            refreshFuture = null;
        }
        if (!(t instanceof BeckerSocketDisconnectException)) {
            Throwable cause = t;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            updateStatus(OFFLINE, COMMUNICATION_ERROR,
                    nonNullElse(cause.getLocalizedMessage(), cause.getClass().getName()));
            connectionFuture = scheduler.schedule(socket::connect, config().connectionInterval, SECONDS);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Received command {} for channel {}", command, channelUID);
        if (command instanceof RefreshType) {
            switch (channelUID.getId()) {
                case CHANNEL_BRIDGE_NOTIFICATION_TIME:
                case CHANNEL_BRIDGE_NOTIFICATION_TEXT:
                    refreshNotification();
                    break;
            }
        }
    }

    public Collection<BeckerDevice> devices() {
        return (Collection<@NonNull BeckerDevice>) devices.values();
    }

    public BeckerConfiguration config() {
        return nonNull(config);
    }

    public int autoRoofWindowTime() {
        return autoRoofWindowTime;
    }

    public ScheduledExecutorService scheduler() {
        return scheduler;
    }

    public WebSocketClient webSocketClient() {
        return webSocketClient;
    }

    public void discoveryService(@Nullable BeckerDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    // failed registration is fatal and causes a disconnect

    private void refreshClientRegistration() {
        logger.debug("Refreshing client registration");
        socket.send(new RegisterClientCommand()).handle((r, t) -> {
            if (t != null) {
                if (!(t instanceof BeckerSocketDisconnectException)) {
                    logger.debug("Client registration failed with {}", t, t);
                    socket.disconnect(t);
                }
            } else if (!r.success) {
                logger.debug("Client registration was rejected");
                socket.disconnect(new SocketException("Client registration was rejected"));
            } else {
                logger.debug("Client registration was successful");
                updateStatus(ONLINE);
            }
            return null;
        });
    }

    // unknown hardware variant only causes warning as it might work anyways

    private void refreshHardwareVersion() {
        logger.debug("Refreshing hardware version");
        socket.send(new ReadHardwareVariantCommand()).thenAccept(r -> {
            logger.debug("Detected hardware variant <{}>", r);
            if (!SUPPORTED_HARDWARE_VARIANTS.contains(r.toString())) {
                logger.warn("Unsupported hardware variant <{}>", r);
            }
            updateProperty(PROPERTY_MODEL_ID, r.toString());
        });
        socket.send(new ReadHardwareSerialCommand()).thenAccept(r -> {
            logger.debug("Detected hardware serial number <{}>", r);
            updateProperty(PROPERTY_SERIAL_NUMBER, r.toString());
        });
    }

    private void refreshSoftwareVersion() {
        logger.debug("Refreshing software version");
        socket.send(new ReadFirmwareVersionCommand()).thenAccept(r -> {
            logger.debug("Detected software version <{}>", r);
            updateProperty(PROPERTY_FIRMWARE_VERSION, r.toString());
        });
    }

    private void refreshNotification() {
        logger.debug("Refreshing notification");
        socket.send(new ReadLatestNotificationCommand())
                .thenCompose(r -> socket.send(new ReadNotificationCommand(r.id))).thenAccept(r -> {
                    if (r.notification != null) {
                        updateState(CHANNEL_BRIDGE_NOTIFICATION_TEXT, new StringType(nonNull(r.notification).message));
                        updateState(CHANNEL_BRIDGE_NOTIFICATION_TIME,
                                new DateTimeType(ZonedDateTime.ofInstant(
                                        Instant.ofEpochMilli(nonNull(r.notification).timestamp * 1000L),
                                        ZoneId.systemDefault())));
                    }
                });
    }

    private void refreshAutoRoofWindowTime() {
        logger.debug("Refreshing auto roof-window time");
        socket.send(new ReadDeviceInfoCommand()).thenAccept(r -> {
            autoRoofWindowTime = r.autoRoofWindowTime;
        });
    }

    private void refreshItems() {
        logger.debug("Refreshing items");
        socket.send(new ReadDevicesCommand()).thenAccept(r -> {
            logger.debug("Refreshing groups");
            devices = r.devices != null
                    ? Collections
                            .unmodifiableMap(Stream.of(r.devices).filter(i -> "group".equals(i.type) && i.name != null)
                                    .collect(Collectors.toMap(i -> i.id, i -> i)))
                    : Collections.unmodifiableMap(Collections.emptyMap());
            logger.debug("Refreshing things");
            getThing().getThings().forEach(t -> {
                if (t.getHandler() != null) {
                    ((BeckerDeviceHandler) nonNull(t.getHandler())).onRefresh();
                }
            });
            if (discoveryService != null) {
                logger.debug("Refreshing discovery service");
                nonNull(discoveryService).onRefresh(this, false);
            }
        });
    }
}
