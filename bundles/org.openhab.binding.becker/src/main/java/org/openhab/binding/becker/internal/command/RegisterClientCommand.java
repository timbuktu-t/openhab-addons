package org.openhab.binding.becker.internal.command;

import static org.eclipse.jdt.annotation.Checks.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.becker.internal.socket.BeckerCommand;

// TODO (3) add JavaDoc and package-info

@NonNullByDefault
public final class RegisterClientCommand extends BeckerCommand<RegisterClientCommand.Result> {

    private static final String METHOD = "rpc_client_register";

    public final String name = "openhab_" + System.currentTimeMillis();

    public RegisterClientCommand() {
        super(METHOD, Result.class);
    }

    public static final class Result extends BeckerCommand.Result {

        public boolean success;

        @Override
        public String toString() {
            return requireNonNull(Boolean.toString(success));
        }
    }
}