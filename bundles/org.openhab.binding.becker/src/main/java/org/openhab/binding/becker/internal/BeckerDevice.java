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
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.becker.internal.command.ReadDeviceList;
import org.openhab.binding.becker.internal.handler.BeckerDeviceHandler;
import org.openhab.core.thing.ThingTypeUID;

import com.google.gson.annotations.SerializedName;

/**
 * The {@link BeckerDevice} represents a device attached to the bridge. It is returned by {@link ReadDeviceList} and its
 * channels are mangaged by {@link BeckerDeviceHandler}.
 */
@NonNullByDefault
public final class BeckerDevice {

    /**
     * The unique device id assigned to this device by the bridge or {@code 0} if unknown.
     */
    public int id = 0;

    /**
     * The type of the device or {@code NULL} if unknown. This binding currently supports the type {@code group} for
     * receivers and receiver groups.
     */
    public String type = NULL;

    /**
     * The subtype of the device or {@code NULL} if unknown. This value is used to form the {@link ThingTypeUID}. See
     * {@link BeckerBindingConstants} for a list of supported subtypes.
     */
    @SerializedName("device_type")
    public String subtype = NULL;

    /**
     * The name of the device or {@code NULL} if unknown.
     */
    public String name = NULL;

    /**
     * Creates a new {@link BeckerDevice}.
     */
    public BeckerDevice() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        result = prime * result + type.hashCode();
        result = prime * result + subtype.hashCode();
        result = prime * result + name.hashCode();
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BeckerDevice other = (BeckerDevice) obj;
        if (id != other.id)
            return false;
        if (!type.equals(other.type))
            return false;
        if (!subtype.equals(other.subtype))
            return false;
        if (!name.equals(other.name))
            return false;
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("%s:%s:%d", type, subtype, id);
    }
}
