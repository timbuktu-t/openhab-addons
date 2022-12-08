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
import org.openhab.binding.becker.internal.socket.BeckerSocket;

import com.google.gson.annotations.SerializedName;

/**
 * The {@ReadHardwareSerial} represents a move command for a device attached to the bridge.
 * 
 * @author Stefan Machura - Initial contribution
 */
@NonNullByDefault
public final class SendGroup extends BeckerCommand<SendGroup.Result> {

    private static final String METHOD = "deviced.group_send_command";

    /**
     * The id of the device to send the command to.
     */
    @SerializedName("group_id")
    public final int id;

    /**
     * The command to send. See {@link Command} for a list of supported commands.
     */
    public final String command;

    /**
     * The value to send. See {@link Command} for a list of supported commands.
     */
    public final int value;

    /**
     * Creates a new {@link SendGroup}.
     * 
     * @param type the type of devices to read
     */
    private SendGroup(int id, String command, int value) {
        super(METHOD, Result.class);
        this.id = id;
        this.command = command;
        this.value = value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("%s with %d", METHOD, id);
    }

    /**
     * The {@link Result} is not used by {@link SendGroup}.
     */
    public static final class Result extends BeckerCommand.Result {
    }

    /**
     * The {@link Command} represents a move command for a device attached to the bridge.
     */
    public static enum Command {

        /**
         * Move the device up, i.e. open the window or blind.
         */
        UP("move", -1),

        /**
         * Stop the device.
         */
        STOP("move", 0),

        /**
         * Move the device down, i.e. close the window or blind.
         */
        DOWN("move", 1);

        private final String command;
        private final int value;

        private Command(String command, int value) {
            this.command = command;
            this.value = value;
        }

        /**
         * Creates a {@link SendGroup} to execute on {@link BeckerSocket} for the current command and given device id.
         * 
         * @param id the device id
         * @return the {@link SendGroup}
         */
        public SendGroup toDevice(int id) {
            return new SendGroup(id, command, value);
        }
    }
}
