package org.openhab.binding.becker.internal.command;

import static org.openhab.binding.becker.internal.BeckerBindingConstants.NULL;
import static org.openhab.binding.becker.internal.util.BeckerUtil.nonNullElse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.becker.internal.socket.BeckerCommand;

@NonNullByDefault
public final class ReadHardwareVariantCommand extends BeckerCommand<ReadHardwareVariantCommand.Result> {

    private static final String METHOD = "systemd.info_hw_variant_read";

    public ReadHardwareVariantCommand() {
        super(METHOD, Result.class);
    }

    public static final class Result extends BeckerCommand.Result {

        public @Nullable String variant;

        @Override
        public String toString() {
            return nonNullElse(variant, NULL);
        }
    }
}