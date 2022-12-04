package org.openhab.binding.becker.internal.socket;

import static org.eclipse.jdt.annotation.Checks.requireNonNull;
import static org.openhab.binding.becker.internal.BeckerBindingConstants.NULL;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

// non-transient members are serialized as parameters

@NonNullByDefault
public abstract class BeckerCommand<R extends BeckerCommand.Result> {

    transient final String method;
    transient final Class<R> resultType;

    protected BeckerCommand(String method, Class<R> resultType) {
        this.method = method;
        this.resultType = resultType;
    }

    // subclasses are encouraged to override toString() for meaningful log entries

    @Override
    public String toString() {
        return requireNonNull(String.format("%s", method));
    }

    // non-transient members are deserialized from result in JSON-RPC response

    public static abstract class Result {

        // subclasses are encouraged to override toString() for meaningful log entries

        @Override
        public String toString() {
            return NULL;
        }
    }
}
