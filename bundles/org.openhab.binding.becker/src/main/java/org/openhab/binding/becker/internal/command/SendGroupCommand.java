package org.openhab.binding.becker.internal.command;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.becker.internal.socket.BeckerCommand;

import com.google.gson.annotations.SerializedName;

@NonNullByDefault
public final class SendGroupCommand extends BeckerCommand<SendGroupCommand.Result> {

    private static final String METHOD = "deviced.group_send_command";

    @SerializedName("group_id")
    public final int id;
    public final String command;
    public final int value;

    private SendGroupCommand(int id, String command, int value) {
        super(METHOD, Result.class);
        this.id = id;
        this.command = command;
        this.value = value;
    }

    @Override
    public String toString() {
        return String.format("%s[%d]", METHOD, id);
    }

    public static final class Result extends BeckerCommand.Result {
    }

    public static enum Preset {

        UP("move", -1),
        STOP("move", 0),
        DOWN("move", 1),
        VENT("movepreset", 2);

        private final String command;
        private final int value;

        private Preset(String command, int value) {
            this.command = command;
            this.value = value;
        }

        public SendGroupCommand asCommand(int id) {
            return new SendGroupCommand(id, command, value);
        }
    }
}