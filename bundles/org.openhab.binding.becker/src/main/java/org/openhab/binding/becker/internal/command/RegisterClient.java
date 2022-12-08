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
import org.openhab.binding.becker.internal.socket.BeckerCommand;

/**
 * The {@ReadHardwareSerial} represents a command to register the this bridge as a new client.
 * 
 * @author Stefan Machura - Initial contribution
 */
@NonNullByDefault
public final class RegisterClient extends BeckerCommand<RegisterClient.Result> {

    private static final String METHOD = "rpc_client_register";

    public final String name = "openhab_" + System.currentTimeMillis();

    /**
     * Creates a new {@link RegisterClient}.
     */
    public RegisterClient() {
        super(METHOD, Result.class);
    }

    /**
     * The {@link Result} represents the success of the registration.
     */
    public static final class Result extends BeckerCommand.Result {

        /**
         * The the success of the registration or {@code false} if unknown.
         */
        public boolean success = false;

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return Boolean.toString(success);
        }
    }
}
