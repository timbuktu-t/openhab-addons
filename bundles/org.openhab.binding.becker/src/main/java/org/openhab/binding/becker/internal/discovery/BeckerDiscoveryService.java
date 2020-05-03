package org.openhab.binding.becker.internal.discovery;

import static org.openhab.binding.becker.internal.BeckerBindingConstants.*;
import static org.openhab.binding.becker.internal.util.BeckerUtil.nonNull;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.becker.internal.handler.BeckerBridgeHandler;
import org.openhab.binding.becker.internal.model.BeckerDevice;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;

@NonNullByDefault
public final class BeckerDiscoveryService extends AbstractDiscoveryService {

    private final BeckerBridgeHandler bridgeHandler;

    public BeckerDiscoveryService(BeckerBridgeHandler bridgeHandler) {
        super(SUPPORTED_DEVICE_TYPES, DISCOVERY_TIMEOUT_SECONDS, true);
        this.bridgeHandler = bridgeHandler;
    }

    @Override
    @Activate
    public void activate(@Nullable Map<String, @Nullable Object> configProperties) {
        super.activate(configProperties);
    }

    @Override
    @Deactivate
    public void deactivate() {
        super.deactivate();
    }

    @Override
    protected void startScan() {
        onRefresh(bridgeHandler, true);
    }

    public void onRefresh(BeckerBridgeHandler bridgeHandler, boolean manual) {
        if (isBackgroundDiscoveryEnabled() || manual) {
            bridgeHandler.devices().forEach(this::onDeviceDiscovered);
            removeOlderResults(getTimestampOfLastScan());
        }
    }

    private void onDeviceDiscovered(BeckerDevice device) {
        if (device.subtype != null) {
            ThingTypeUID thingTypeUID = new ThingTypeUID(BINDING_ID, nonNull(device.subtype));
            if (SUPPORTED_DEVICE_TYPES.contains(thingTypeUID)) {
                thingDiscovered(DiscoveryResultBuilder
                        .create(new ThingUID(thingTypeUID, bridgeHandler.getThing().getUID(),
                                Integer.toString(device.id)))
                        .withThingType(thingTypeUID).withProperty(PROPERTY_ID, device.id)
                        .withRepresentationProperty(PROPERTY_ID).withLabel(device.name)
                        .withBridge(bridgeHandler.getThing().getUID()).build());
            }
        }
    }
}
