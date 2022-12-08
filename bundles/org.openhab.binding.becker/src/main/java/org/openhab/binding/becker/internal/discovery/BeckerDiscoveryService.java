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
package org.openhab.binding.becker.internal.discovery;

import static org.openhab.binding.becker.internal.BeckerBindingConstants.BINDING_ID;
import static org.openhab.binding.becker.internal.BeckerBindingConstants.DISCOVERY_TIMEOUT_SECONDS;
import static org.openhab.binding.becker.internal.BeckerBindingConstants.PROPERTY_ID;
import static org.openhab.binding.becker.internal.BeckerBindingConstants.SUPPORTED_DEVICE_TYPES;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.becker.internal.BeckerDevice;
import org.openhab.binding.becker.internal.handler.BeckerBridgeHandler;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;

/**
 * The {@link BeckerBridgeHandler} is responsible to provide the devices attached to its bridge as
 * {@link DiscoveryResult} so they appear in the inbox. Discovery of the bridge itself is not supported by this binding.
 *
 * @author Stefan Machura - Initial contribution
 */
@NonNullByDefault
public final class BeckerDiscoveryService extends AbstractDiscoveryService {

    private final BeckerBridgeHandler bridge;

    /**
     * Creates a new {@link BeckerDiscoveryService}.
     * 
     * @param bridge the {@link BeckerBridgeHandler}
     */
    public BeckerDiscoveryService(BeckerBridgeHandler bridge) {
        super(SUPPORTED_DEVICE_TYPES, DISCOVERY_TIMEOUT_SECONDS, true);
        this.bridge = bridge;
    }

    @Override
    @Activate
    public void activate(@Nullable Map<String, Object> configProperties) {
        super.activate(configProperties);
    }

    @Override
    @Deactivate
    public void deactivate() {
        super.deactivate();
    }

    @Override
    protected void startScan() {
        onRefresh(bridge, true);
    }

    /**
     * Handles manual scans and background discoveries if enabled. This method is invoked by the associated bridge when
     * the device list has changed. All attached devices are registered as discovery result as the inbox already detects
     * duplicate registrations.
     * 
     * @param bridge the {@link BeckerBridgeHandler}
     * @param manual {@code true} if this is a manual scan; {@code false} otherwise
     */
    public void onRefresh(BeckerBridgeHandler bridge, boolean manual) {
        if (isBackgroundDiscoveryEnabled() || manual) {
            bridge.devices.values().forEach(this::onDeviceDiscovered);
            removeOlderResults(getTimestampOfLastScan());
        }
    }

    /**
     * Registers the {@link BeckerDevice} as a discovery result.
     * 
     * @param device the {@link BeckerDevice}
     */
    private void onDeviceDiscovered(BeckerDevice device) {
        ThingTypeUID thingTypeUID = new ThingTypeUID(BINDING_ID, device.subtype);
        if (SUPPORTED_DEVICE_TYPES.contains(thingTypeUID)) {
            thingDiscovered(DiscoveryResultBuilder
                    .create(new ThingUID(thingTypeUID, bridge.getThing().getUID(), Integer.toString(device.id)))
                    .withThingType(thingTypeUID).withProperty(PROPERTY_ID, device.id).withLabel(device.name)
                    .withBridge(bridge.getThing().getUID()).build());
        }
    }
}
