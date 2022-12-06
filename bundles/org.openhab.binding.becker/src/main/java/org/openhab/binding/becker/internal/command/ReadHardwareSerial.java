package org.openhab.binding.becker.internal.command;

import static org.openhab.binding.becker.internal.BeckerBindingConstants.NULL;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.becker.internal.socket.BeckerCommand;

@NonNullByDefault
public final class ReadHardwareSerial extends BeckerCommand<ReadHardwareSerial.Result> {

    private static final String METHOD = "systemd.info_hw_serialno_read";

    public ReadHardwareSerial() {
        super(METHOD, Result.class);
    }

    public static final class Result extends BeckerCommand.Result {

        public String serialno = NULL;

        @Override
        public String toString() {
            return serialno;
        }
    }
}
