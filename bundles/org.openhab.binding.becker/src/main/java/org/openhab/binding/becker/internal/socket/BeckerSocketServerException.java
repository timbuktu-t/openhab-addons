package org.openhab.binding.becker.internal.socket;

import static org.openhab.binding.becker.internal.BeckerBindingConstants.NULL;
import static org.openhab.binding.becker.internal.util.BeckerUtil.nonNullElse;

import java.net.SocketException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

@NonNullByDefault
public final class BeckerSocketServerException extends SocketException {

    private static final long serialVersionUID = 1L;

    private final int code;
    private final @Nullable String reason;

    public int code() {
        return code;
    }

    public @Nullable String reason() {
        return reason;
    }

    public BeckerSocketServerException(int code, @Nullable String reason) {
        super(String.format("Server reported <%s> [%d]", nonNullElse(reason, NULL), code));
        this.code = code;
        this.reason = reason;
    }
}
