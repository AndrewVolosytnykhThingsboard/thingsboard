/**
 * Copyright © 2016-2021 ThingsBoard, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.service.ruleengine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.queue.ServiceQueue;
import org.thingsboard.server.common.msg.queue.TbCallback;
import org.thingsboard.server.common.msg.queue.TbMsgCallback;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.service.queue.TbClusterService;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
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
public class DefaultRuleEngineCallService implements RuleEngineCallService {

    private final TbClusterService clusterService;

    private ScheduledExecutorService rpcCallBackExecutor;

    private final ConcurrentMap<UUID, Consumer<TbMsg>> requests = new ConcurrentHashMap<>();

    public DefaultRuleEngineCallService(TbClusterService clusterService) {
        this.clusterService = clusterService;
    }

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
    public void processRestAPICallToRuleEngine(TenantId tenantId, UUID requestId, TbMsg request, Consumer<TbMsg> consumer) {
        log.trace("[{}] Processing REST API call to rule engine [{}]", tenantId, request.getOriginator());
        requests.put(requestId, consumer);
        sendRequestToRuleEngine(tenantId, request);
        scheduleTimeout(request, requestId, requests);
    }

    @Override
    public void onQueueMsg(TransportProtos.RestApiCallResponseMsgProto restApiCallResponseMsg, TbCallback callback) {
        UUID requestId = new UUID(restApiCallResponseMsg.getRequestIdMSB(), restApiCallResponseMsg.getRequestIdLSB());
        Consumer<TbMsg> consumer = requests.remove(requestId);
        if (consumer != null) {
            consumer.accept(TbMsg.fromBytes(ServiceQueue.MAIN, restApiCallResponseMsg.getResponse().toByteArray(), TbMsgCallback.EMPTY));
        } else {
            log.trace("[{}] Unknown or stale rest api call response received", requestId);
        }
        callback.onSuccess();
    }

    private void sendRequestToRuleEngine(TenantId tenantId, TbMsg msg) {
        clusterService.pushMsgToRuleEngine(tenantId, msg.getOriginator(), msg, null);
    }

    private void scheduleTimeout(TbMsg request, UUID requestId, ConcurrentMap<UUID, Consumer<TbMsg>> requestsMap) {
        long expirationTime = Long.parseLong(request.getMetaData().getValue("expirationTime"));
        long timeout = Math.max(0, expirationTime - System.currentTimeMillis());
        log.trace("[{}] processing the request: [{}]", this.hashCode(), requestId);
        rpcCallBackExecutor.schedule(() -> {
            log.trace("[{}] timeout the request: [{}]", this.hashCode(), requestId);
            Consumer<TbMsg> consumer = requestsMap.remove(requestId);
            if (consumer != null) {
                consumer.accept(null);
            }
        }, timeout, TimeUnit.MILLISECONDS);
    }
}
