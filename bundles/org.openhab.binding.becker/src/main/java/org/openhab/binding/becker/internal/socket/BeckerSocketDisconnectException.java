package org.openhab.binding.becker.internal.socket;

import java.util.concurrent.CancellationException;

import org.eclipse.jdt.annotation.NonNullByDefault;

// indicates that socket was disconnected explicitly using disconnect or dispose
// this indicates to the bridge handler that no further connection attempts should be made until re-initialization

@NonNullByDefault
public final class BeckerSocketDisconnectException extends CancellationException {

    private static final long serialVersionUID = 1L;

    public BeckerSocketDisconnectException(String message) {
        super(message);
    }
}
