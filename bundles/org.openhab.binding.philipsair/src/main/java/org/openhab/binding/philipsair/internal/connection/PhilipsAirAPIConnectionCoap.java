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
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map.Entry;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.StringUtils;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.elements.exception.ConnectorException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.philipsair.internal.PhilipsAirConfiguration;
import org.openhab.binding.philipsair.internal.model.PhilipsAirPurifierDataDTO;
import org.openhab.binding.philipsair.internal.model.PhilipsAirPurifierDeviceDTO;
import org.openhab.binding.philipsair.internal.model.PhilipsAirPurifierFiltersDTO;
import org.openhab.binding.philipsair.internal.model.PhilipsAirPurifierWritableDataDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles communication with Philips Air purifiers AC2729 and AC2889 and others
 *
 * @author Stefan Machura - Modifications for COAP
 *
 */

@NonNullByDefault
public class PhilipsAirAPIConnectionCoap implements PhilipsAirAPIConnection {
    private static final Logger logger = LoggerFactory.getLogger(PhilipsAirAPIConnectionCoap.class);
    private static final String SYNC_URL = "sys/dev/sync";
    private static final String CONTROL_URL = "sys/dev/control";
    private static final String STATUS_URL = "sys/dev/status";

    // character encoding to use for communication
    private static final String ENCODING = "UTF-8";
    // secret to use for encrypted communication
    private static final String SECRET = "JiangPan";
    // timeout for observations before resynchronizing
    private static final long OBSERVATION_TIMEOUT_MS = 30000;
    // timeout for synchronous calls and first observation before failing the connection
    private static final long CONNECTION_TIMEOUT_MS = 5000;

    private final Gson gson = new Gson();
    private final PhilipsAirConfiguration config;
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
        this.config = config;
        syncClient = new CoapClient("coap", config.getHost(), 5683, SYNC_URL);
        statusClient = new CoapClient("coap", config.getHost(), 5683, STATUS_URL);
        controlClient = new CoapClient("coap", config.getHost(), 5683, CONTROL_URL);
        syncClient.setTimeout(CONNECTION_TIMEOUT_MS);
        statusClient.setTimeout(CONNECTION_TIMEOUT_MS);
        controlClient.setTimeout(CONNECTION_TIMEOUT_MS);
    }

    @Override
    public void dispose() {
        logger.debug("disposing");
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
        @Nullable PhilipsAirPurifierDeviceDTO dto = gson.fromJson(getObservation(), PhilipsAirPurifierDeviceDTO.class);
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
                }
                else {
                    logger.debug("Waiting for observation");
                    wait(CONNECTION_TIMEOUT_MS);
                    if (observation == null) {
                        throw new PhilipsAirAPIException("Device did not send respond within time limits");
                    }                    
                }
            }
            return observation;
        } 
        catch (InterruptedException e) {
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
        try {
            // verify message length and split into compontents
            if (message.length() < 8 + 64) {
                logger.debug("Ignoring message with unexpected length");
                return;
            }
            final String id = message.substring(0, 8);
            final String content = message.substring(8, message.length() - 64);
            final String digest = message.substring(message.length() - 64);
            // verify and increment message id
            if (!id.equals(toHex(lastClientId + 1))) {
                logger.debug("Ignoring message with unexpected message id {} instead of {}", id,
                        toHex(lastClientId + 1));
                return;
            }
            ++lastClientId;
            // verify digest
            if (!digest.equals(digest(id + content))) {
                logger.debug("Ignoring message with mismatching digest");
                return;
            }
            // decrypt content
            final String decrypted = decrypt(SECRET + id, content);            
            logger.trace("Received observation {}", decrypted);
            // unwrap content and notify receivers
            observationTime = System.currentTimeMillis();
            observation = new JsonParser().parse(decrypted).getAsJsonObject().getAsJsonObject("state")
                    .getAsJsonObject("reported");
            notifyAll();
        } catch (GeneralSecurityException | DecoderException | UnsupportedEncodingException e) {
            logger.warn("Decoding of observation failed: {}", e.getLocalizedMessage(), e);
        }
    }
    
    @Override
    public synchronized @Nullable PhilipsAirPurifierDataDTO sendCommand(String parameter, PhilipsAirPurifierWritableDataDTO value)
            throws GeneralSecurityException {
        try {
            final JsonObject command = new JsonObject();
            command.addProperty("CommandType", "app");
            command.addProperty("DeviceId", config.getDeviceUUID());
            command.addProperty("EnduserId", "");
            for (Entry<String, JsonElement> entry : gson.toJsonTree(value).getAsJsonObject().entrySet()) {
                command.add(entry.getKey(), entry.getValue());
            }
            final String content = "{\"state\":{\"desired\":" + command.toString() + "}}";
            logger.trace("Sending message {}", content);
            final String id = toHex(++lastServerId);
            final String encrypted = encrypt(SECRET + id, content);
            final String digest = digest(id + encrypted);
            final String message = id + encrypted + digest;
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
        }
        catch (IOException | ConnectorException e) {
            throw new PhilipsAirAPIException(e.getLocalizedMessage(), e);
        }
    }

    private static final String encrypt(final String key, final String data) 
            throws GeneralSecurityException, UnsupportedEncodingException {
        final MessageDigest md = MessageDigest.getInstance("MD5");
        final byte[] keyAndIv = md.digest(key.getBytes(ENCODING));
        final SecretKeySpec keySpec = new SecretKeySpec(toHexBinary(keyAndIv, 0, 8).getBytes(ENCODING), "AES");
        final IvParameterSpec ivSpec = new IvParameterSpec(toHexBinary(keyAndIv, 8, 8).getBytes(ENCODING));
        final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        return toHexBinary(cipher.doFinal(data.getBytes(ENCODING)));
    }

    private static final String decrypt(final String key, final String data)
            throws GeneralSecurityException, DecoderException, UnsupportedEncodingException {
        final MessageDigest md = MessageDigest.getInstance("MD5");
        final byte[] keyAndIv = md.digest(key.getBytes(ENCODING));
        final SecretKeySpec keySpec = new SecretKeySpec(toHexBinary(keyAndIv, 0, 8).getBytes(ENCODING), "AES");
        final IvParameterSpec ivSpec = new IvParameterSpec(toHexBinary(keyAndIv, 8, 8).getBytes(ENCODING));
        final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return new String(cipher.doFinal(fromHexBinary(data)), ENCODING);
    }

    private static final String digest(final String data)
            throws GeneralSecurityException, UnsupportedEncodingException {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return toHexBinary(digest.digest(data.getBytes(ENCODING)));
    }

    private static long fromHex(final String value) {
        return Long.valueOf(value, 16) & 0xFFFFFFFF;
    }

    private static String toHex(final long value) {
        return StringUtils.leftPad(Integer.toHexString((int) (value & 0xFFFFFFFF)).toUpperCase(), 8, '0');
    }

    private static byte[] fromHexBinary(final String value) throws DecoderException {
        return Hex.decodeHex(value.toUpperCase().toCharArray());
    }

    private static String toHexBinary(final byte[] binary) {
        return new String(Hex.encodeHex(binary)).toUpperCase();
    }

    private static String toHexBinary(final byte[] binary, final int offset, final int length) {
        return new String(Hex.encodeHex(Arrays.copyOfRange(binary, offset, offset + length))).toUpperCase();
    }

    @Override
    public PhilipsAirConfiguration getConfig() {
        return this.config;
    }
}
