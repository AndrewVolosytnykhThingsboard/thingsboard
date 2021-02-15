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
package org.thingsboard.server.service.query;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.context.request.async.DeferredResult;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.query.AlarmData;
import org.thingsboard.server.common.data.query.AlarmDataQuery;
import org.thingsboard.server.common.data.query.EntityCountQuery;
import org.thingsboard.server.common.data.query.EntityData;
import org.thingsboard.server.common.data.query.EntityDataPageLink;
import org.thingsboard.server.common.data.query.EntityDataQuery;
import org.thingsboard.server.common.data.query.EntityDataSortOrder;
import org.thingsboard.server.common.data.query.EntityKey;
import org.thingsboard.server.common.data.query.EntityKeyType;
import org.thingsboard.server.dao.alarm.AlarmService;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.entity.EntityService;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.server.dao.util.mapping.JacksonUtil;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.executors.DbCallbackExecutorService;
import org.thingsboard.server.service.security.AccessValidator;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@Slf4j
@TbCoreComponent
public class DefaultEntityQueryService implements EntityQueryService {

    private final EntityService entityService;

    public DefaultEntityQueryService(EntityService entityService) {
        this.entityService = entityService;
    }

    @Autowired
    private AlarmService alarmService;

    @Value("${server.ws.max_entities_per_alarm_subscription:1000}")
    private int maxEntitiesPerAlarmSubscription;

    @Autowired
    private DbCallbackExecutorService dbCallbackExecutor;

    @Autowired
    private TimeseriesService timeseriesService;

    @Autowired
    private AttributesService attributesService;

    @Override
    public long countEntitiesByQuery(SecurityUser securityUser, EntityCountQuery query) {
        return entityService.countEntitiesByQuery(securityUser.getTenantId(), securityUser.getCustomerId(), securityUser.getUserPermissions(), query);
    }

    @Override
    public PageData<EntityData> findEntityDataByQuery(SecurityUser securityUser, EntityDataQuery query) {
        return entityService.findEntityDataByQuery(securityUser.getTenantId(), securityUser.getCustomerId(), securityUser.getUserPermissions(), query);
    }

    @Override
    public PageData<AlarmData> findAlarmDataByQuery(SecurityUser securityUser, AlarmDataQuery query) {
        EntityDataQuery entityDataQuery = this.buildEntityDataQuery(query);
        PageData<EntityData> entities = entityService.findEntityDataByQuery(securityUser.getTenantId(),
                securityUser.getCustomerId(), securityUser.getUserPermissions(), entityDataQuery);
        if (entities.getTotalElements() > 0) {
            LinkedHashMap<EntityId, EntityData> entitiesMap = new LinkedHashMap<>();
            for (EntityData entityData : entities.getData()) {
                entitiesMap.put(entityData.getEntityId(), entityData);
            }
            PageData<AlarmData> alarms = alarmService.findAlarmDataByQueryForEntities(securityUser.getTenantId(),
                    securityUser.getCustomerId(), securityUser.getUserPermissions(), query, entitiesMap.keySet());
            for (AlarmData alarmData : alarms.getData()) {
                EntityId entityId = alarmData.getEntityId();
                if (entityId != null) {
                    EntityData entityData = entitiesMap.get(entityId);
                    if (entityData != null) {
                        alarmData.getLatest().putAll(entityData.getLatest());
                    }
                }
            }
            return alarms;
        } else {
            return new PageData<>();
        }
    }

    private EntityDataQuery buildEntityDataQuery(AlarmDataQuery query) {
        EntityDataSortOrder sortOrder = query.getPageLink().getSortOrder();
        EntityDataSortOrder entitiesSortOrder;
        if (sortOrder == null || sortOrder.getKey().getType().equals(EntityKeyType.ALARM_FIELD)) {
            entitiesSortOrder = new EntityDataSortOrder(new EntityKey(EntityKeyType.ENTITY_FIELD, ModelConstants.CREATED_TIME_PROPERTY));
        } else {
            entitiesSortOrder = sortOrder;
        }
        EntityDataPageLink edpl = new EntityDataPageLink(maxEntitiesPerAlarmSubscription, 0, null, entitiesSortOrder);
        return new EntityDataQuery(query.getEntityFilter(), edpl, query.getEntityFields(), query.getLatestValues(), query.getKeyFilters());
    }

    @Override
    public DeferredResult<ResponseEntity> getKeysByQuery(SecurityUser securityUser, TenantId tenantId, EntityDataQuery query,
                                                         boolean isTimeseries, boolean isAttributes) {
        final DeferredResult<ResponseEntity> response = new DeferredResult<>();
        if (!isAttributes && !isTimeseries) {
            replyWithEmptyResponse(response);
            return response;
        }

        List<EntityId> ids = this.findEntityDataByQuery(securityUser, query).getData().stream()
                .map(EntityData::getEntityId)
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            replyWithEmptyResponse(response);
            return response;
        }

        Set<EntityType> types = ids.stream().map(EntityId::getEntityType).collect(Collectors.toSet());
        final ListenableFuture<List<String>> timeseriesKeysFuture;
        final ListenableFuture<List<String>> attributesKeysFuture;

        if (isTimeseries) {
            timeseriesKeysFuture = dbCallbackExecutor.submit(() -> timeseriesService.findAllKeysByEntityIds(tenantId, ids));
        } else {
            timeseriesKeysFuture = null;
        }

        if (isAttributes) {
            Map<EntityType, List<EntityId>> typesMap = ids.stream().collect(Collectors.groupingBy(EntityId::getEntityType));
            List<ListenableFuture<List<String>>> futures = new ArrayList<>(typesMap.size());
            typesMap.forEach((type, entityIds) -> futures.add(dbCallbackExecutor.submit(() -> attributesService.findAllKeysByEntityIds(tenantId, type, entityIds))));
            attributesKeysFuture = Futures.transform(Futures.allAsList(futures), lists -> {
                if (CollectionUtils.isEmpty(lists)) {
                    return Collections.emptyList();
                }
                return lists.stream().flatMap(List::stream).distinct().sorted().collect(Collectors.toList());
            }, dbCallbackExecutor);
        } else {
            attributesKeysFuture = null;
        }

        if (isTimeseries && isAttributes) {
            Futures.whenAllComplete(timeseriesKeysFuture, attributesKeysFuture).run(() -> {
                try {
                    replyWithResponse(response, types, timeseriesKeysFuture.get(), attributesKeysFuture.get());
                } catch (Exception e) {
                    log.error("Failed to fetch timeseries and attributes keys!", e);
                    AccessValidator.handleError(e, response, HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }, dbCallbackExecutor);
        } else if (isTimeseries) {
            addCallback(timeseriesKeysFuture, keys -> replyWithResponse(response, types, keys, null),
                    error -> {
                        log.error("Failed to fetch timeseries keys!", error);
                        AccessValidator.handleError(error, response, HttpStatus.INTERNAL_SERVER_ERROR);
                    });
        } else {
            addCallback(attributesKeysFuture, keys -> replyWithResponse(response, types, null, keys),
                    error -> {
                        log.error("Failed to fetch attributes keys!", error);
                        AccessValidator.handleError(error, response, HttpStatus.INTERNAL_SERVER_ERROR);
                    });
        }
        return response;
    }

    private void replyWithResponse(DeferredResult<ResponseEntity> response, Set<EntityType> types, List<String> timeseriesKeys, List<String> attributesKeys) {
        ObjectNode json = JacksonUtil.newObjectNode();
        addItemsToArrayNode(json.putArray("entityTypes"), types);
        addItemsToArrayNode(json.putArray("timeseries"), timeseriesKeys);
        addItemsToArrayNode(json.putArray("attribute"), attributesKeys);
        response.setResult(new ResponseEntity<>(json, HttpStatus.OK));
    }

    private void replyWithEmptyResponse(DeferredResult<ResponseEntity> response) {
        replyWithResponse(response, Collections.emptySet(), Collections.emptyList(), Collections.emptyList());
    }

    private void addItemsToArrayNode(ArrayNode arrayNode, Collection<?> collection) {
        if (!CollectionUtils.isEmpty(collection)) {
            collection.forEach(item -> arrayNode.add(item.toString()));
        }
    }

    private void addCallback(ListenableFuture<List<String>> future, Consumer<List<String>> success, Consumer<Throwable> error) {
        Futures.addCallback(future, new FutureCallback<List<String>>() {
            @Override
            public void onSuccess(@Nullable List<String> keys) {
                success.accept(keys);
            }

            @Override
            public void onFailure(Throwable t) {
                error.accept(t);
            }
        }, dbCallbackExecutor);
    }

}
