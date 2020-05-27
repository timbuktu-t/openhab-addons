/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
package org.openhab.binding.philipsair.internal.connection;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;

import com.google.gson.JsonSyntaxException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.philipsair.internal.PhilipsAirConfiguration;
import org.openhab.binding.philipsair.internal.model.PhilipsAirPurifierDataDTO;
import org.openhab.binding.philipsair.internal.model.PhilipsAirPurifierDeviceDTO;
import org.openhab.binding.philipsair.internal.model.PhilipsAirPurifierFiltersDTO;
import org.openhab.binding.philipsair.internal.model.PhilipsAirPurifierWritableDataDTO;

/**
 * Interface to abstract communication protocol with Philips Air purifiers
 *
 * @author Stefan Machura - Modifications for COAP
 *
 */

@NonNullByDefault
public interface PhilipsAirAPIConnection {

    public void dispose();

    public @Nullable PhilipsAirPurifierDataDTO getAirPurifierStatus(String host)
            throws JsonSyntaxException, PhilipsAirAPIException;

    public @Nullable PhilipsAirPurifierDeviceDTO getAirPurifierDevice(String host)
            throws JsonSyntaxException, PhilipsAirAPIException;

    public @Nullable PhilipsAirPurifierFiltersDTO getAirPurifierFiltersStatus(String host)
            throws JsonSyntaxException, PhilipsAirAPIException;

    public @Nullable PhilipsAirPurifierDataDTO sendCommand(String parameter, PhilipsAirPurifierWritableDataDTO value)
            throws GeneralSecurityException, UnsupportedEncodingException;
                
    public PhilipsAirConfiguration getConfig();
}