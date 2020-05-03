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
package org.openhab.binding.becker.internal;

import static org.openhab.binding.becker.internal.BeckerBindingConstants.*;
import static org.openhab.binding.becker.internal.util.BeckerUtil.nonNull;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.eclipse.smarthome.io.net.http.WebSocketFactory;
import org.openhab.binding.becker.internal.discovery.BeckerDiscoveryService;
import org.openhab.binding.becker.internal.handler.BeckerBridgeHandler;
import org.openhab.binding.becker.internal.handler.BeckerDeviceHandler;
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
    // TODO (2) review and finalize files created by archetype, especially the documentation in README.md.
    // TODO (3) remove parent relative paths from pom when contributing as pull request

    private final Logger logger = LoggerFactory.getLogger(BeckerHandlerFactory.class);
    private final Map<ThingUID, @Nullable ServiceRegistration<?>> discoveryServiceRegs = new HashMap<>();

    private @Nullable WebSocketClient webSocketClient;

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return THING_TYPE_BRIDGE.equals(thingTypeUID) || SUPPORTED_DEVICE_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        if (THING_TYPE_BRIDGE.equals(thing.getThingTypeUID())) {
            BeckerBridgeHandler bridgeHandler = new BeckerBridgeHandler((Bridge) thing, nonNull(webSocketClient));
            registerDiscoveryService(bridgeHandler);
            return bridgeHandler;
        } else if (SUPPORTED_DEVICE_TYPES.contains(thing.getThingTypeUID())) {
            return new BeckerDeviceHandler(thing);
        }
        logger.warn("Unsupported thing type UID {} ", thing.getThingTypeUID());
        return null;
    }

    @Override
    protected synchronized void removeHandler(ThingHandler thingHandler) {
        if (thingHandler instanceof BeckerBridgeHandler) {
            unregisterDiscoveryService(((BeckerBridgeHandler) thingHandler));
        }
    }

    private void registerDiscoveryService(BeckerBridgeHandler bridgeHandler) {
        BeckerDiscoveryService service = new BeckerDiscoveryService(bridgeHandler);
        service.activate(null);
        bridgeHandler.discoveryService(service);
        discoveryServiceRegs.put(bridgeHandler.getThing().getUID(), bundleContext
                .registerService(DiscoveryService.class.getName(), service, new Hashtable<String, Object>()));
    }

    private void unregisterDiscoveryService(BeckerBridgeHandler bridgeHandler) {
        ServiceRegistration<?> serviceReg = discoveryServiceRegs.get(bridgeHandler.getThing().getUID());
        if (serviceReg != null) {
            BeckerDiscoveryService service = (BeckerDiscoveryService) bundleContext
                    .getService(serviceReg.getReference());
            if (service != null) {
                bridgeHandler.discoveryService(null);
                service.deactivate();
            }
            serviceReg.unregister();
            discoveryServiceRegs.remove(bridgeHandler.getThing().getUID());
        }
    }

    @Reference
    protected void setHttpClientFactory(WebSocketFactory webSocketFactory) {
        this.webSocketClient = webSocketFactory.getCommonWebSocketClient();
    }

    protected void unsetHttpClientFactory(WebSocketFactory webSocketFactory) {
        this.webSocketClient = null;
    }
}
