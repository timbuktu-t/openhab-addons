/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
package org.openhab.binding.philipsair.internal.connection;

import java.io.IOException;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.elements.exception.ConnectorException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.philipsair.internal.PhilipsAirConfiguration;
import org.openhab.binding.philipsair.internal.model.PhilipsAirPurifierDataDTO;
import org.openhab.binding.philipsair.internal.model.PhilipsAirPurifierDeviceDTO;
import org.openhab.binding.philipsair.internal.model.PhilipsAirPurifierFiltersDTO;
import org.openhab.binding.philipsair.internal.model.PhilipsAirPurifierStateDTO;
import org.openhab.binding.philipsair.internal.model.PhilipsAirPurifierStatusDTO;
import org.openhab.binding.philipsair.internal.model.PhilipsAirPurifierWritableDataDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * The {@link PhilipsAirCoapAPIConnection} is responsible for handling commands, for the 2019 and newer models
 * communicating using the coap protocol.
 *
 * @author Marcel Verpaalen - Initial contribution
 * @author Stefan Machura - Observation semantics
 */
@NonNullByDefault
public class PhilipsAirAPIConnectionCoap extends PhilipsAirAPIConnection {

    private final Logger logger = LoggerFactory.getLogger(PhilipsAirAPIConnectionCoap.class);

    // endpoints to use
    private static final String SYNC_PATH = "sys/dev/sync";
    private static final String CONTROL_PATH = "sys/dev/control";
    private static final String STATUS_PATH = "sys/dev/status";
    // port to use for communication
    private static final int PORT = 5683;
    // timeout for observations before resynchronizing
    private static final long OBSERVATION_TIMEOUT_MS = 30000;
    // timeout for synchronous calls and first observation before failing the connection
    private static final long CONNECTION_TIMEOUT_MS = 5000;

    private final Gson gson = new Gson();
    private final CoapClient syncClient;
    private final CoapClient statusClient;
    private final CoapClient controlClient;

    private long lastClientId = 0;
    private long lastServerId = 0;
    private @Nullable CoapObserveRelation statusObserveRelation = null;
    private volatile @Nullable JsonElement observation = null;
    private volatile long observationTime = 0;
    private volatile boolean observationRestarting = false;

    public PhilipsAirAPIConnectionCoap(PhilipsAirConfiguration config) {
        super(config);

        NetworkConfig netConfig = NetworkConfig.createStandardWithoutFile();
        NetworkConfig.getStandard().setString(NetworkConfig.Keys.DEDUPLICATOR, NetworkConfig.Keys.NO_DEDUPLICATOR);
        netConfig.setString(NetworkConfig.Keys.DEDUPLICATOR, NetworkConfig.Keys.NO_DEDUPLICATOR);

        CoapEndpoint endpoint = new CoapEndpoint.Builder().setNetworkConfig(netConfig).build();

        syncClient = new CoapClient("coap", config.getHost(), PORT, SYNC_PATH);
        syncClient.setEndpoint(endpoint);
        syncClient.setTimeout(CONNECTION_TIMEOUT_MS);

        statusClient = new CoapClient("coap", config.getHost(), PORT, STATUS_PATH);
        statusClient.setEndpoint(endpoint);
        statusClient.setTimeout(CONNECTION_TIMEOUT_MS);

        controlClient = new CoapClient("coap", config.getHost(), PORT, CONTROL_PATH);
        controlClient.setEndpoint(endpoint);
        controlClient.setTimeout(CONNECTION_TIMEOUT_MS);
    }

    @Override
    public void dispose() {
        logger.debug("Disposing");
        reset();
        syncClient.shutdown();
        statusClient.shutdown();
        controlClient.shutdown();
    }

    @Override
    public @Nullable PhilipsAirPurifierDataDTO getAirPurifierStatus(String host)
            throws JsonSyntaxException, PhilipsAirAPIException {
        return gson.fromJson(getObservation(), PhilipsAirPurifierDataDTO.class);
    }

    @Override
    public synchronized @Nullable PhilipsAirPurifierDeviceDTO getAirPurifierDevice(String host)
            throws JsonSyntaxException, PhilipsAirAPIException {
        @Nullable
        PhilipsAirPurifierDeviceDTO dto = gson.fromJson(getObservation(), PhilipsAirPurifierDeviceDTO.class);
        if (dto.getDeviceId() != null && !dto.getDeviceId().equals(config.getDeviceUUID())) {
            logger.debug("Setting deviceUUID to {}", dto.getDeviceId());
            config.setDeviceUUID(dto.getDeviceId());
        }
        return dto;
    }

    @Override
    public @Nullable PhilipsAirPurifierFiltersDTO getAirPurifierFiltersStatus(String host)
            throws JsonSyntaxException, PhilipsAirAPIException {
        return gson.fromJson(getObservation(), PhilipsAirPurifierFiltersDTO.class);
    }

    private synchronized @Nullable JsonElement getObservation() throws PhilipsAirAPIException {
        try {
            if (observation == null || System.currentTimeMillis() - observationTime >= OBSERVATION_TIMEOUT_MS) {
                if (!observationRestarting) {
                    try {
                        logger.debug("Restarting observation as cached data is stale");
                        observationRestarting = true;
                        reset();
                        sync();
                        observe();
                        logger.debug("Waiting for observation");
                        wait(CONNECTION_TIMEOUT_MS);
                        if (observation == null) {
                            throw new PhilipsAirAPIException("Device did not send respond within time limits");
                        }
                    } finally {
                        observationRestarting = false;
                    }
                } else {
                    logger.debug("Waiting for observation");
                    wait(CONNECTION_TIMEOUT_MS);
                    if (observation == null) {
                        throw new PhilipsAirAPIException("Device did not send respond within time limits");
                    }
                }
            }
            return observation;
        } catch (InterruptedException e) {
            throw new PhilipsAirAPIException("Interrupted while waiting for response from device", e);
        }
    }

    private void reset() {
        logger.debug("Resetting device observation");
        if (statusObserveRelation != null) {
            statusObserveRelation.reactiveCancel();
            statusObserveRelation = null;
        }
        observation = null;
        observationTime = 0;
    }

    private void sync() throws PhilipsAirAPIException {
        logger.debug("Synchronizing with device");
        try {
            final CoapResponse response = syncClient.post(toHex(lastClientId), 0);
            if (response == null) {
                throw new PhilipsAirAPIException("Synchronization failed as device is unreachable");
            }
            if (!ResponseCode.isSuccess(response.getCode())) {
                throw new PhilipsAirAPIException("Synchronization failed with status " + response.getCode());
            }
            lastServerId = fromHex(response.getResponseText());
            logger.debug("Synchronized with client id {} and server id {}", toHex(lastClientId), toHex(lastServerId));
        } catch (ConnectorException | IOException e) {
            throw new PhilipsAirAPIException("Synchronization failed", e);
        }
    }

    private void observe() {
        logger.debug("Observing device");
        statusObserveRelation = statusClient.observe(new CoapHandler() {
            @Override
            public void onLoad(final @Nullable CoapResponse response) {
                if (response == null) {
                    logger.warn("Ignoring empty observation");
                } else if (!ResponseCode.CONTENT.equals(response.getCode())) {
                    logger.warn("Received unexpected observation status {}", response.getCode());
                } else {
                    logger.trace("Received encrypted observation {}", response.getResponseText());
                    receive(response.getResponseText());
                }
            }

            @Override
            public void onError() {
                logger.warn("Connector reported communication error");
            }
        });
    }

    private synchronized void receive(final String message) {
        // verify message length and message id
        if (message.length() < 8 + 64) {
            logger.debug("Ignoring message with unexpected length");
            return;
        }
        final String id = message.substring(0, 8);
        if (!id.equals(toHex(lastClientId + 1))) {
            logger.debug("Ignoring message with unexpected message id {} instead of {}", id, toHex(lastClientId + 1));
            return;
        }
        ++lastClientId;
        final String decrypted = PhilipsAirCoapCipher.decryptMsg(message, logger);
        logger.trace("Received observation {}", decrypted);
        // unwrap content and notify receivers
        observationTime = System.currentTimeMillis();
        observation = JsonParser.parseString(decrypted).getAsJsonObject().getAsJsonObject("state")
                .getAsJsonObject("reported");
        notifyAll();
    }

    @Override
    public synchronized @Nullable PhilipsAirPurifierDataDTO sendCommand(String parameter,
            PhilipsAirPurifierWritableDataDTO value) throws PhilipsAirAPIException {
        try {
            final JsonObject command = (JsonObject) gson.toJsonTree(value);
            command.addProperty("CommandType", "app");
            command.addProperty("DeviceId", config.getDeviceUUID());
            command.addProperty("EnduserId", "");
            final PhilipsAirPurifierStateDTO state = new PhilipsAirPurifierStateDTO();
            state.setDesired(command);
            PhilipsAirPurifierStatusDTO full = new PhilipsAirPurifierStatusDTO();
            full.setState(state);
            final String content = gson.toJson(full).toString();
            logger.trace("Sending message {}", content);
            final String message = PhilipsAirCoapCipher.encryptedMsg(content, ++lastServerId, logger);
            logger.trace("Sending encrypted message {}", message);
            final CoapResponse response = controlClient.post(message, 0);
            if (response == null) {
                throw new PhilipsAirAPIException("Sending failed as device is unreachable");
            }
            if (!ResponseCode.isSuccess(response.getCode())) {
                throw new PhilipsAirAPIException("Sending failed with status " + response.getCode());
            }
            logger.trace("Received response {}", response.getResponseText());
            if (!"{\"status\":\"success\"}".equals(response.getResponseText())) {
                logger.warn("Received unexpected response {}", response.getResponseText());
            }
            // last observation is now stale due to command
            observation = null;
            observationTime = 0;
            return null;
        } catch (IOException | ConnectorException e) {
            throw new PhilipsAirAPIException(e.getLocalizedMessage(), e);
        }
    }

    private static long fromHex(final String value) {
        return Long.valueOf(value, 16) & 0xFFFFFFFF;
    }

    private static String toHex(final long value) {
        return String.format("%08X", (int) (value & 0xFFFFFFFF));
    }
}
