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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.permission.MergedUserPermissions;
import org.thingsboard.server.common.data.query.AbstractDataQuery;
import org.thingsboard.server.common.data.query.EntityData;
import org.thingsboard.server.common.data.query.EntityDataPageLink;
import org.thingsboard.server.common.data.query.EntityDataQuery;
import org.thingsboard.server.common.data.query.EntityKey;
import org.thingsboard.server.common.data.query.EntityKeyType;
import org.thingsboard.server.common.data.query.TsValue;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.entity.EntityService;
import org.thingsboard.server.service.telemetry.TelemetryWebSocketService;
import org.thingsboard.server.service.telemetry.TelemetryWebSocketSessionRef;
import org.thingsboard.server.service.telemetry.sub.TelemetrySubscriptionUpdate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public abstract class TbAbstractDataSubCtx<T extends AbstractDataQuery<? extends EntityDataPageLink>> extends TbAbstractSubCtx<T> {

    protected final Map<Integer, EntityId> subToEntityIdMap;
    @Getter
    protected PageData<EntityData> data;

    public TbAbstractDataSubCtx(String serviceId, TelemetryWebSocketService wsService,
                                EntityService entityService, TbLocalSubscriptionService localSubscriptionService,
                                AttributesService attributesService, SubscriptionServiceStatistics stats,
                                TelemetryWebSocketSessionRef sessionRef, int cmdId) {
        super(serviceId, wsService, entityService, localSubscriptionService, attributesService, stats, sessionRef, cmdId);
        this.subToEntityIdMap = new ConcurrentHashMap<>();
    }

    public void fetchData() {
        this.data = findEntityData();
    }

    protected PageData<EntityData> findEntityData() {
        PageData<EntityData> result = entityService.findEntityDataByQuery(getTenantId(), getCustomerId(), getMergedUserPermissions(), buildEntityDataQuery());
        if (log.isTraceEnabled()) {
            result.getData().forEach(ed -> {
                log.trace("[{}][{}] EntityData: {}", getSessionId(), getCmdId(), ed);
            });
        }
        return result;
    }

    protected synchronized void update() {
        long start = System.currentTimeMillis();
        PageData<EntityData> newData = findEntityData();
        long end = System.currentTimeMillis();
        stats.getRegularQueryInvocationCnt().incrementAndGet();
        stats.getRegularQueryTimeSpent().addAndGet(end - start);
        Map<EntityId, EntityData> oldDataMap;
        if (data != null && !data.getData().isEmpty()) {
            oldDataMap = data.getData().stream().collect(Collectors.toMap(EntityData::getEntityId, Function.identity(), (a, b) -> a));
        } else {
            oldDataMap = Collections.emptyMap();
        }
        Map<EntityId, EntityData> newDataMap = newData.getData().stream().collect(Collectors.toMap(EntityData::getEntityId, Function.identity(), (a, b) -> a));
        if (oldDataMap.size() == newDataMap.size() && oldDataMap.keySet().equals(newDataMap.keySet())) {
            log.trace("[{}][{}] No updates to entity data found", sessionRef.getSessionId(), cmdId);
        } else {
            this.data = newData;
            doUpdate(newDataMap);
        }
    }

    protected abstract void doUpdate(Map<EntityId, EntityData> newDataMap);

    protected abstract EntityDataQuery buildEntityDataQuery();

    public List<EntityData> getEntitiesData() {
        return data.getData();
    }

    @Override
    public void clearSubscriptions() {
        clearEntitySubscriptions();
        super.clearSubscriptions();
    }

    public void clearEntitySubscriptions() {
        if (subToEntityIdMap != null) {
            for (Integer subId : subToEntityIdMap.keySet()) {
                localSubscriptionService.cancelSubscription(sessionRef.getSessionId(), subId);
            }
            subToEntityIdMap.clear();
        }
    }

    public void createLatestValuesSubscriptions(List<EntityKey> keys) {
        createSubscriptions(keys, true, 0, 0);
    }

    public void createTimeseriesSubscriptions(List<EntityKey> keys, long startTs, long endTs) {
        createSubscriptions(keys, false, startTs, endTs);
    }

    private void createSubscriptions(List<EntityKey> keys, boolean latestValues, long startTs, long endTs) {
        Map<EntityKeyType, List<EntityKey>> keysByType = getEntityKeyByTypeMap(keys);
        for (EntityData entityData : data.getData()) {
            List<TbSubscription> entitySubscriptions = addSubscriptions(entityData, keysByType, latestValues, startTs, endTs);
            entitySubscriptions.forEach(localSubscriptionService::addSubscription);
        }
    }

    protected Map<EntityKeyType, List<EntityKey>> getEntityKeyByTypeMap(List<EntityKey> keys) {
        Map<EntityKeyType, List<EntityKey>> keysByType = new HashMap<>();
        keys.forEach(key -> keysByType.computeIfAbsent(key.getType(), k -> new ArrayList<>()).add(key));
        return keysByType;
    }

    protected List<TbSubscription> addSubscriptions(EntityData entityData, Map<EntityKeyType, List<EntityKey>> keysByType, boolean latestValues, long startTs, long endTs) {
        List<TbSubscription> subscriptionList = new ArrayList<>();
        keysByType.forEach((keysType, keysList) -> {
            int subIdx = sessionRef.getSessionSubIdSeq().incrementAndGet();
            subToEntityIdMap.put(subIdx, entityData.getEntityId());
            switch (keysType) {
                case TIME_SERIES:
                    if (entityData.isReadTs()) {
                        subscriptionList.add(createTsSub(entityData, subIdx, keysList, latestValues, startTs, endTs));
                    }
                    break;
                case CLIENT_ATTRIBUTE:
                    if (entityData.isReadAttrs()) {
                        subscriptionList.add(createAttrSub(entityData, subIdx, keysType, TbAttributeSubscriptionScope.CLIENT_SCOPE, keysList));
                    }
                    break;
                case SHARED_ATTRIBUTE:
                    if (entityData.isReadAttrs()) {
                        subscriptionList.add(createAttrSub(entityData, subIdx, keysType, TbAttributeSubscriptionScope.SHARED_SCOPE, keysList));
                    }
                    break;
                case SERVER_ATTRIBUTE:
                    if (entityData.isReadAttrs()) {
                        subscriptionList.add(createAttrSub(entityData, subIdx, keysType, TbAttributeSubscriptionScope.SERVER_SCOPE, keysList));
                    }
                    break;
                case ATTRIBUTE:
                    if (entityData.isReadAttrs()) {
                        subscriptionList.add(createAttrSub(entityData, subIdx, keysType, TbAttributeSubscriptionScope.ANY_SCOPE, keysList));
                    }
                    break;
            }
        });
        return subscriptionList;
    }

    private TbSubscription createAttrSub(EntityData entityData, int subIdx, EntityKeyType keysType, TbAttributeSubscriptionScope scope, List<EntityKey> subKeys) {
        Map<String, Long> keyStates = buildKeyStats(entityData, keysType, subKeys);
        log.trace("[{}][{}][{}] Creating attributes subscription for [{}] with keys: {}", serviceId, cmdId, subIdx, entityData.getEntityId(), keyStates);
        return TbAttributeSubscription.builder()
                .serviceId(serviceId)
                .sessionId(sessionRef.getSessionId())
                .subscriptionId(subIdx)
                .tenantId(sessionRef.getSecurityCtx().getTenantId())
                .entityId(entityData.getEntityId())
                .updateConsumer((s, subscriptionUpdate) -> sendWsMsg(s, subscriptionUpdate, keysType))
                .allKeys(false)
                .keyStates(keyStates)
                .scope(scope)
                .build();
    }

    private TbSubscription createTsSub(EntityData entityData, int subIdx, List<EntityKey> subKeys, boolean latestValues, long startTs, long endTs) {
        Map<String, Long> keyStates = buildKeyStats(entityData, EntityKeyType.TIME_SERIES, subKeys);
        if (!latestValues && entityData.getTimeseries() != null) {
            entityData.getTimeseries().forEach((k, v) -> {
                long ts = Arrays.stream(v).map(TsValue::getTs).max(Long::compareTo).orElse(0L);
                log.trace("[{}][{}] Updating key: {} with ts: {}", serviceId, cmdId, k, ts);
                keyStates.put(k, ts);
            });
        }
        log.trace("[{}][{}][{}] Creating time-series subscription for [{}] with keys: {}", serviceId, cmdId, subIdx, entityData.getEntityId(), keyStates);
        return TbTimeseriesSubscription.builder()
                .serviceId(serviceId)
                .sessionId(sessionRef.getSessionId())
                .subscriptionId(subIdx)
                .tenantId(sessionRef.getSecurityCtx().getTenantId())
                .entityId(entityData.getEntityId())
                .updateConsumer((sessionId, subscriptionUpdate) -> sendWsMsg(sessionId, subscriptionUpdate, EntityKeyType.TIME_SERIES, latestValues))
                .allKeys(false)
                .keyStates(keyStates)
                .latestValues(latestValues)
                .startTime(startTs)
                .endTime(endTs)
                .build();
    }

    private void sendWsMsg(String sessionId, TelemetrySubscriptionUpdate subscriptionUpdate, EntityKeyType keyType) {
        sendWsMsg(sessionId, subscriptionUpdate, keyType, true);
    }

    private Map<String, Long> buildKeyStats(EntityData entityData, EntityKeyType keysType, List<EntityKey> subKeys) {
        Map<String, Long> keyStates = new HashMap<>();
        subKeys.forEach(key -> keyStates.put(key.getKey(), 0L));
        if (entityData.getLatest() != null) {
            Map<String, TsValue> currentValues = entityData.getLatest().get(keysType);
            if (currentValues != null) {
                currentValues.forEach((k, v) -> {
                    log.trace("[{}][{}] Updating key: {} with ts: {}", serviceId, cmdId, k, v.getTs());
                    keyStates.put(k, v.getTs());
                });
            }
        }
        return keyStates;
    }

    abstract void sendWsMsg(String sessionId, TelemetrySubscriptionUpdate subscriptionUpdate, EntityKeyType keyType, boolean resultToLatestValues);

}
