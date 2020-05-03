package org.openhab.binding.becker.internal.model;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

@NonNullByDefault
public final class BeckerNotification {

    @SerializedName("event_id")
    public int id;
    public long timestamp;
    public @Nullable String message;

}
