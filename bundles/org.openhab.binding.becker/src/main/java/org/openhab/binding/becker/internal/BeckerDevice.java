package org.openhab.binding.becker.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

@NonNullByDefault
public final class BeckerDevice {

    public int id;
    public @Nullable String type;
    @SerializedName("device_type")
    public @Nullable String subtype;
    public @Nullable String name;

    public BeckerDevice() {
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%d", type, subtype, id);
    }
}
