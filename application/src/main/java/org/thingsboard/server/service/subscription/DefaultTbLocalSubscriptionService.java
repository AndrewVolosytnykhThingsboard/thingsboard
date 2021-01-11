/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.service.subscription;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.ThingsBoardThreadFactory;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.queue.discovery.ClusterTopologyChangeEvent;
import org.thingsboard.server.queue.discovery.PartitionChangeEvent;
import org.thingsboard.server.queue.discovery.PartitionService;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.common.msg.queue.TbCallback;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.queue.TbClusterService;
import org.thingsboard.server.service.telemetry.sub.AlarmSubscriptionUpdate;
import org.thingsboard.server.service.telemetry.sub.TelemetrySubscriptionUpdate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@TbCoreComponent
@Service
public class DefaultTbLocalSubscriptionService implements TbLocalSubscriptionService {

    private final Set<TopicPartitionInfo> currentPartitions = ConcurrentHashMap.newKeySet();
    private final Map<String, Map<Integer, TbSubscription>> subscriptionsBySessionId = new ConcurrentHashMap<>();

    @Autowired
    private PartitionService partitionService;

    @Autowired
    private TbClusterService clusterService;

    @Autowired
    @Lazy
    private SubscriptionManagerService subscriptionManagerService;

    private ExecutorService subscriptionUpdateExecutor;

    @PostConstruct
    public void initExecutor() {
        subscriptionUpdateExecutor = Executors.newWorkStealingPool(20);
    }

    @PreDestroy
    public void shutdownExecutor() {
        if (subscriptionUpdateExecutor != null) {
            subscriptionUpdateExecutor.shutdownNow();
        }
    }

    @Override
    @EventListener(PartitionChangeEvent.class)
    public void onApplicationEvent(PartitionChangeEvent partitionChangeEvent) {
        if (ServiceType.TB_CORE.equals(partitionChangeEvent.getServiceType())) {
            currentPartitions.clear();
            currentPartitions.addAll(partitionChangeEvent.getPartitions());
        }
    }

    @Override
    @EventListener(ClusterTopologyChangeEvent.class)
    public void onApplicationEvent(ClusterTopologyChangeEvent event) {
        if (event.getServiceQueueKeys().stream().anyMatch(key -> ServiceType.TB_CORE.equals(key.getServiceType()))) {
            /*
             * If the cluster topology has changed, we need to push all current subscriptions to SubscriptionManagerService again.
             * Otherwise, the SubscriptionManagerService may "forget" those subscriptions in case of restart.
             * Although this is resource consuming operation, it is cheaper than sending ping/pong commands periodically
             * It is also cheaper then caching the subscriptions by entity id and then lookup of those caches every time we have new telemetry in SubscriptionManagerService.
             * Even if we cache locally the list of active subscriptions by entity id, it is still time consuming operation to get them from cache
             * Since number of subscriptions is usually much less then number of devices that are pushing data.
             */
            subscriptionsBySessionId.values().forEach(map -> map.values()
                    .forEach(sub -> pushSubscriptionToManagerService(sub, true)));
        }
    }

    //TODO 3.1: replace null callbacks with callbacks from websocket service.
    @Override
    public void addSubscription(TbSubscription subscription) {
        pushSubscriptionToManagerService(subscription, true);
        registerSubscription(subscription);
    }

    private void pushSubscriptionToManagerService(TbSubscription subscription, boolean pushToLocalService) {
        TopicPartitionInfo tpi = partitionService.resolve(ServiceType.TB_CORE, subscription.getTenantId(), subscription.getEntityId());
        if (currentPartitions.contains(tpi)) {
            // Subscription is managed on the same server;
            if (pushToLocalService) {
                subscriptionManagerService.addSubscription(subscription, TbCallback.EMPTY);
            }
        } else {
            // Push to the queue;
            TransportProtos.ToCoreMsg toCoreMsg = TbSubscriptionUtils.toNewSubscriptionProto(subscription);
            clusterService.pushMsgToCore(tpi, subscription.getEntityId().getId(), toCoreMsg, null);
        }
    }

    @Override
    public void onSubscriptionUpdate(String sessionId, TelemetrySubscriptionUpdate update, TbCallback callback) {
        TbSubscription subscription = subscriptionsBySessionId
                .getOrDefault(sessionId, Collections.emptyMap()).get(update.getSubscriptionId());
        if (subscription != null) {
            switch (subscription.getType()) {
                case TIMESERIES:
                    TbTimeseriesSubscription tsSub = (TbTimeseriesSubscription) subscription;
                    update.getLatestValues().forEach((key, value) -> tsSub.getKeyStates().put(key, value));
                    break;
                case ATTRIBUTES:
                    TbAttributeSubscription attrSub = (TbAttributeSubscription) subscription;
                    update.getLatestValues().forEach((key, value) -> attrSub.getKeyStates().put(key, value));
                    break;
            }
            subscriptionUpdateExecutor.submit(() -> subscription.getUpdateConsumer().accept(sessionId, update));
        }
        callback.onSuccess();
    }

    @Override
    public void onSubscriptionUpdate(String sessionId, AlarmSubscriptionUpdate update, TbCallback callback) {
        TbSubscription subscription = subscriptionsBySessionId
                .getOrDefault(sessionId, Collections.emptyMap()).get(update.getSubscriptionId());
        if (subscription != null && subscription.getType() == TbSubscriptionType.ALARMS) {
            subscriptionUpdateExecutor.submit(() -> subscription.getUpdateConsumer().accept(sessionId, update));
        }
        callback.onSuccess();
    }

    @Override
    public void cancelSubscription(String sessionId, int subscriptionId) {
        log.debug("[{}][{}] Going to remove subscription.", sessionId, subscriptionId);
        Map<Integer, TbSubscription> sessionSubscriptions = subscriptionsBySessionId.get(sessionId);
        if (sessionSubscriptions != null) {
            TbSubscription subscription = sessionSubscriptions.remove(subscriptionId);
            if (subscription != null) {
                if (sessionSubscriptions.isEmpty()) {
                    subscriptionsBySessionId.remove(sessionId);
                }
                TopicPartitionInfo tpi = partitionService.resolve(ServiceType.TB_CORE, subscription.getTenantId(), subscription.getEntityId());
                if (currentPartitions.contains(tpi)) {
                    // Subscription is managed on the same server;
                    subscriptionManagerService.cancelSubscription(sessionId, subscriptionId, TbCallback.EMPTY);
                } else {
                    // Push to the queue;
                    TransportProtos.ToCoreMsg toCoreMsg = TbSubscriptionUtils.toCloseSubscriptionProto(subscription);
                    clusterService.pushMsgToCore(tpi, subscription.getEntityId().getId(), toCoreMsg, null);
                }
            } else {
                log.debug("[{}][{}] Subscription not found!", sessionId, subscriptionId);
            }
        } else {
            log.debug("[{}] No session subscriptions found!", sessionId);
        }
    }

    @Override
    public void cancelAllSessionSubscriptions(String sessionId) {
        Map<Integer, TbSubscription> subscriptions = subscriptionsBySessionId.get(sessionId);
        if (subscriptions != null) {
            Set<Integer> toRemove = new HashSet<>(subscriptions.keySet());
            toRemove.forEach(id -> cancelSubscription(sessionId, id));
        }
    }

    private void registerSubscription(TbSubscription subscription) {
        Map<Integer, TbSubscription> sessionSubscriptions = subscriptionsBySessionId.computeIfAbsent(subscription.getSessionId(), k -> new ConcurrentHashMap<>());
        sessionSubscriptions.put(subscription.getSubscriptionId(), subscription);
    }

}
