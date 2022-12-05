package org.openhab.binding.becker.internal.command;

import static org.eclipse.jdt.annotation.Checks.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.becker.internal.socket.BeckerCommand;

// TODO (3) add JavaDoc and package-info

@NonNullByDefault
public final class ReadFirmwareVersionCommand extends BeckerCommand<ReadFirmwareVersionCommand.Result> {

    private static final String METHOD = "systemd.info_release_data_read";

    public ReadFirmwareVersionCommand() {
        super(METHOD, Result.class);
    }

    public static final class Result extends BeckerCommand.Result {

        public @Nullable String rcode;
        public @Nullable String rdate;

        public String version() {
            return requireNonNull(new StringBuilder().append(rcode).append("-").append(rdate).toString());
        }

        @Override
        public String toString() {
            return version();
        }
    }
}