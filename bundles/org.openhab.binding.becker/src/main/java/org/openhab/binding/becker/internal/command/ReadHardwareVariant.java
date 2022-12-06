package org.openhab.binding.becker.internal.command;

import static org.openhab.binding.becker.internal.BeckerBindingConstants.NULL;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.becker.internal.socket.BeckerCommand;

@NonNullByDefault
public final class ReadHardwareVariant extends BeckerCommand<ReadHardwareVariant.Result> {

    private static final String METHOD = "systemd.info_hw_variant_read";

    public ReadHardwareVariant() {
        super(METHOD, Result.class);
    }

    public static final class Result extends BeckerCommand.Result {

        public String variant = NULL;

        @Override
        public String toString() {
            return variant;
        }
    }
}
