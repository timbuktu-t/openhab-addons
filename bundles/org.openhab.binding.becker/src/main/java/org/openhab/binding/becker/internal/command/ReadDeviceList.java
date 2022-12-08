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
package org.openhab.binding.becker.internal.command;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.becker.internal.BeckerDevice;
import org.openhab.binding.becker.internal.socket.BeckerCommand;

import com.google.gson.annotations.SerializedName;

/**
 * The {@ReadDeviceList} represents a command to read the list of devices of a certain type attached to the bridge.
 * 
 * @author Stefan Machura - Initial contribution
 */
@NonNullByDefault
public final class ReadDeviceList extends BeckerCommand<ReadDeviceList.Result> {

    private static final String METHOD = "deviced.deviced_get_item_list";

    /**
     * The type of devices to read. Supported types are {@code receivers} and {@code groups}.
     */
    @SerializedName("list_type")
    public final String type;

    /**
     * Creates a new {@link ReadDeviceList}.
     * 
     * @param type the type of devices to read
     */
    public ReadDeviceList(String type) {
        super(METHOD, Result.class);
        this.type = type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("%s for %s", method, type);
    }

    /**
     * The {@link Result} represents the list of devices read.
     */
    public static final class Result extends BeckerCommand.Result {

        /**
         * The list of devices read or an empty array if unknown.
         */
        @SerializedName("item_list")
        public BeckerDevice[] devices = new BeckerDevice[0];

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return String.format("%d devices", devices.length);
        }
    }
}
