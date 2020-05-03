package org.openhab.binding.becker.internal.command;

import static org.openhab.binding.becker.internal.BeckerBindingConstants.NULL;
import static org.openhab.binding.becker.internal.util.BeckerUtil.nonNullElse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.becker.internal.socket.BeckerCommand;

@NonNullByDefault
public final class ReadFirmwareVersionCommand extends BeckerCommand<ReadFirmwareVersionCommand.Result> {

    private static final String METHOD = "systemd.info_release_data_read";

    public ReadFirmwareVersionCommand() {
        super(METHOD, Result.class);
    }

    public static final class Result extends BeckerCommand.Result {

        public @Nullable String rcode;
        public @Nullable String rdate;

        @Override
        public String toString() {
            return String.format("%s [%s]", nonNullElse(rcode, NULL), nonNullElse(rdate, NULL));
        }
    }
}