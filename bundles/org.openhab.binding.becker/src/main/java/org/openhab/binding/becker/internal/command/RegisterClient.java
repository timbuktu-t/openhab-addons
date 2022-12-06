package org.openhab.binding.becker.internal.command;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.becker.internal.socket.BeckerCommand;

@NonNullByDefault
public final class RegisterClient extends BeckerCommand<RegisterClient.Result> {

    private static final String METHOD = "rpc_client_register";

    public final String name = "openhab_" + System.currentTimeMillis();

    public RegisterClient() {
        super(METHOD, Result.class);
    }

    public static final class Result extends BeckerCommand.Result {

        public boolean success = false;

        @Override
        public String toString() {
            return Boolean.toString(success);
        }
    }
}
