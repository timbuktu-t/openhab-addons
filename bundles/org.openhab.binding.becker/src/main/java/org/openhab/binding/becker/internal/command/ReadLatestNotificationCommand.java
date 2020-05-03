package org.openhab.binding.becker.internal.command;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.becker.internal.socket.BeckerCommand;

import com.google.gson.annotations.SerializedName;

@NonNullByDefault
public final class ReadLatestNotificationCommand extends BeckerCommand<ReadLatestNotificationCommand.Result> {

    private static final String METHOD = "systemd.log_top_event_id_read";

    public ReadLatestNotificationCommand() {
        super(METHOD, Result.class);
    }

    public static final class Result extends BeckerCommand.Result {

        @SerializedName("event_id")
        public int id;

        @Override
        public String toString() {
            return Integer.toString(id);
        }
    }
}