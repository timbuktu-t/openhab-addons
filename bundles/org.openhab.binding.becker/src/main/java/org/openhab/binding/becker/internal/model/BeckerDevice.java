package org.openhab.binding.becker.internal.model;

import static org.openhab.binding.becker.internal.BeckerBindingConstants.NULL;
import static org.openhab.binding.becker.internal.util.BeckerUtil.nonNullElse;

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
        // empty
    }

    @Override
    public String toString() {
        return String.format("%s:%s[%d]", nonNullElse(type, NULL), nonNullElse(subtype, NULL), id);
    }
}
