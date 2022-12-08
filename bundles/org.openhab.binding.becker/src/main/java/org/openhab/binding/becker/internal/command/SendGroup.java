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

import com.google.gson.annotations.SerializedName;

@NonNullByDefault
public final class SendGroup extends BeckerCommand<SendGroup.Result> {

    private static final String METHOD = "deviced.group_send_command";

    @SerializedName("group_id")
    public final int id;
    public final String command;
    public final int value;

    private SendGroup(int id, String command, int value) {
        super(METHOD, Result.class);
        this.id = id;
        this.command = command;
        this.value = value;
    }

    @Override
    public String toString() {
        return String.format("%s[%d]", METHOD, id);
    }

    public static final class Result extends BeckerCommand.Result {
    }

    public static enum Command {

        UP("move", -1),
        STOP("move", 0),
        DOWN("move", 1);

        private final String command;
        private final int value;

        private Command(String command, int value) {
            this.command = command;
            this.value = value;
        }

        public SendGroup toDevice(int id) {
            return new SendGroup(id, command, value);
        }
    }
}
