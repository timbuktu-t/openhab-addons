/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.becker.internal.socket;

import static org.openhab.binding.becker.internal.BeckerBindingConstants.NULL;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.google.gson.Gson;

// non-transient members are serialized as parameters
/**
 * The {@link BeckerCommand} represents a command that can be sent using a {@link BeckerSocket} and the result that is
 * expected after execution. Non-transient member variables of commands are serialized to JSON and results deserialized
 * from JSON by the socket using {@link Gson} so annotations can be used here.
 * 
 * @author Stefan Machura - Initial contribution
 */
@NonNullByDefault
public abstract class BeckerCommand<R extends BeckerCommand.Result> {

    transient protected final String method;
    transient final Class<R> resultType;

    /**
     * Creates a new {@link BeckerCommand}.
     * 
     * @param method the JSONRPC method to invoke
     * @param resultType the expected result type
     */
    protected BeckerCommand(String method, Class<R> resultType) {
        this.method = method;
        this.resultType = resultType;
    }

    /**
     * Returns a string representing this command for logging purposes. Subclasses should override this method for
     * meaningful log entries.
     */
    @Override
    public String toString() {
        return method;
    }

    /**
     * The {@link Result} represents the result that is expected when the command is executed.
     */
    public static abstract class Result {

        /**
         * Returns a string representing this response for logging purposes. Subclasses should override this method for
         * meaningful log entries.
         */
        @Override
        public String toString() {
            return NULL;
        }
    }
}
