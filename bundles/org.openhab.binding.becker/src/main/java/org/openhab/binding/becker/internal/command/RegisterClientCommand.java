package org.openhab.binding.becker.internal.command;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.becker.internal.socket.BeckerCommand;

// TODO (2) check naming scheme; possibly start with noun instead of verb

@NonNullByDefault
public final class RegisterClientCommand extends BeckerCommand<RegisterClientCommand.Result> {

    private static final String METHOD = "rpc_client_register";

    public final String name = "openhab_" + System.currentTimeMillis();

    public RegisterClientCommand() {
        super(METHOD, Result.class);
    }

    @Override
    public String toString() {
        return String.format("%s[%s]", METHOD, name);
    }

    public static final class Result extends BeckerCommand.Result {

        public boolean success;

        @Override
        public String toString() {
            return Boolean.toString(success);
        }
    }
}