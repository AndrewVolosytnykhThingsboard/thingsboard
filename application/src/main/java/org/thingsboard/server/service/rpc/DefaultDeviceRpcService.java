/**
 * Thingsboard OÜ ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2018 Thingsboard OÜ. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of Thingsboard OÜ and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Thingsboard OÜ
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
package org.thingsboard.server.service.rpc;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.rule.engine.api.RpcError;
import org.thingsboard.rule.engine.api.msg.ToDeviceActorNotificationMsg;
import org.thingsboard.server.actors.service.ActorService;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.msg.cluster.SendToClusterMsg;
import org.thingsboard.server.common.msg.cluster.ServerAddress;
import org.thingsboard.server.common.msg.core.ToServerRpcResponseMsg;
import org.thingsboard.server.common.msg.rpc.ToDeviceRpcRequest;
import org.thingsboard.server.dao.audit.AuditLogService;
import org.thingsboard.server.gen.cluster.ClusterAPIProtos;
import org.thingsboard.server.service.cluster.routing.ClusterRoutingService;
import org.thingsboard.server.service.cluster.rpc.ClusterRpcService;
import org.thingsboard.server.service.encoding.DataDecodingEncodingService;
import org.thingsboard.server.service.telemetry.sub.Subscription;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Created by ashvayka on 27.03.18.
 */
@Service
@Slf4j
public class DefaultDeviceRpcService implements DeviceRpcService {

    @Autowired
    private ClusterRoutingService routingService;

    @Autowired
    private ClusterRpcService rpcService;

    @Autowired
    private ActorService actorService;

    private ScheduledExecutorService rpcCallBackExecutor;

    private final ConcurrentMap<UUID, Consumer<FromDeviceRpcResponse>> localRpcRequests = new ConcurrentHashMap<>();

    @PostConstruct
    public void initExecutor() {
        rpcCallBackExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    @PreDestroy
    public void shutdownExecutor() {
        if (rpcCallBackExecutor != null) {
            rpcCallBackExecutor.shutdownNow();
        }
    }

    @Override
    public void processRpcRequestToDevice(ToDeviceRpcRequest request, Consumer<FromDeviceRpcResponse> responseConsumer) {
        log.trace("[{}] Processing local rpc call for device [{}]", request.getTenantId(), request.getDeviceId());
        sendRpcRequest(request);
        UUID requestId = request.getId();
        localRpcRequests.put(requestId, responseConsumer);
        long timeout = Math.max(0, request.getExpirationTime() - System.currentTimeMillis());
        log.error("[{}] processing the request: [{}]", this.hashCode(), requestId);
        rpcCallBackExecutor.schedule(() -> {
            log.error("[{}] timeout the request: [{}]", this.hashCode(), requestId);
            Consumer<FromDeviceRpcResponse> consumer = localRpcRequests.remove(requestId);
            if (consumer != null) {
                consumer.accept(new FromDeviceRpcResponse(requestId, null, null, RpcError.TIMEOUT));
            }
        }, timeout, TimeUnit.MILLISECONDS);
    }

    @Override
    public void processRpcResponseFromDevice(FromDeviceRpcResponse response) {
        log.error("[{}] response to request: [{}]", this.hashCode(), response.getId());
        if (routingService.getCurrentServer().equals(response.getServerAddress())) {
            UUID requestId = response.getId();
            Consumer<FromDeviceRpcResponse> consumer = localRpcRequests.remove(requestId);
            if (consumer != null) {
                consumer.accept(response);
            } else {
                log.trace("[{}] Unknown or stale rpc response received [{}]", requestId, response);
            }
        } else {
            ClusterAPIProtos.FromDeviceRPCResponseProto.Builder builder = ClusterAPIProtos.FromDeviceRPCResponseProto.newBuilder();
            builder.setRequestIdMSB(response.getId().getMostSignificantBits());
            builder.setRequestIdLSB(response.getId().getLeastSignificantBits());
            response.getResponse().ifPresent(builder::setResponse);
            if (response.getError().isPresent()) {
                builder.setError(response.getError().get().ordinal());
            } else {
                builder.setError(-1);
            }
            rpcService.tell(response.getServerAddress(), ClusterAPIProtos.MessageType.CLUSTER_RPC_FROM_DEVICE_RESPONSE_MESSAGE, builder.build().toByteArray());
        }
    }

    @Override
    public void processRemoteResponseFromDevice(ServerAddress serverAddress, byte[] data) {
        ClusterAPIProtos.FromDeviceRPCResponseProto proto;
        try {
            proto = ClusterAPIProtos.FromDeviceRPCResponseProto.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
        RpcError error = proto.getError() > 0 ? RpcError.values()[proto.getError()] : null;
        FromDeviceRpcResponse response = new FromDeviceRpcResponse(new UUID(proto.getRequestIdMSB(), proto.getRequestIdLSB()), routingService.getCurrentServer(),
                proto.getResponse(), error);
        processRpcResponseFromDevice(response);
    }

    @Override
    public void sendRpcReplyToDevice(TenantId tenantId, DeviceId deviceId, int requestId, String body) {
        ToServerRpcResponseActorMsg rpcMsg = new ToServerRpcResponseActorMsg(tenantId, deviceId, new ToServerRpcResponseMsg(requestId, body));
        forward(deviceId, rpcMsg);
    }

    private void sendRpcRequest(ToDeviceRpcRequest msg) {
        ToDeviceRpcRequestActorMsg rpcMsg = new ToDeviceRpcRequestActorMsg(routingService.getCurrentServer(), msg);
        log.trace("[{}] Forwarding msg {} to device actor!", msg.getDeviceId(), msg);
        forward(msg.getDeviceId(), rpcMsg);
    }

    private <T extends ToDeviceActorNotificationMsg> void forward(DeviceId deviceId, T msg) {
        actorService.onMsg(new SendToClusterMsg(deviceId, msg));
    }
}
