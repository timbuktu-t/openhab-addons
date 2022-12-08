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

// non-transient members are serialized as parameters

@NonNullByDefault
public abstract class BeckerCommand<R extends BeckerCommand.Result> {

    transient protected final String method;
    transient final Class<R> resultType;

    protected BeckerCommand(String method, Class<R> resultType) {
        this.method = method;
        this.resultType = resultType;
    }

    // subclasses are encouraged to override toString() for meaningful log entries

    @Override
    public String toString() {
        return method;
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
