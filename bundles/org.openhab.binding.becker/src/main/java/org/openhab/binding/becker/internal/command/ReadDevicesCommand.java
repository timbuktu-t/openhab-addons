package org.openhab.binding.becker.internal.command;

import static org.openhab.binding.becker.internal.util.BeckerUtil.nonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.becker.internal.model.BeckerDevice;
import org.openhab.binding.becker.internal.socket.BeckerCommand;

import com.google.gson.annotations.SerializedName;

@NonNullByDefault
public final class ReadDevicesCommand extends BeckerCommand<ReadDevicesCommand.Result> {

    private static final String METHOD = "deviced.deviced_get_item_list";

    @SerializedName("list_type")
    public final String type = "receivers";

    public ReadDevicesCommand() {
        super(METHOD, Result.class);
    }

    public static final class Result extends BeckerCommand.Result {

        @SerializedName("item_list")
        public BeckerDevice @Nullable [] devices;

        @Override
        public String toString() {
            return String.format("%d devices", devices != null ? nonNull(devices).length : "0");
        }
    }
}