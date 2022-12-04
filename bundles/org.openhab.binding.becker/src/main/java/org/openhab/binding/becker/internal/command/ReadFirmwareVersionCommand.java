package org.openhab.binding.becker.internal.command;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        @Override
        @SuppressWarnings("null")
        public String toString() {
            return Stream.of(rcode, rdate).filter(Objects::nonNull).collect(Collectors.joining(" "));
        }
    }
}