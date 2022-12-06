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
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.becker.internal.discovery.BeckerDiscoveryService;
import org.openhab.binding.becker.internal.handler.BeckerBridgeHandler;
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
 * The {@link BeckerHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Stefan Machura - Initial contribution
 */
@NonNullByDefault
@Component(service = ThingHandlerFactory.class, configurationPid = "binding.becker")
public final class BeckerHandlerFactory extends BaseThingHandlerFactory {

    // TODO (1) localize all messages in English and German.
    // TODO (2) review and finalize files esp. documentation in README.md
    // TODO (2) add JavaDoc and package-info

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
        }
        /*
         * TODO (1) return device handler
         * else if (SUPPORTED_DEVICE_TYPES.contains(thing.getThingTypeUID())) {
         * return new BeckerDeviceHandler(thing);
         * }
         */
        logger.warn("Unsupported thing type UID {} ", thing.getThingTypeUID());
        return null;
    }

    @Override
    protected synchronized void removeHandler(ThingHandler thing) {
        if (thing instanceof BeckerBridgeHandler) {
            unregisterDiscovery(((BeckerBridgeHandler) thing));
        }
    }

    private void registerDiscovery(BeckerBridgeHandler bridge) {
        BeckerDiscoveryService discovery = new BeckerDiscoveryService(bridge);
        discovery.activate(null);
        bridge.discoveryService(discovery);
        discoveries.put(bridge.getThing().getUID(),
                bundleContext.registerService(DiscoveryService.class.getName(), discovery, new Hashtable<>()));
    }

    private void unregisterDiscovery(BeckerBridgeHandler bridge) {
        Optional<ServiceRegistration<?>> reg = Optional.ofNullable(discoveries.remove(bridge.getThing().getUID()));
        reg.flatMap(d -> Optional.ofNullable((BeckerDiscoveryService) bundleContext.getService(d.getReference())))
                .ifPresent(s -> {
                    bridge.discoveryService(null);
                    s.deactivate();
                });
        reg.ifPresent(ServiceRegistration::unregister);
    }

    /*
     * TODO (1) remove if osgi injection into member works
     * 
     * @Reference
     * protected void setHttpClientFactory(WebSocketFactory webSocketFactory) {
     * this.webSocketClient = webSocketFactory.getCommonWebSocketClient();
     * }
     * 
     * protected void unsetHttpClientFactory(WebSocketFactory webSocketFactory) {
     * this.webSocketClient = null;
     * }
     */
}
