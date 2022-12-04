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

import static org.openhab.binding.becker.internal.BeckerNullables.nonNull;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link BeckerBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Stefan Machura - Initial contribution
 */

// TODO (3) add JavaDoc and package-info

@NonNullByDefault
public class BeckerBindingConstants {

    private static final String BINDING_ID = "becker";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_SAMPLE = new ThingTypeUID(BINDING_ID, "sample");

    // List of all Channel ids
    public static final String CHANNEL_1 = "channel1";

    // Pattern to use when creating URI from host and port.
    public static final String TRANSPORT_URI_PATTERN = "ws://%s:%d/jrpc";

    // Transport encoding to use for binary conversion
    public static final Charset TRANSPORT_ENCODING = nonNull(StandardCharsets.UTF_8);

    // TODO (2) automatically determine origin using network service
    // Dummy origin to send when connecting to the server
    public static final String TRANSPORT_ORIGIN = "http://127.0.0.1:12345";

    // JSON-RPC version to send in messages
    public static final String JSONRPC_VERSION = "2.0";

    private BeckerBindingConstants() {
    }
}