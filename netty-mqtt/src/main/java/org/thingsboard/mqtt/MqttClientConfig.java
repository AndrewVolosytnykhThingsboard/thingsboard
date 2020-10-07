/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 *
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes
 * information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
 * OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
 * THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
 * DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
 * OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
 */
package org.thingsboard.mqtt;

import io.netty.channel.Channel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.handler.ssl.SslContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Random;

@SuppressWarnings({"WeakerAccess", "unused"})
public final class MqttClientConfig {

    private final SslContext sslContext;
    private final String randomClientId;

    private String clientId;
    private int timeoutSeconds = 60;
    private MqttVersion protocolVersion = MqttVersion.MQTT_3_1;
    @Nullable private String username = null;
    @Nullable private String password = null;
    private boolean cleanSession = true;
    @Nullable private MqttLastWill lastWill;
    private Class<? extends Channel> channelClass = NioSocketChannel.class;

    private boolean reconnect = true;
    private long reconnectDelay = 1L;
    private int maxBytesInMessage = 32368;

    public MqttClientConfig() {
        this(null);
    }

    public MqttClientConfig(SslContext sslContext) {
        this.sslContext = sslContext;
        Random random = new Random();
        String id = "netty-mqtt/";
        String[] options = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".split("");
        for(int i = 0; i < 8; i++){
            id += options[random.nextInt(options.length)];
        }
        this.clientId = id;
        this.randomClientId = id;
    }

    @Nonnull
    public String getClientId() {
        return clientId;
    }

    public void setClientId(@Nullable String clientId) {
        if(clientId == null){
            this.clientId = randomClientId;
        }else{
            this.clientId = clientId;
        }
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        if(timeoutSeconds != -1 && timeoutSeconds <= 0){
            throw new IllegalArgumentException("timeoutSeconds must be > 0 or -1");
        }
        this.timeoutSeconds = timeoutSeconds;
    }

    public MqttVersion getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(MqttVersion protocolVersion) {
        if(protocolVersion == null){
            throw new NullPointerException("protocolVersion");
        }
        this.protocolVersion = protocolVersion;
    }

    @Nullable
    public String getUsername() {
        return username;
    }

    public void setUsername(@Nullable String username) {
        this.username = username;
    }

    @Nullable
    public String getPassword() {
        return password;
    }

    public void setPassword(@Nullable String password) {
        this.password = password;
    }

    public boolean isCleanSession() {
        return cleanSession;
    }

    public void setCleanSession(boolean cleanSession) {
        this.cleanSession = cleanSession;
    }

    @Nullable
    public MqttLastWill getLastWill() {
        return lastWill;
    }

    public void setLastWill(@Nullable MqttLastWill lastWill) {
        this.lastWill = lastWill;
    }

    public Class<? extends Channel> getChannelClass() {
        return channelClass;
    }

    public void setChannelClass(Class<? extends Channel> channelClass) {
        this.channelClass = channelClass;
    }

    public SslContext getSslContext() {
        return sslContext;
    }

    public boolean isReconnect() {
        return reconnect;
    }

    public void setReconnect(boolean reconnect) {
        this.reconnect = reconnect;
    }

    public long getReconnectDelay() {
        return reconnectDelay;
    }

    /**
     * Sets the reconnect delay in seconds. Defaults to 1 second.
     * @param reconnectDelay
     * @throws IllegalArgumentException if reconnectDelay is smaller than 1.
     */
    public void setReconnectDelay(long reconnectDelay) {
        if (reconnectDelay <= 0) {
            throw new IllegalArgumentException("reconnectDelay must be > 0");
        }
        this.reconnectDelay = reconnectDelay;
    }

    public int getMaxBytesInMessage() {
        return maxBytesInMessage;
    }

    /**
     * Sets the maximum number of bytes in the message for the {@link io.netty.handler.codec.mqtt.MqttDecoder}.
     * Default value is 8092 as specified by Netty. The absolute maximum size is 256MB as set by the MQTT spec.
     *
     * @param maxBytesInMessage
     * @throws IllegalArgumentException if maxBytesInMessage is smaller than 1 or greater than 256_000_000.
     */
    public void setMaxBytesInMessage(int maxBytesInMessage) {
        if (maxBytesInMessage <= 0 || maxBytesInMessage > 256_000_000) {
            throw new IllegalArgumentException("maxBytesInMessage must be > 0 or < 256_000_000");
        }
        this.maxBytesInMessage = maxBytesInMessage;
    }
}
