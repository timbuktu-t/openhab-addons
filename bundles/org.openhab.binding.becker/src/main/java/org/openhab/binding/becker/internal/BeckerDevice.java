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

import static org.openhab.binding.becker.internal.BeckerBindingConstants.NULL;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.google.gson.annotations.SerializedName;

@NonNullByDefault
public final class BeckerDevice {

    public int id = 0;
    public String type = NULL;
    @SerializedName("device_type")
    public String subtype = NULL;
    public String name = NULL;

    public BeckerDevice() {
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%d", type, subtype, id);
    }
}
