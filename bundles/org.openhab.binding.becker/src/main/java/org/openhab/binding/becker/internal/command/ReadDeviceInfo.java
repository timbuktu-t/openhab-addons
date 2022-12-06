package org.openhab.binding.becker.internal.command;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.becker.internal.socket.BeckerCommand;

import com.google.gson.annotations.SerializedName;

@NonNullByDefault
public final class ReadDeviceInfo extends BeckerCommand<ReadDeviceInfo.Result> {

    private static final String METHOD = "deviced.deviced_get_info";

    public ReadDeviceInfo() {
        super(METHOD, Result.class);
    }

    public static final class Result extends BeckerCommand.Result {

        @SerializedName("auto_roof_window_time")
        public int autoRoofWindowTime = 3;

        @Override
        public String toString() {
            return Integer.toString(autoRoofWindowTime);
        }
    }
}
