package org.openhab.binding.becker.internal;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

// TODO (3) add JavaDoc and package-info

@NonNullByDefault
public final class BeckerNullables {

    private BeckerNullables() {
    }

    // workaround when ide does not recognize that value is non-null

    public static <T> @NonNull T nonNull(@Nullable T value) {
        if (value == null) {
            throw new NullPointerException();
        }
        return value;
    }

    public static <T> @NonNull T nonNullElse(@Nullable T value, @NonNull T fallbackValue) {
        if (value == null) {
            return fallbackValue;
        }
        return value;
    }
}