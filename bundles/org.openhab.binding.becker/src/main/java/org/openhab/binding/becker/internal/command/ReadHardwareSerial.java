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
 * The {@ReadHardwareSerial} represents a command to read the hardware serial number of the bridge.
 * 
 * @author Stefan Machura - Initial contribution
 */
@NonNullByDefault
public final class ReadHardwareSerial extends BeckerCommand<ReadHardwareSerial.Result> {

    private static final String METHOD = "systemd.info_hw_serialno_read";

    /**
     * Creates a new {@link ReadHardwareSerial}.
     */
    public ReadHardwareSerial() {
        super(METHOD, Result.class);
    }

    /**
     * The {@link Result} represents the hardware serial number read.
     */
    public static final class Result extends BeckerCommand.Result {

        /**
         * The hardware serial number read or {@link NULL} if unknown.
         */
        public String serialno = NULL;

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return serialno;
        }
    }
}
