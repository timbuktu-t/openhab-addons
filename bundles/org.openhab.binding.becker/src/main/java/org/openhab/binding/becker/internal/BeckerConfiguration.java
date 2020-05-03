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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link BeckerConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author Stefan Machura - Initial contribution
 */
@NonNullByDefault
public final class BeckerConfiguration {

    public @Nullable String host;
    public int port;

    public int connectionDelay;
    public int connectionInterval;
    public int refreshInterval;
    public int idleTimeout;
    public int queueCapacity;
    public int queueTimeout;
    public int queueTimeoutInterval;
}
