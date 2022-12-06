package org.openhab.binding.becker.internal;

import static org.openhab.binding.becker.internal.BeckerBindingConstants.NULL;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.google.gson.annotations.SerializedName;

@NonNullByDefault
public final class BeckerDevice {

    public int id = 0;
    public String type = NULL;
    @SerializedName("device_type")
    public String subtype = NULL;
    public String name = NULL;

    public BeckerDevice() {
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%d", type, subtype, id);
    }
}
