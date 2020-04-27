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
package org.thingsboard.server.queue.discovery;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.msg.queue.ServiceQueueKey;
import org.thingsboard.server.common.msg.queue.ServiceQueue;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.gen.transport.TransportProtos.ServiceInfo;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ConsistentHashPartitionService implements PartitionService {

    @Value("${queue.core.topic}")
    private String coreTopic;
    @Value("${queue.core.partitions:100}")
    private Integer corePartitions;
    @Value("${queue.partitions.hash_function_name:murmur3_128}")
    private String hashFunctionName;
    @Value("${queue.partitions.virtual_nodes_size:16}")
    private Integer virtualNodesSize;

    private final ApplicationEventPublisher applicationEventPublisher;
    private final TbServiceInfoProvider serviceInfoProvider;
    private final TenantRoutingInfoService tenantRoutingInfoService;
    private final ConcurrentMap<ServiceQueue, String> partitionTopics = new ConcurrentHashMap<>();
    private final ConcurrentMap<ServiceQueue, Integer> partitionSizes = new ConcurrentHashMap<>();
    private final ConcurrentMap<TenantId, TenantRoutingInfo> tenantRoutingInfoMap = new ConcurrentHashMap<>();

    private ConcurrentMap<ServiceQueueKey, List<Integer>> myPartitions = new ConcurrentHashMap<>();
    private ConcurrentMap<TopicPartitionInfoKey, TopicPartitionInfo> tpiCache = new ConcurrentHashMap<>();

    private Map<String, TopicPartitionInfo> tbCoreNotificationTopics = new HashMap<>();
    private Map<String, TopicPartitionInfo> tbRuleEngineNotificationTopics = new HashMap<>();
    private List<ServiceInfo> currentOtherServices;

    private HashFunction hashFunction;

    public ConsistentHashPartitionService(TbServiceInfoProvider serviceInfoProvider, TenantRoutingInfoService tenantRoutingInfoService, ApplicationEventPublisher applicationEventPublisher) {
        this.serviceInfoProvider = serviceInfoProvider;
        this.tenantRoutingInfoService = tenantRoutingInfoService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @PostConstruct
    public void init() {
        this.hashFunction = forName(hashFunctionName);
        partitionSizes.put(new ServiceQueue(ServiceType.TB_CORE), corePartitions);
        partitionTopics.put(new ServiceQueue(ServiceType.TB_CORE), coreTopic);
    }

    @Override
    public TopicPartitionInfo resolve(ServiceType serviceType, TenantId tenantId, EntityId entityId) {
        return resolve(new ServiceQueue(serviceType), tenantId, entityId);
    }

    @Override
    public TopicPartitionInfo resolve(ServiceType serviceType, String queueName, TenantId tenantId, EntityId entityId) {
        return resolve(new ServiceQueue(serviceType, queueName), tenantId, entityId);
    }

    private TopicPartitionInfo resolve(ServiceQueue serviceQueue, TenantId tenantId, EntityId entityId) {
        int hash = hashFunction.newHasher()
                .putLong(entityId.getId().getMostSignificantBits())
                .putLong(entityId.getId().getLeastSignificantBits()).hash().asInt();
        Integer partitionSize = partitionSizes.get(serviceQueue);
        int partition;
        if (partitionSize != null) {
            partition = Math.abs(hash % partitionSize);
        } else {
            //TODO: In 2.6/3.1 this should not happen because all Rule Engine Queues will be in the DB and we always know their partition sizes.
            partition = 0;
        }
        boolean isolatedTenant = isIsolated(serviceQueue, tenantId);
        TopicPartitionInfoKey cacheKey = new TopicPartitionInfoKey(serviceQueue, isolatedTenant ? tenantId : null, partition);
        return tpiCache.computeIfAbsent(cacheKey, key -> buildTopicPartitionInfo(serviceQueue, tenantId, partition));
    }

    @Override
    public void recalculatePartitions(ServiceInfo currentService, List<ServiceInfo> otherServices) {
        logServiceInfo(currentService);
        otherServices.forEach(this::logServiceInfo);
        Map<ServiceQueueKey, ConsistentHashCircle<ServiceInfo>> circles = new HashMap<>();
        addNode(circles, currentService);
        for (ServiceInfo other : otherServices) {
            addNode(circles, other);
        }
        ConcurrentMap<ServiceQueueKey, List<Integer>> oldPartitions = myPartitions;
        TenantId myIsolatedOrSystemTenantId = getSystemOrIsolatedTenantId(currentService);
        myPartitions = new ConcurrentHashMap<>();
        partitionSizes.forEach((type, size) -> {
            ServiceQueueKey myServiceQueueKey = new ServiceQueueKey(type, myIsolatedOrSystemTenantId);
            for (int i = 0; i < size; i++) {
                ServiceInfo serviceInfo = resolveByPartitionIdx(circles.get(myServiceQueueKey), i);
                if (currentService.equals(serviceInfo)) {
                    ServiceQueueKey serviceQueueKey = new ServiceQueueKey(type, getSystemOrIsolatedTenantId(serviceInfo));
                    myPartitions.computeIfAbsent(serviceQueueKey, key -> new ArrayList<>()).add(i);
                }
            }
        });
        myPartitions.forEach((serviceQueueKey, partitions) -> {
            if (!partitions.equals(oldPartitions.get(serviceQueueKey))) {
                log.info("[{}] NEW PARTITIONS: {}", serviceQueueKey, partitions);
                Set<TopicPartitionInfo> tpiList = partitions.stream()
                        .map(partition -> buildTopicPartitionInfo(serviceQueueKey, partition))
                        .collect(Collectors.toSet());
                applicationEventPublisher.publishEvent(new PartitionChangeEvent(this, serviceQueueKey, tpiList));
            }
        });
        tpiCache.clear();

        if (currentOtherServices == null) {
            currentOtherServices = new ArrayList<>(otherServices);
        } else {
            Set<ServiceQueueKey> changes = new HashSet<>();
            Map<ServiceQueueKey, List<ServiceInfo>> currentMap = getServiceKeyListMap(currentOtherServices);
            Map<ServiceQueueKey, List<ServiceInfo>> newMap = getServiceKeyListMap(otherServices);
            currentOtherServices = otherServices;
            currentMap.forEach((key, list) -> {
                if (!list.equals(newMap.get(key))) {
                    changes.add(key);
                }
            });
            currentMap.keySet().forEach(newMap::remove);
            changes.addAll(newMap.keySet());
            if (!changes.isEmpty()) {
                applicationEventPublisher.publishEvent(new ClusterTopologyChangeEvent(this, changes));
            }
        }
    }

    @Override
    public Set<String> getAllServiceIds(ServiceType serviceType) {
        Set<String> result = new HashSet<>();
        ServiceInfo current = serviceInfoProvider.getServiceInfo();
        if (current.getServiceTypesList().contains(serviceType.name())) {
            result.add(current.getServiceId());
        }
        for (ServiceInfo serviceInfo : currentOtherServices) {
            if (serviceInfo.getServiceTypesList().contains(serviceType.name())) {
                result.add(serviceInfo.getServiceId());
            }
        }
        return result;
    }

    @Override
    public TopicPartitionInfo getNotificationsTopic(ServiceType serviceType, String serviceId) {
        switch (serviceType) {
            case TB_CORE:
                return tbCoreNotificationTopics.computeIfAbsent(serviceId,
                        id -> buildNotificationsTopicPartitionInfo(serviceType, serviceId));
            case TB_RULE_ENGINE:
                return tbRuleEngineNotificationTopics.computeIfAbsent(serviceId,
                        id -> buildNotificationsTopicPartitionInfo(serviceType, serviceId));
            default:
                return buildNotificationsTopicPartitionInfo(serviceType, serviceId);
        }
    }

    private Map<ServiceQueueKey, List<ServiceInfo>> getServiceKeyListMap(List<ServiceInfo> services) {
        final Map<ServiceQueueKey, List<ServiceInfo>> currentMap = new HashMap<>();
        services.forEach(serviceInfo -> {
            for (String serviceTypeStr : serviceInfo.getServiceTypesList()) {
                ServiceType serviceType = ServiceType.valueOf(serviceTypeStr.toUpperCase());
                if (ServiceType.TB_RULE_ENGINE.equals(serviceType)) {
                    for (TransportProtos.QueueInfo queue : serviceInfo.getRuleEngineQueuesList()) {
                        ServiceQueueKey serviceQueueKey = new ServiceQueueKey(new ServiceQueue(serviceType, queue.getName()), getSystemOrIsolatedTenantId(serviceInfo));
                        currentMap.computeIfAbsent(serviceQueueKey, key -> new ArrayList<>()).add(serviceInfo);
                    }
                } else {
                    ServiceQueueKey serviceQueueKey = new ServiceQueueKey(new ServiceQueue(serviceType), getSystemOrIsolatedTenantId(serviceInfo));
                    currentMap.computeIfAbsent(serviceQueueKey, key -> new ArrayList<>()).add(serviceInfo);
                }
            }
        });
        return currentMap;
    }

    private TopicPartitionInfo buildNotificationsTopicPartitionInfo(ServiceType serviceType, String serviceId) {
        return new TopicPartitionInfo(serviceType.name().toLowerCase() + ".notifications." + serviceId, null, null, false);
    }

    private TopicPartitionInfo buildTopicPartitionInfo(ServiceQueueKey serviceQueueKey, int partition) {
        return buildTopicPartitionInfo(serviceQueueKey.getServiceQueue(), serviceQueueKey.getTenantId(), partition);
    }

    private TopicPartitionInfo buildTopicPartitionInfo(ServiceQueue serviceQueue, TenantId tenantId, int partition) {
        TopicPartitionInfo.TopicPartitionInfoBuilder tpi = TopicPartitionInfo.builder();
        tpi.topic(partitionTopics.get(serviceQueue));
        tpi.partition(partition);
        ServiceQueueKey myPartitionsSearchKey;
        if (isIsolated(serviceQueue, tenantId)) {
            tpi.tenantId(tenantId);
            myPartitionsSearchKey = new ServiceQueueKey(serviceQueue, tenantId);
        } else {
            myPartitionsSearchKey = new ServiceQueueKey(serviceQueue, new TenantId(TenantId.NULL_UUID));
        }
        List<Integer> partitions = myPartitions.get(myPartitionsSearchKey);
        if (partitions != null) {
            tpi.myPartition(partitions.contains(partition));
        } else {
            tpi.myPartition(false);
        }
        return tpi.build();
    }

    private boolean isIsolated(ServiceQueue serviceQueue, TenantId tenantId) {
        if (TenantId.SYS_TENANT_ID.equals(tenantId)) {
            return false;
        }
        TenantRoutingInfo routingInfo = tenantRoutingInfoMap.get(tenantId);
        if (routingInfo == null) {
            synchronized (tenantRoutingInfoMap) {
                routingInfo = tenantRoutingInfoMap.get(tenantId);
                if (routingInfo == null) {
                    routingInfo = tenantRoutingInfoService.getRoutingInfo(tenantId);
                    tenantRoutingInfoMap.put(tenantId, routingInfo);
                }
            }
        }
        if (routingInfo == null) {
            throw new RuntimeException("Tenant not found!");
        }
        switch (serviceQueue.getType()) {
            case TB_CORE:
                return routingInfo.isIsolatedTbCore();
            case TB_RULE_ENGINE:
                return routingInfo.isIsolatedTbRuleEngine();
            default:
                return false;
        }
    }

    private void logServiceInfo(TransportProtos.ServiceInfo server) {
        TenantId tenantId = getSystemOrIsolatedTenantId(server);
        if (tenantId.isNullUid()) {
            log.info("[{}] Found common server: [{}]", server.getServiceId(), server.getServiceTypesList());
        } else {
            log.info("[{}][{}] Found specific server: [{}]", server.getServiceId(), tenantId, server.getServiceTypesList());
        }
    }

    private TenantId getSystemOrIsolatedTenantId(TransportProtos.ServiceInfo serviceInfo) {
        return new TenantId(new UUID(serviceInfo.getTenantIdMSB(), serviceInfo.getTenantIdLSB()));
    }

    private void addNode(Map<ServiceQueueKey, ConsistentHashCircle<ServiceInfo>> circles, ServiceInfo instance) {
        TenantId tenantId = getSystemOrIsolatedTenantId(instance);
        for (String serviceTypeStr : instance.getServiceTypesList()) {
            ServiceType serviceType = ServiceType.valueOf(serviceTypeStr.toUpperCase());
            if (ServiceType.TB_RULE_ENGINE.equals(serviceType)) {
                for (TransportProtos.QueueInfo queue : instance.getRuleEngineQueuesList()) {
                    ServiceQueueKey serviceQueueKey = new ServiceQueueKey(new ServiceQueue(serviceType, queue.getName()), tenantId);
                    partitionSizes.put(new ServiceQueue(ServiceType.TB_RULE_ENGINE, queue.getName()), queue.getPartitions());
                    partitionTopics.put(new ServiceQueue(ServiceType.TB_RULE_ENGINE, queue.getName()), queue.getTopic());
                    for (int i = 0; i < virtualNodesSize; i++) {
                        circles.computeIfAbsent(serviceQueueKey, key -> new ConsistentHashCircle<>()).put(hash(instance, i).asLong(), instance);
                    }
                }
            } else {
                ServiceQueueKey serviceQueueKey = new ServiceQueueKey(new ServiceQueue(serviceType), tenantId);
                for (int i = 0; i < virtualNodesSize; i++) {
                    circles.computeIfAbsent(serviceQueueKey, key -> new ConsistentHashCircle<>()).put(hash(instance, i).asLong(), instance);
                }
            }
        }
    }

    private ServiceInfo resolveByPartitionIdx(ConsistentHashCircle<ServiceInfo> circle, Integer partitionIdx) {
        if (circle == null || circle.isEmpty()) {
            return null;
        }
        Long hash = hashFunction.newHasher().putInt(partitionIdx).hash().asLong();
        if (!circle.containsKey(hash)) {
            ConcurrentNavigableMap<Long, ServiceInfo> tailMap = circle.tailMap(hash);
            hash = tailMap.isEmpty() ?
                    circle.firstKey() : tailMap.firstKey();
        }
        return circle.get(hash);
    }

    private HashCode hash(ServiceInfo instance, int i) {
        return hashFunction.newHasher().putString(instance.getServiceId(), StandardCharsets.UTF_8).putInt(i).hash();
    }

    public static HashFunction forName(String name) {
        switch (name) {
            case "murmur3_32":
                return Hashing.murmur3_32();
            case "murmur3_128":
                return Hashing.murmur3_128();
            case "crc32":
                return Hashing.crc32();
            case "md5":
                return Hashing.md5();
            default:
                throw new IllegalArgumentException("Can't find hash function with name " + name);
        }
    }
}
