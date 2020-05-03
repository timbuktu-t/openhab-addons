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

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.eclipse.smarthome.core.thing.ThingStatus.OFFLINE;
import static org.eclipse.smarthome.core.thing.ThingStatus.ONLINE;
import static org.eclipse.smarthome.core.thing.ThingStatusDetail.*;
import static org.openhab.binding.becker.internal.BeckerBindingConstants.*;
import static org.openhab.binding.becker.internal.util.BeckerUtil.nonNull;

import java.math.BigDecimal;
import java.util.concurrent.ScheduledFuture;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.becker.internal.command.SendGroupCommand.Preset;
import org.openhab.binding.becker.internal.model.BeckerDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
public final class BeckerDeviceHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(BeckerDeviceHandler.class);

    private @Nullable ScheduledFuture<?> ventFuture;
    private int id;

    public BeckerDeviceHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        id = ((BigDecimal) getConfig().get(PROPERTY_ID)).intValue();
        onRefresh();
        updateState(CHANNEL_DEVICE_CONTROL, new StringType(Preset.STOP.toString()));
    }

    @Override
    public void dispose() {
        cancelVenting();
    }

    // vent is automatically reset to down after 5 minutes; any other command cancels timer

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Received command {} for channel {}", command, channelUID);
        if (command instanceof StringType) {
            switch (channelUID.getId()) {
                case CHANNEL_DEVICE_CONTROL:
                    try {
                        cancelVenting();
                        Preset preset = Preset.valueOf(command.toString());
                        bridgeHandler().socket.send(preset.asCommand(id));
                        updateState(channelUID, (StringType) command);
                        if (Preset.VENT == preset) {
                            int autoRoofWindowTime = bridgeHandler().autoRoofWindowTime();
                            logger.debug("Resetting venting of '{}' after {} minutes", channelUID, autoRoofWindowTime);
                            ventFuture = bridgeHandler().scheduler().schedule(() -> {
                                logger.debug("Venting of '{}' is complete", channelUID, autoRoofWindowTime);
                                updateState(channelUID, new StringType(Preset.DOWN.toString()));
                            }, bridgeHandler().autoRoofWindowTime(), MINUTES);
                        }
                    } catch (IllegalArgumentException e) {
                        // empty
                    }
                    break;
            }
        }
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        onRefresh();
    }

    void onRefresh() {
        if (nonNull(getBridge()).getStatus() == ONLINE) {
            BeckerDevice device = bridgeHandler().devices.get(id);
            if (device != null && getThing().getThingTypeUID().getId().equals(device.subtype)) {
                if (getThing().getStatus() != ONLINE) {
                    updateStatus(ONLINE);
                }
            } else if (getThing().getStatusInfo().getStatusDetail() != GONE) {
                updateStatus(OFFLINE, GONE);
            }
        } else {
            if (getThing().getStatusInfo().getStatusDetail() != BRIDGE_OFFLINE) {
                updateStatus(OFFLINE, BRIDGE_OFFLINE);
            }
        }
    }

    private BeckerBridgeHandler bridgeHandler() {
        return ((BeckerBridgeHandler) nonNull(nonNull(getBridge()).getHandler()));
    }

    private void cancelVenting() {
        if (ventFuture != null) {
            nonNull(ventFuture).cancel(true);
            ventFuture = null;
        }
    }
}
