package org.openhab.binding.becker.internal.command;

import static org.openhab.binding.becker.internal.BeckerBindingConstants.NULL;
import static org.openhab.binding.becker.internal.util.BeckerUtil.nonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.becker.internal.model.BeckerNotification;
import org.openhab.binding.becker.internal.socket.BeckerCommand;

import com.google.gson.annotations.SerializedName;

@NonNullByDefault
public final class ReadNotificationCommand extends BeckerCommand<ReadNotificationCommand.Result> {

    private static final String METHOD = "systemd.log_entry_read";

    @SerializedName("event_id")
    public final int id;

    public ReadNotificationCommand(int id) {
        super(METHOD, Result.class);
        this.id = id;
    }

    @Override
    public String toString() {
        return String.format("%s[%d]", METHOD, id);
    }

    public static final class Result extends BeckerCommand.Result {

        @SerializedName("entry")
        public @Nullable BeckerNotification notification;

        @Override
        public String toString() {
            return notification != null ? Integer.toString(nonNull(notification).id) : NULL;
        }
    }
}