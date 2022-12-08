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

import static org.openhab.binding.becker.internal.BeckerBindingConstants.CHANNEL_DEVICE_CONTROL;
import static org.openhab.binding.becker.internal.BeckerBindingConstants.PROPERTY_ID;
import static org.openhab.core.thing.ThingStatus.OFFLINE;
import static org.openhab.core.thing.ThingStatus.ONLINE;
import static org.openhab.core.thing.ThingStatusDetail.BRIDGE_OFFLINE;
import static org.openhab.core.thing.ThingStatusDetail.BRIDGE_UNINITIALIZED;
import static org.openhab.core.thing.ThingStatusDetail.GONE;

import java.math.BigDecimal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.becker.internal.BeckerDevice;
import org.openhab.binding.becker.internal.command.SendGroup;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StopMoveType;
import org.openhab.core.library.types.UpDownType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link BeckerBridgeHandler} is responsible to communicate with a device attached to the bridge and provide its
 * channels. It uses the connection maintained by the bridge to send commands and the bridges status and device list to
 * update its own status.
 *
 * @author Stefan Machura - Initial contribution
 */
@NonNullByDefault
public final class BeckerDeviceHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(BeckerDeviceHandler.class);

    private int id = 0;

    /**
     * Creates a new {@link BeckerBridgeHandler}.
     * 
     * @param thing the {@link Thing}
     */
    public BeckerDeviceHandler(Thing thing) {
        super(thing);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {
        id = getConfig().containsKey(PROPERTY_ID) ? ((BigDecimal) getConfig().get(PROPERTY_ID)).intValue() : 0;
        onRefresh();
    }

    /**
     * Handles commands. This method supports a single channel of type {@code rollershutter}. This binding supports only
     * {@link UpDownType} and {@link StopMoveType} and maps positional commands using {@link PercentType} to these
     * commands for compatibility.
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Received command {} for channel {}", command, channelUID);

        BeckerBridgeHandler bridge = bridge();

        if (bridge != null && channelUID.getId().equals(CHANNEL_DEVICE_CONTROL)) {
            if (command == UpDownType.UP || command.equals(PercentType.ZERO)) {
                bridge.socket.send(SendGroup.Command.UP.toDevice(id));
                updateState(channelUID, PercentType.ZERO);
            } else if (command == UpDownType.DOWN) {
                bridge.socket.send(SendGroup.Command.DOWN.toDevice(id));
                updateState(channelUID, PercentType.HUNDRED);
            } else if (command == StopMoveType.STOP) {
                bridge.socket.send(SendGroup.Command.STOP.toDevice(id));
            } else if (command instanceof PercentType) {
                bridge.socket.send(SendGroup.Command.DOWN.toDevice(id));
                updateState(channelUID, (PercentType) command);
            }
        }
    }

    /**
     * Handles bridge status changes.
     */
    @Override
    public void bridgeStatusChanged(@Nullable ThingStatusInfo bridgeStatusInfo) {
        onRefresh();
    }

    /**
     * Handles bridge status and device list changes. Updates this things status accordingly.
     */
    void onRefresh() {
        BeckerBridgeHandler bridge = bridge();

        if (bridge == null) {
            updateStatus(OFFLINE, BRIDGE_UNINITIALIZED);
        } else if (bridge.getThing().getStatus() != ONLINE) {
            updateStatus(OFFLINE, BRIDGE_OFFLINE);
        } else {
            BeckerDevice device = bridge.devices.get(id);

            if (device != null && getThing().getThingTypeUID().getId().equals(device.subtype)) {
                updateStatus(ONLINE);
            } else {
                updateStatus(OFFLINE, GONE);
            }
        }
    }

    /**
     * Returns the bridges {@link BeckerBridgeHandler}.
     * 
     * @return the bridges {@link BeckerBridgeHandler} or {@code null} if missing
     */
    private @Nullable BeckerBridgeHandler bridge() {
        Bridge bridge = getBridge();

        if (bridge != null) {
            return (BeckerBridgeHandler) bridge.getHandler();
        }

        return null;
    }
}
