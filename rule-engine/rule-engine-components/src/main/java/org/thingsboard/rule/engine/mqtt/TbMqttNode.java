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
package org.thingsboard.rule.engine.mqtt;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.MqttClient;
import org.thingsboard.mqtt.MqttClientConfig;
import org.thingsboard.mqtt.MqttConnectResult;
import org.springframework.util.StringUtils;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.rule.engine.api.*;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;

import javax.net.ssl.SSLException;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RuleNode(
        type = ComponentType.EXTERNAL,
        name = "mqtt",
        configClazz = TbMqttNodeConfiguration.class,
        nodeDescription = "Publish messages to the MQTT broker",
        nodeDetails = "Will publish message payload to the MQTT broker with QoS <b>AT_LEAST_ONCE</b>.",
        uiResources = {"static/rulenode/rulenode-core-config.js"},
        configDirective = "tbActionNodeMqttConfig",
        icon = "call_split"
)
public class TbMqttNode implements TbNode {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static final String ERROR = "error";

    protected TbMqttNodeConfiguration mqttNodeConfiguration;

    protected MqttClient mqttClient;

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        try {
            this.mqttNodeConfiguration = TbNodeUtils.convert(configuration, TbMqttNodeConfiguration.class);
            this.mqttClient = initClient(ctx);
        } catch (Exception e) {
            throw new TbNodeException(e);
        }
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) {
        String topic = TbNodeUtils.processPattern(this.mqttNodeConfiguration.getTopicPattern(), msg.getMetaData());
        this.mqttClient.publish(topic, Unpooled.wrappedBuffer(msg.getData().getBytes(UTF8)), MqttQoS.AT_LEAST_ONCE)
                .addListener(future -> {
                    if (future.isSuccess()) {
                        ctx.tellSuccess(msg);
                    } else {
                        TbMsg next = processException(ctx, msg, future.cause());
                        ctx.tellFailure(next, future.cause());
                    }
                }
        );
    }

    private TbMsg processException(TbContext ctx, TbMsg origMsg, Throwable e) {
        TbMsgMetaData metaData = origMsg.getMetaData().copy();
        metaData.putValue(ERROR, e.getClass() + ": " + e.getMessage());
        return ctx.transformMsg(origMsg, origMsg.getType(), origMsg.getOriginator(), metaData, origMsg.getData());
    }

    @Override
    public void destroy() {
        if (this.mqttClient != null) {
            this.mqttClient.disconnect();
        }
    }

    protected MqttClient initClient(TbContext ctx) throws Exception {
        Optional<SslContext> sslContextOpt = initSslContext();
        MqttClientConfig config = sslContextOpt.isPresent() ? new MqttClientConfig(sslContextOpt.get()) : new MqttClientConfig();
        if (!StringUtils.isEmpty(this.mqttNodeConfiguration.getClientId())) {
            config.setClientId(this.mqttNodeConfiguration.getClientId());
        }
        config.setCleanSession(this.mqttNodeConfiguration.isCleanSession());
        this.mqttNodeConfiguration.getCredentials().configure(config);
        MqttClient client = MqttClient.create(config, null);
        client.setEventLoop(ctx.getSharedEventLoop());
        Future<MqttConnectResult> connectFuture = client.connect(this.mqttNodeConfiguration.getHost(), this.mqttNodeConfiguration.getPort());
        MqttConnectResult result;
        try {
            result = connectFuture.get(this.mqttNodeConfiguration.getConnectTimeoutSec(), TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            connectFuture.cancel(true);
            client.disconnect();
            String hostPort = this.mqttNodeConfiguration.getHost() + ":" + this.mqttNodeConfiguration.getPort();
            throw new RuntimeException(String.format("Failed to connect to MQTT broker at %s.", hostPort));
        }
        if (!result.isSuccess()) {
            connectFuture.cancel(true);
            client.disconnect();
            String hostPort = this.mqttNodeConfiguration.getHost() + ":" + this.mqttNodeConfiguration.getPort();
            throw new RuntimeException(String.format("Failed to connect to MQTT broker at %s. Result code is: %s", hostPort, result.getReturnCode()));
        }
        return client;
    }

    private Optional<SslContext> initSslContext() throws SSLException {
        Optional<SslContext> result = this.mqttNodeConfiguration.getCredentials().initSslContext();
        if (this.mqttNodeConfiguration.isSsl() && !result.isPresent()) {
            result = Optional.of(SslContextBuilder.forClient().build());
        }
        return result;
    }

}
