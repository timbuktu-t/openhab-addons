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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link BeckerConfiguration} class contains fields mapping thing
 * configuration parameters.
 *
 * @author Stefan Machura - Initial contribution
 */
@NonNullByDefault
public final class BeckerConfiguration {

    public String host = "";
    public int port = 80;
    public int connectionDelay = 1;
    public int connectionInterval = 60;
    public int refreshInterval = 600;
    public int connectionTimeout = 10;
    public int requestTimeout = 10;
    public int idleTimeout = 3600;
}
