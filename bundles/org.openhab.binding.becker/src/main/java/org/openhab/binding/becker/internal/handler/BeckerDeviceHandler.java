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
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StopMoveType;
import org.openhab.core.library.types.UpDownType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
public final class BeckerDeviceHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(BeckerDeviceHandler.class);

    private int id = 0;

    public BeckerDeviceHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        id = getConfig().containsKey(PROPERTY_ID) ? ((BigDecimal) getConfig().get(PROPERTY_ID)).intValue() : 0;
        onRefresh();
    }

    // vent is automatically reset to down after 5 minutes; any other command
    // cancels timer

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Received command {} of type {} for channel {}", command, channelUID);

        // TODO (2) implement commands

        if (command instanceof RefreshType) {
            logger.debug("Received refresh command");
        } else if (command instanceof UpDownType || command instanceof StopMoveType) {
            logger.debug("Received move command {}", command);
        } else if (command instanceof PercentType) {
            logger.debug("Received positional command for {} percent", ((PercentType) command).intValue());
        }
    }

    @Override
    public void bridgeStatusChanged(@Nullable ThingStatusInfo bridgeStatusInfo) {
        onRefresh();
    }

    void onRefresh() {
        Bridge bridge = getBridge();
        BeckerBridgeHandler bridgeHandler = bridge();

        if (bridge == null || bridgeHandler == null) {
            updateStatus(OFFLINE, BRIDGE_UNINITIALIZED);
        } else if (bridge.getStatus() != ONLINE) {
            updateStatus(OFFLINE, BRIDGE_OFFLINE);
        } else {
            BeckerDevice device = bridgeHandler.devices(id);

            if (device != null && getThing().getThingTypeUID().getId().equals(device.subtype)) {
                updateStatus(ONLINE);
            } else {
                updateStatus(OFFLINE, GONE);
            }
        }
    }

    private @Nullable BeckerBridgeHandler bridge() {
        Bridge bridge = getBridge();

        if (bridge != null) {
            return (BeckerBridgeHandler) bridge.getHandler();
        }

        return null;
    }
}
