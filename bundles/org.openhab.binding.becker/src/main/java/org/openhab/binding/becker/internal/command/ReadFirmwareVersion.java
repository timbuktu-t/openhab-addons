package org.openhab.binding.becker.internal.command;

import static org.openhab.binding.becker.internal.BeckerBindingConstants.NULL;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.becker.internal.socket.BeckerCommand;

@NonNullByDefault
public final class ReadFirmwareVersion extends BeckerCommand<ReadFirmwareVersion.Result> {

    private static final String METHOD = "systemd.info_release_data_read";

    public ReadFirmwareVersion() {
        super(METHOD, Result.class);
    }

    public static final class Result extends BeckerCommand.Result {

        public String rcode = NULL;
        public String rdate = NULL;

        public String version() {
            return String.format("%s-%s", rcode, rdate);
        }

        @Override
        public String toString() {
            return version();
        }
    }
}
