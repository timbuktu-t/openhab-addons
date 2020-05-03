package org.openhab.binding.becker.internal.util;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

@NonNullByDefault
public final class BeckerUtil {

    private BeckerUtil() {
        // empty
    }

    // workaround when eclipse does not recognize that value is non-null

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
