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
package org.openhab.binding.becker.internal;

import static org.openhab.binding.becker.internal.BeckerBindingConstants.SUPPORTED_DEVICE_TYPES;
import static org.openhab.binding.becker.internal.BeckerBindingConstants.THING_TYPE_BRIDGE;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.becker.internal.discovery.BeckerDiscoveryService;
import org.openhab.binding.becker.internal.handler.BeckerBridgeHandler;
import org.openhab.binding.becker.internal.handler.BeckerDeviceHandler;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.io.net.http.WebSocketFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link BeckerHandlerFactory} is responsible for creating bridge and device things and their handlers. For each
 * bridge this factory also creates an associated {@link BeckerDiscoveryService}.
 *
 * @author Stefan Machura - Initial contribution
 */
@NonNullByDefault
@Component(service = ThingHandlerFactory.class, configurationPid = "binding.becker")
public final class BeckerHandlerFactory extends BaseThingHandlerFactory {

    // TODO (1) write documentation in README.md

    private final Logger logger = LoggerFactory.getLogger(BeckerHandlerFactory.class);
    private final Map<ThingUID, @Nullable ServiceRegistration<?>> discoveries = new HashMap<>();

    @Reference
    private @NonNullByDefault({}) WebSocketFactory webSocketFactory;

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return THING_TYPE_BRIDGE.equals(thingTypeUID) || SUPPORTED_DEVICE_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        if (THING_TYPE_BRIDGE.equals(thing.getThingTypeUID())) {
            WebSocketClient webSocket = webSocketFactory.getCommonWebSocketClient();
            BeckerBridgeHandler bridge = new BeckerBridgeHandler((Bridge) thing, webSocket);
            registerDiscovery(bridge);
            return bridge;
        } else if (SUPPORTED_DEVICE_TYPES.contains(thing.getThingTypeUID())) {
            return new BeckerDeviceHandler(thing);
        }
        logger.warn("Unsupported thing type UID {} ", thing.getThingTypeUID());
        return null;
    }

    @Override
    protected synchronized void removeHandler(ThingHandler thing) {
        if (thing instanceof BeckerBridgeHandler) {
            unregisterDiscovery(((BeckerBridgeHandler) thing));
        }
    }

    /**
     * Creates and registers a {@link BeckerDiscoveryService} associated with the bridge.
     * 
     * @param bridge the {@link BeckerBridgeHandler}
     */
    private void registerDiscovery(BeckerBridgeHandler bridge) {
        BeckerDiscoveryService discovery = new BeckerDiscoveryService(bridge);
        discovery.activate(null);
        bridge.discoveryService(discovery);
        discoveries.put(bridge.getThing().getUID(),
                bundleContext.registerService(DiscoveryService.class.getName(), discovery, new Hashtable<>()));
    }

    /**
     * Unregisters the {@link BeckerDiscoveryService} associated with the bridge.
     * 
     * @param bridge the {@link BeckerBridgeHandler}
     */
    private void unregisterDiscovery(BeckerBridgeHandler bridge) {
        ServiceRegistration<?> reg = discoveries.remove(bridge.getThing().getUID());
        if (reg != null) {
            BeckerDiscoveryService discovery = (BeckerDiscoveryService) bundleContext.getService(reg.getReference());
            if (discovery != null) {
                bridge.discoveryService(null);
                discovery.deactivate();
            }
            reg.unregister();
        }
    }
}
