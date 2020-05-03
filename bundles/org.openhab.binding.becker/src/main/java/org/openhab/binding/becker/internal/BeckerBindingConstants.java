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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link BeckerBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Stefan Machura - Initial contribution
 */
@NonNullByDefault
public final class BeckerBindingConstants {

    // ID of this binding.
    public static final String BINDING_ID = "becker";

    // List of all thing type UIDs
    public static final ThingTypeUID THING_TYPE_BRIDGE = new ThingTypeUID(BINDING_ID, "bridge");
    public static final ThingTypeUID THING_TYPE_ROOF_WINDOW = new ThingTypeUID(BINDING_ID, "roof-window");
    public static final ThingTypeUID THING_TYPE_VENETIAN = new ThingTypeUID(BINDING_ID, "venetian");
    public static final ThingTypeUID THING_TYPE_SHUTTER = new ThingTypeUID(BINDING_ID, "shutter");

    // List of all device type UIDs i.e. excluding the bridge.
    public static final Set<ThingTypeUID> SUPPORTED_DEVICE_TYPES = Collections.unmodifiableSet(
            Stream.of(THING_TYPE_ROOF_WINDOW, THING_TYPE_VENETIAN, THING_TYPE_SHUTTER).collect(Collectors.toSet()));

    // List of all bridge channel IDs
    public static final String CHANNEL_BRIDGE_NOTIFICATION_TEXT = "notification#text";
    public static final String CHANNEL_BRIDGE_NOTIFICATION_TIME = "notification#time";

    // List of all device channel IDs
    public static final String CHANNEL_DEVICE_CONTROL = "control";

    // List of all hardware variants
    public static final String HARDWARE_VARIANT_CC51 = "cc51";

    public static final Set<String> SUPPORTED_HARDWARE_VARIANTS = Collections
            .unmodifiableSet(Stream.of(HARDWARE_VARIANT_CC51).collect(Collectors.toSet()));

    /// List of property names.
    public static final String PROPERTY_ID = "id";

    // Text representing a null value.
    public static final String NULL = "none";

    // Timeout in seconds after which discovery service automatically stops its forced discovery process.
    public static final int DISCOVERY_TIMEOUT_SECONDS = 5;

    // Pattern to use when creating URI from host and port.
    public static final String TRANSPORT_URI_PATTERN = "ws://%s:%d/jrpc";

    // Transport encoding to use for binary conversion
    public static final Charset TRANSPORT_ENCODING = StandardCharsets.UTF_8;

    // TODO (2) automatically determine origin using network service
    // Dummy origin to send when connecting to the server
    public static final String TRANSPORT_ORIGIN = "http://127.0.0.1:12345";

    // JSON-RPC version to send in messages
    public static final String JSONRPC_VERSION = "2.0";

    private BeckerBindingConstants() {
        // empty
    }
}
