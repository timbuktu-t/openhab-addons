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

import static org.openhab.binding.becker.internal.BeckerBindingConstants.NULL;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.becker.internal.socket.BeckerCommand;

/**
 * The {@ReadFirmwareVersion} represents a command to read the firmware version of the bridge.
 * 
 * @author Stefan Machura - Initial contribution
 */
@NonNullByDefault
public final class ReadFirmwareVersion extends BeckerCommand<ReadFirmwareVersion.Result> {

    private static final String METHOD = "systemd.info_release_data_read";

    /**
     * Creates a new {@link ReadFirmwareVersion}.
     */
    public ReadFirmwareVersion() {
        super(METHOD, Result.class);
    }

    /**
     * The {@link Result} represents the firmware version read.
     */
    public static final class Result extends BeckerCommand.Result {

        /**
         * The release code component of the firmware version read or {@link NULL} if unknown.
         */
        public String rcode = NULL;

        /**
         * The release date component of the firmware version read or {@link NULL} if unknown.
         */
        public String rdate = NULL;

        /**
         * Returns the firmware version read.
         * 
         * @return the firmware version read
         */
        public String version() {
            return String.format("%s-%s", rcode, rdate);
        }

        @Override
        public String toString() {
            return version();
        }
    }
}
