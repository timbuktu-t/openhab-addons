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
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;

@NonNullByDefault
public final class BeckerDiscoveryService extends AbstractDiscoveryService {

    private final BeckerBridgeHandler bridge;

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

    public void onRefresh(BeckerBridgeHandler bridge, boolean manual) {
        if (isBackgroundDiscoveryEnabled() || manual) {
            bridge.devices().forEach(this::onDeviceDiscovered);
            removeOlderResults(getTimestampOfLastScan());
        }
    }

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
