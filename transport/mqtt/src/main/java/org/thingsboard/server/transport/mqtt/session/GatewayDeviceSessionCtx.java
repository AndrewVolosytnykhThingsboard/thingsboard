/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2018 ThingsBoard, Inc. All Rights Reserved.
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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.mqtt.*;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.id.SessionId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.KvEntry;
import org.thingsboard.server.common.msg.core.*;
import org.thingsboard.server.common.msg.kv.AttributesKVMsg;
import org.thingsboard.server.common.msg.session.*;
import org.thingsboard.server.common.msg.session.ex.SessionException;
import org.thingsboard.server.common.transport.adaptor.JsonConverter;
import org.thingsboard.server.common.transport.session.DeviceAwareSessionContext;
import org.thingsboard.server.transport.mqtt.MqttTopics;
import org.thingsboard.server.transport.mqtt.MqttTransportHandler;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ashvayka on 19.01.17.
 */
public class GatewayDeviceSessionCtx extends DeviceAwareSessionContext {

    private static final Gson GSON = new Gson();
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final ByteBufAllocator ALLOCATOR = new UnpooledByteBufAllocator(false);
    public static final String DEVICE_PROPERTY = "device";

    private GatewaySessionCtx parent;
    private final MqttSessionId sessionId;
    private volatile boolean closed;
    private AtomicInteger msgIdSeq = new AtomicInteger(0);

    public GatewayDeviceSessionCtx(GatewaySessionCtx parent, Device device) {
        super(parent.getProcessor(), parent.getAuthService(), device);
        this.parent = parent;
        this.sessionId = new MqttSessionId();
    }

    @Override
    public SessionId getSessionId() {
        return sessionId;
    }

    @Override
    public SessionType getSessionType() {
        return SessionType.ASYNC;
    }

    @Override
    public void onMsg(SessionActorToAdaptorMsg sessionMsg) throws SessionException {
        Optional<MqttMessage> message = getToDeviceMsg(sessionMsg);
        message.ifPresent(parent::writeAndFlush);
    }

    private Optional<MqttMessage> getToDeviceMsg(SessionActorToAdaptorMsg sessionMsg) {
        ToDeviceMsg msg = sessionMsg.getMsg();
        switch (msg.getSessionMsgType()) {
            case STATUS_CODE_RESPONSE:
                ResponseMsg<?> responseMsg = (ResponseMsg) msg;
                if (responseMsg.isSuccess()) {
                    SessionMsgType requestMsgType = responseMsg.getRequestMsgType();
                    Integer requestId = responseMsg.getRequestId();
                    if (requestId >= 0 && (requestMsgType == SessionMsgType.POST_ATTRIBUTES_REQUEST || requestMsgType == SessionMsgType.POST_TELEMETRY_REQUEST)) {
                        return Optional.of(MqttTransportHandler.createMqttPubAckMsg(requestId));
                    }
                }
                break;
            case GET_ATTRIBUTES_RESPONSE:
                GetAttributesResponse response = (GetAttributesResponse) msg;
                if (response.isSuccess()) {
                    return Optional.of(createMqttPublishMsg(MqttTopics.GATEWAY_ATTRIBUTES_RESPONSE_TOPIC, response));
                } else {
                    //TODO: push error handling to the gateway
                }
                break;
            case ATTRIBUTES_UPDATE_NOTIFICATION:
                AttributesUpdateNotification notification = (AttributesUpdateNotification) msg;
                return Optional.of(createMqttPublishMsg(MqttTopics.GATEWAY_ATTRIBUTES_TOPIC, notification.getData()));
            case TO_DEVICE_RPC_REQUEST:
                ToDeviceRpcRequestMsg rpcRequest = (ToDeviceRpcRequestMsg) msg;
                return Optional.of(createMqttPublishMsg(MqttTopics.GATEWAY_RPC_TOPIC, rpcRequest));
            default:
                break;
        }
        return Optional.empty();
    }

    @Override
    public void onMsg(SessionCtrlMsg msg) throws SessionException {
        //Do nothing
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    @Override
    public long getTimeout() {
        return 0;
    }

    private MqttMessage createMqttPublishMsg(String topic, GetAttributesResponse response) {
        JsonObject result = new JsonObject();
        result.addProperty("id", response.getRequestId());
        result.addProperty(DEVICE_PROPERTY, device.getName());
        Optional<AttributesKVMsg> responseData = response.getData();
        if (responseData.isPresent()) {
            AttributesKVMsg msg = responseData.get();
            if (msg.getClientAttributes() != null) {
                addValues(result, msg.getClientAttributes());
            }
            if (msg.getSharedAttributes() != null) {
                addValues(result, msg.getSharedAttributes());
            }
        }
        return createMqttPublishMsg(topic, result);
    }

    private void addValues(JsonObject result, List<AttributeKvEntry> kvList) {
        if (kvList.size() == 1) {
            addValueToJson(result, "value", kvList.get(0));
        } else {
            JsonObject values;
            if (result.has("values")) {
                values = result.get("values").getAsJsonObject();
            } else {
                values = new JsonObject();
                result.add("values", values);
            }
            kvList.forEach(value -> addValueToJson(values, value.getKey(), value));
        }
    }

    private void addValueToJson(JsonObject json, String name, KvEntry entry) {
        switch (entry.getDataType()) {
            case BOOLEAN:
                entry.getBooleanValue().ifPresent(aBoolean -> json.addProperty(name, aBoolean));
                break;
            case STRING:
                entry.getStrValue().ifPresent(aString -> json.addProperty(name, aString));
                break;
            case DOUBLE:
                entry.getDoubleValue().ifPresent(aDouble -> json.addProperty(name, aDouble));
                break;
            case LONG:
                entry.getLongValue().ifPresent(aLong -> json.addProperty(name, aLong));
                break;
        }
    }

    private MqttMessage createMqttPublishMsg(String topic, AttributesKVMsg data) {
        JsonObject result = new JsonObject();
        result.addProperty(DEVICE_PROPERTY, device.getName());
        result.add("data", JsonConverter.toJson(data, false));
        return createMqttPublishMsg(topic, result);
    }

    private MqttMessage createMqttPublishMsg(String topic, ToDeviceRpcRequestMsg data) {
        JsonObject result = new JsonObject();
        result.addProperty(DEVICE_PROPERTY, device.getName());
        result.add("data", JsonConverter.toJson(data, true));
        return createMqttPublishMsg(topic, result);
    }

    private MqttPublishMessage createMqttPublishMsg(String topic, JsonElement json) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.AT_LEAST_ONCE, false, 0);
        MqttPublishVariableHeader header = new MqttPublishVariableHeader(topic, msgIdSeq.incrementAndGet());
        ByteBuf payload = ALLOCATOR.buffer();
        payload.writeBytes(GSON.toJson(json).getBytes(UTF8));
        return new MqttPublishMessage(mqttFixedHeader, header, payload);
    }

}
