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
package org.thingsboard.server.transport.mqtt.session;


import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.adaptor.AdaptorException;
import org.thingsboard.server.common.adaptor.JsonConverter;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.transport.TransportService;
import org.thingsboard.server.common.transport.TransportServiceCallback;
import org.thingsboard.server.common.transport.service.AbstractTransportService;
import org.thingsboard.server.gen.transport.ClaimDeviceMsg;
import org.thingsboard.server.gen.transport.DeviceInfoProto;
import org.thingsboard.server.gen.transport.GetAttributeRequestMsg;
import org.thingsboard.server.gen.transport.GetOrCreateDeviceFromGatewayRequestMsg;
import org.thingsboard.server.gen.transport.GetOrCreateDeviceFromGatewayResponseMsg;
import org.thingsboard.server.gen.transport.PostAttributeMsg;
import org.thingsboard.server.gen.transport.PostTelemetryMsg;
import org.thingsboard.server.gen.transport.SessionEvent;
import org.thingsboard.server.gen.transport.SessionInfoProto;
import org.thingsboard.server.gen.transport.SubscribeToAttributeUpdatesMsg;
import org.thingsboard.server.gen.transport.SubscribeToRPCMsg;
import org.thingsboard.server.gen.transport.ToDeviceRpcResponseMsg;
import org.thingsboard.server.transport.mqtt.MqttTransportContext;
import org.thingsboard.server.transport.mqtt.MqttTransportHandler;
import org.thingsboard.server.transport.mqtt.adaptors.JsonMqttAdaptor;
import org.thingsboard.server.transport.mqtt.adaptors.MqttTransportAdaptor;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by ashvayka on 19.01.17.
 */
@Slf4j
public class GatewaySessionHandler {

    private static final String DEFAULT_DEVICE_TYPE = "default";
    private static final String CAN_T_PARSE_VALUE = "Can't parse value: ";
    private static final String DEVICE_PROPERTY = "device";

    private final MqttTransportContext context;
    private final TransportService transportService;
    private final DeviceInfoProto gateway;
    private final UUID sessionId;
    private final Map<String, GatewayDeviceSessionCtx> devices;
    private final ConcurrentMap<MqttTopicMatcher, Integer> mqttQoSMap;
    private final ChannelHandlerContext channel;
    private final DeviceSessionCtx deviceSessionCtx;

    public GatewaySessionHandler(MqttTransportContext context, DeviceSessionCtx deviceSessionCtx, UUID sessionId) {
        this.context = context;
        this.transportService = context.getTransportService();
        this.deviceSessionCtx = deviceSessionCtx;
        this.gateway = deviceSessionCtx.getDeviceInfo();
        this.sessionId = sessionId;
        this.devices = new ConcurrentHashMap<>();
        this.mqttQoSMap = deviceSessionCtx.getMqttQoSMap();
        this.channel = deviceSessionCtx.getChannel();
    }

    public void onDeviceConnect(MqttPublishMessage msg) throws AdaptorException {
        JsonElement json = getJson(msg);
        String deviceName = checkDeviceName(getDeviceName(json));
        String deviceType = getDeviceType(json);
        log.trace("[{}] onDeviceConnect: {}", sessionId, deviceName);
        Futures.addCallback(onDeviceConnect(deviceName, deviceType), new FutureCallback<GatewayDeviceSessionCtx>() {
            @Override
            public void onSuccess(@Nullable GatewayDeviceSessionCtx result) {
                ack(msg);
                log.trace("[{}] onDeviceConnectOk: {}", sessionId, deviceName);
            }

            @Override
            public void onFailure(Throwable t) {
                log.warn("[{}] Failed to process device connect command: {}", sessionId, deviceName, t);

            }
        }, context.getExecutor());
    }

    private ListenableFuture<GatewayDeviceSessionCtx> onDeviceConnect(String deviceName, String deviceType) {
        SettableFuture<GatewayDeviceSessionCtx> future = SettableFuture.create();
        GatewayDeviceSessionCtx result = devices.get(deviceName);
        if (result == null) {
            transportService.process(GetOrCreateDeviceFromGatewayRequestMsg.newBuilder()
                            .setDeviceName(deviceName)
                            .setDeviceType(deviceType)
                            .setGatewayIdMSB(gateway.getDeviceIdMSB())
                            .setGatewayIdLSB(gateway.getDeviceIdLSB()).build(),
                    new TransportServiceCallback<GetOrCreateDeviceFromGatewayResponseMsg>() {
                        @Override
                        public void onSuccess(GetOrCreateDeviceFromGatewayResponseMsg msg) {
                            GatewayDeviceSessionCtx deviceSessionCtx = new GatewayDeviceSessionCtx(GatewaySessionHandler.this, msg.getDeviceInfo(), mqttQoSMap);
                            if (devices.putIfAbsent(deviceName, deviceSessionCtx) == null) {
                                SessionInfoProto deviceSessionInfo = deviceSessionCtx.getSessionInfo();
                                transportService.registerAsyncSession(deviceSessionInfo, deviceSessionCtx);
                                transportService.process(deviceSessionInfo, AbstractTransportService.getSessionEventMsg(SessionEvent.OPEN), null);
                                transportService.process(deviceSessionInfo, SubscribeToRPCMsg.getDefaultInstance(), null);
                                transportService.process(deviceSessionInfo, SubscribeToAttributeUpdatesMsg.getDefaultInstance(), null);
                            }
                            future.set(devices.get(deviceName));
                        }

                        @Override
                        public void onError(Throwable e) {
                            log.warn("[{}] Failed to process device connect command: {}", sessionId, deviceName, e);
                            future.setException(e);
                        }
                    });
        } else {
            future.set(result);
        }
        return future;
    }

    public void onDeviceDisconnect(MqttPublishMessage msg) throws AdaptorException {
        String deviceName = checkDeviceName(getDeviceName(getJson(msg)));
        deregisterSession(deviceName);
        ack(msg);
    }

    void deregisterSession(String deviceName) {
        GatewayDeviceSessionCtx deviceSessionCtx = devices.remove(deviceName);
        if (deviceSessionCtx != null) {
            deregisterSession(deviceName, deviceSessionCtx);
        } else {
            log.debug("[{}] Device [{}] was already removed from the gateway session", sessionId, deviceName);
        }
    }

    public void onGatewayDisconnect() {
        devices.forEach(this::deregisterSession);
    }

    public void onDeviceTelemetry(MqttPublishMessage mqttMsg) throws AdaptorException {
        JsonElement json = JsonMqttAdaptor.validateJsonPayload(sessionId, mqttMsg.payload());
        int msgId = mqttMsg.variableHeader().packetId();
        if (json.isJsonObject()) {
            JsonObject jsonObj = json.getAsJsonObject();
            for (Map.Entry<String, JsonElement> deviceEntry : jsonObj.entrySet()) {
                String deviceName = deviceEntry.getKey();
                Futures.addCallback(checkDeviceConnected(deviceName),
                        new FutureCallback<GatewayDeviceSessionCtx>() {
                            @Override
                            public void onSuccess(@Nullable GatewayDeviceSessionCtx deviceCtx) {
                                if (!deviceEntry.getValue().isJsonArray()) {
                                    throw new JsonSyntaxException(CAN_T_PARSE_VALUE + json);
                                }
                                try {
                                    PostTelemetryMsg postTelemetryMsg = JsonConverter.convertToTelemetryProto(deviceEntry.getValue().getAsJsonArray());
                                    transportService.process(deviceCtx.getSessionInfo(), postTelemetryMsg, getPubAckCallback(channel, deviceName, msgId, postTelemetryMsg));
                                } catch (Throwable e) {
                                    UUID gatewayId = new UUID(gateway.getDeviceIdMSB(), gateway.getDeviceIdLSB());
                                    log.warn("[{}][{}] Failed to convert telemetry: {}", gatewayId, deviceName, deviceEntry.getValue(), e);
                                }
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                log.debug("[{}] Failed to process device telemetry command: {}", sessionId, deviceName, t);
                            }
                        }, context.getExecutor());
            }
        } else {
            throw new JsonSyntaxException(CAN_T_PARSE_VALUE + json);
        }
    }

    public void onDeviceClaim(MqttPublishMessage mqttMsg) throws AdaptorException {
        JsonElement json = JsonMqttAdaptor.validateJsonPayload(sessionId, mqttMsg.payload());
        int msgId = mqttMsg.variableHeader().packetId();
        if (json.isJsonObject()) {
            JsonObject jsonObj = json.getAsJsonObject();
            for (Map.Entry<String, JsonElement> deviceEntry : jsonObj.entrySet()) {
                String deviceName = deviceEntry.getKey();
                Futures.addCallback(checkDeviceConnected(deviceName),
                        new FutureCallback<GatewayDeviceSessionCtx>() {
                            @Override
                            public void onSuccess(@Nullable GatewayDeviceSessionCtx deviceCtx) {
                                if (!deviceEntry.getValue().isJsonObject()) {
                                    throw new JsonSyntaxException(CAN_T_PARSE_VALUE + json);
                                }
                                try {
                                    DeviceId deviceId = deviceCtx.getDeviceId();
                                    ClaimDeviceMsg claimDeviceMsg = JsonConverter.convertToClaimDeviceProto(deviceId, deviceEntry.getValue());
                                    transportService.process(deviceCtx.getSessionInfo(), claimDeviceMsg, getPubAckCallback(channel, deviceName, msgId, claimDeviceMsg));
                                } catch (Throwable e) {
                                    UUID gatewayId = new UUID(gateway.getDeviceIdMSB(), gateway.getDeviceIdLSB());
                                    log.warn("[{}][{}] Failed to convert claim message: {}", gatewayId, deviceName, deviceEntry.getValue(), e);
                                }
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                log.debug("[{}] Failed to process device claiming command: {}", sessionId, deviceName, t);
                            }
                        }, context.getExecutor());
            }
        } else {
            throw new JsonSyntaxException(CAN_T_PARSE_VALUE + json);
        }
    }

    public void onDeviceAttributes(MqttPublishMessage mqttMsg) throws AdaptorException {
        JsonElement json = JsonMqttAdaptor.validateJsonPayload(sessionId, mqttMsg.payload());
        int msgId = mqttMsg.variableHeader().packetId();
        if (json.isJsonObject()) {
            JsonObject jsonObj = json.getAsJsonObject();
            for (Map.Entry<String, JsonElement> deviceEntry : jsonObj.entrySet()) {
                String deviceName = deviceEntry.getKey();
                Futures.addCallback(checkDeviceConnected(deviceName),
                        new FutureCallback<GatewayDeviceSessionCtx>() {
                            @Override
                            public void onSuccess(@Nullable GatewayDeviceSessionCtx deviceCtx) {
                                if (!deviceEntry.getValue().isJsonObject()) {
                                    throw new JsonSyntaxException(CAN_T_PARSE_VALUE + json);
                                }
                                PostAttributeMsg postAttributeMsg = JsonConverter.convertToAttributesProto(deviceEntry.getValue().getAsJsonObject());
                                transportService.process(deviceCtx.getSessionInfo(), postAttributeMsg, getPubAckCallback(channel, deviceName, msgId, postAttributeMsg));
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                log.debug("[{}] Failed to process device attributes command: {}", sessionId, deviceName, t);
                            }
                        }, context.getExecutor());
            }
        } else {
            throw new JsonSyntaxException(CAN_T_PARSE_VALUE + json);
        }
    }

    public void onDeviceRpcResponse(MqttPublishMessage mqttMsg) throws AdaptorException {
        JsonElement json = JsonMqttAdaptor.validateJsonPayload(sessionId, mqttMsg.payload());
        int msgId = mqttMsg.variableHeader().packetId();
        if (json.isJsonObject()) {
            JsonObject jsonObj = json.getAsJsonObject();
            String deviceName = jsonObj.get(DEVICE_PROPERTY).getAsString();
            Futures.addCallback(checkDeviceConnected(deviceName),
                    new FutureCallback<GatewayDeviceSessionCtx>() {
                        @Override
                        public void onSuccess(@Nullable GatewayDeviceSessionCtx deviceCtx) {
                            Integer requestId = jsonObj.get("id").getAsInt();
                            String data = jsonObj.get("data").toString();
                            ToDeviceRpcResponseMsg rpcResponseMsg = ToDeviceRpcResponseMsg.newBuilder()
                                    .setRequestId(requestId).setPayload(data).build();
                            transportService.process(deviceCtx.getSessionInfo(), rpcResponseMsg, getPubAckCallback(channel, deviceName, msgId, rpcResponseMsg));
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            log.debug("[{}] Failed to process device teleemtry command: {}", sessionId, deviceName, t);
                        }
                    }, context.getExecutor());
        } else {
            throw new JsonSyntaxException(CAN_T_PARSE_VALUE + json);
        }
    }

    public void onDeviceAttributesRequest(MqttPublishMessage msg) throws AdaptorException {
        JsonElement json = JsonMqttAdaptor.validateJsonPayload(sessionId, msg.payload());
        if (json.isJsonObject()) {
            JsonObject jsonObj = json.getAsJsonObject();
            int requestId = jsonObj.get("id").getAsInt();
            String deviceName = jsonObj.get(DEVICE_PROPERTY).getAsString();
            boolean clientScope = jsonObj.get("client").getAsBoolean();
            Set<String> keys;
            if (jsonObj.has("key")) {
                keys = Collections.singleton(jsonObj.get("key").getAsString());
            } else {
                JsonArray keysArray = jsonObj.get("keys").getAsJsonArray();
                keys = new HashSet<>();
                for (JsonElement keyObj : keysArray) {
                    keys.add(keyObj.getAsString());
                }
            }
            GetAttributeRequestMsg.Builder result = GetAttributeRequestMsg.newBuilder();
            result.setRequestId(requestId);

            if (clientScope) {
                result.addAllClientAttributeNames(keys);
            } else {
                result.addAllSharedAttributeNames(keys);
            }
            GetAttributeRequestMsg requestMsg = result.build();
            int msgId = msg.variableHeader().packetId();
            Futures.addCallback(checkDeviceConnected(deviceName),
                    new FutureCallback<GatewayDeviceSessionCtx>() {
                        @Override
                        public void onSuccess(@Nullable GatewayDeviceSessionCtx deviceCtx) {
                            transportService.process(deviceCtx.getSessionInfo(), requestMsg, getPubAckCallback(channel, deviceName, msgId, requestMsg));
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            ack(msg);
                            log.debug("[{}] Failed to process device attributes request command: {}", sessionId, deviceName, t);
                        }
                    }, context.getExecutor());
        } else {
            throw new JsonSyntaxException(CAN_T_PARSE_VALUE + json);
        }
    }

    private ListenableFuture<GatewayDeviceSessionCtx> checkDeviceConnected(String deviceName) {
        GatewayDeviceSessionCtx ctx = devices.get(deviceName);
        if (ctx == null) {
            log.debug("[{}] Missing device [{}] for the gateway session", sessionId, deviceName);
            return onDeviceConnect(deviceName, DEFAULT_DEVICE_TYPE);
        } else {
            return Futures.immediateFuture(ctx);
        }
    }

    private String checkDeviceName(String deviceName) {
        if (StringUtils.isEmpty(deviceName)) {
            throw new RuntimeException("Device name is empty!");
        } else {
            return deviceName;
        }
    }

    private String getDeviceName(JsonElement json) throws AdaptorException {
        return json.getAsJsonObject().get(DEVICE_PROPERTY).getAsString();
    }

    private String getDeviceType(JsonElement json) throws AdaptorException {
        JsonElement type = json.getAsJsonObject().get("type");
        return type == null || type instanceof JsonNull ? DEFAULT_DEVICE_TYPE : type.getAsString();
    }

    private JsonElement getJson(MqttPublishMessage mqttMsg) throws AdaptorException {
        return JsonMqttAdaptor.validateJsonPayload(sessionId, mqttMsg.payload());
    }

    private void ack(MqttPublishMessage msg) {
        if (msg.variableHeader().packetId() > 0) {
            writeAndFlush(MqttTransportHandler.createMqttPubAckMsg(msg.variableHeader().packetId()));
        }
    }

    void writeAndFlush(MqttMessage mqttMessage) {
        channel.writeAndFlush(mqttMessage);
    }

    public String getNodeId() {
        return context.getNodeId();
    }

    private void deregisterSession(String deviceName, GatewayDeviceSessionCtx deviceSessionCtx) {
        transportService.deregisterSession(deviceSessionCtx.getSessionInfo());
        transportService.process(deviceSessionCtx.getSessionInfo(), AbstractTransportService.getSessionEventMsg(SessionEvent.CLOSED), null);
        log.debug("[{}] Removed device [{}] from the gateway session", sessionId, deviceName);
    }

    private <T> TransportServiceCallback<Void> getPubAckCallback(final ChannelHandlerContext ctx, final String deviceName, final int msgId, final T msg) {
        return new TransportServiceCallback<Void>() {
            @Override
            public void onSuccess(Void dummy) {
                log.trace("[{}][{}] Published msg: {}", sessionId, deviceName, msg);
                if (msgId > 0) {
                    ctx.writeAndFlush(MqttTransportHandler.createMqttPubAckMsg(msgId));
                }
            }

            @Override
            public void onError(Throwable e) {
                log.trace("[{}] Failed to publish msg: {}", sessionId, deviceName, msg, e);
                ctx.close();
            }
        };
    }

    public MqttTransportContext getContext() {
        return context;
    }

    MqttTransportAdaptor getAdaptor() {
        return context.getAdaptor();
    }

    int nextMsgId() {
        return deviceSessionCtx.nextMsgId();
    }

    public void reportActivity() {
        devices.forEach((id, deviceCtx) -> transportService.reportActivity(deviceCtx.getSessionInfo()));
    }
}
