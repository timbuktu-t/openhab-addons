package org.openhab.binding.becker.internal.command;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.becker.internal.BeckerDevice;
import org.openhab.binding.becker.internal.socket.BeckerCommand;

import com.google.gson.annotations.SerializedName;

@NonNullByDefault
public final class ReadDeviceList extends BeckerCommand<ReadDeviceList.Result> {

    private static final String METHOD = "deviced.deviced_get_item_list";

    @SerializedName("list_type")
    public final String type;

    public ReadDeviceList(String type) {
        super(METHOD, Result.class);
        this.type = type;
    }

    @Override
    public String toString() {
        return String.format("%s[%s]", method, type);
    }

    public static final class Result extends BeckerCommand.Result {

        @SerializedName("item_list")
        public BeckerDevice[] devices = new BeckerDevice[0];

        @Override
        public String toString() {
            return String.format("%d devices", devices.length);
        }
    }
}
