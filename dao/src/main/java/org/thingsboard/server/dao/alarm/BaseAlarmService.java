/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2019 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.dao.alarm;


import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.thingsboard.common.util.ThingsBoardThreadFactory;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmId;
import org.thingsboard.server.common.data.alarm.AlarmInfo;
import org.thingsboard.server.common.data.alarm.AlarmQuery;
import org.thingsboard.server.common.data.alarm.AlarmSearchStatus;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.alarm.AlarmStatus;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TimePageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.EntityRelationsQuery;
import org.thingsboard.server.common.data.relation.EntitySearchDirection;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.data.relation.RelationsSearchParameters;
import org.thingsboard.server.dao.entity.AbstractEntityService;
import org.thingsboard.server.dao.entity.EntityService;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.tenant.TenantDao;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.thingsboard.server.dao.service.Validator.validateId;

@Service
@Slf4j
public class BaseAlarmService extends AbstractEntityService implements AlarmService {

    public static final String ALARM_RELATION_PREFIX = "ALARM_";

    @Autowired
    private AlarmDao alarmDao;

    @Autowired
    private TenantDao tenantDao;

    @Autowired
    private EntityService entityService;

    protected ExecutorService readResultsProcessingExecutor;

    @PostConstruct
    public void startExecutor() {
        readResultsProcessingExecutor = Executors.newCachedThreadPool(ThingsBoardThreadFactory.forName("alarm-service"));
    }

    @PreDestroy
    public void stopExecutor() {
        if (readResultsProcessingExecutor != null) {
            readResultsProcessingExecutor.shutdownNow();
        }
    }

    @Override
    public Alarm createOrUpdateAlarm(Alarm alarm) {
        alarmDataValidator.validate(alarm, Alarm::getTenantId);
        try {
            if (alarm.getStartTs() == 0L) {
                alarm.setStartTs(System.currentTimeMillis());
            }
            if (alarm.getEndTs() == 0L) {
                alarm.setEndTs(alarm.getStartTs());
            }
            if (alarm.getId() == null) {
                Alarm existing = alarmDao.findLatestByOriginatorAndType(alarm.getTenantId(), alarm.getOriginator(), alarm.getType()).get();
                if (existing == null || existing.getStatus().isCleared()) {
                    return createAlarm(alarm);
                } else {
                    return updateAlarm(existing, alarm);
                }
            } else {
                return updateAlarm(alarm).get();
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public ListenableFuture<Alarm> findLatestByOriginatorAndType(TenantId tenantId, EntityId originator, String type) {
        return alarmDao.findLatestByOriginatorAndType(tenantId, originator, type);
    }

    @Override
    public Boolean deleteAlarm(TenantId tenantId, AlarmId alarmId) {
        try {
            log.debug("Deleting Alarm Id: {}", alarmId);
            Alarm alarm = alarmDao.findAlarmByIdAsync(tenantId, alarmId.getId()).get();
            if (alarm == null) {
                return false;
            }
            deleteEntityRelations(tenantId, alarm.getId());
            return alarmDao.deleteAlarm(tenantId, alarm);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private Alarm createAlarm(Alarm alarm) throws InterruptedException, ExecutionException {
        log.debug("New Alarm : {}", alarm);
        Alarm saved = alarmDao.save(alarm.getTenantId(), alarm);
        createAlarmRelations(saved);
        return saved;
    }

    private void createAlarmRelations(Alarm alarm) throws InterruptedException, ExecutionException {
        if (alarm.isPropagate()) {
            Set<EntityId> parentEntities = getParentEntities(alarm);
            for (EntityId parentId : parentEntities) {
                createAlarmRelation(alarm.getTenantId(), parentId, alarm.getId(), alarm.getStatus(), true);
            }
        }
        createAlarmRelation(alarm.getTenantId(), alarm.getOriginator(), alarm.getId(), alarm.getStatus(), true);
    }

    private Set<EntityId> getParentEntities(Alarm alarm) throws InterruptedException, ExecutionException {
        EntityRelationsQuery commonQuery = new EntityRelationsQuery();
        commonQuery.setParameters(new RelationsSearchParameters(alarm.getOriginator(), EntitySearchDirection.TO, Integer.MAX_VALUE, RelationTypeGroup.COMMON, false));
        EntityRelationsQuery groupQuery = new EntityRelationsQuery();
        groupQuery.setParameters(new RelationsSearchParameters(alarm.getOriginator(), EntitySearchDirection.TO, Integer.MAX_VALUE, RelationTypeGroup.FROM_ENTITY_GROUP, false));
        List<String> propagateRelationTypes = alarm.getPropagateRelationTypes();
        Stream<EntityRelation> commonRelations = relationService.findByQuery(alarm.getTenantId(), commonQuery).get().stream();
        Stream<EntityRelation> groupRelations = relationService.findByQuery(alarm.getTenantId(), groupQuery).get().stream();
        if (!CollectionUtils.isEmpty(propagateRelationTypes)) {
            commonRelations = commonRelations.filter(entityRelation -> propagateRelationTypes.contains(entityRelation.getType()));
        }
        Set<EntityId> parentEntities = new HashSet<>();
        parentEntities.addAll(commonRelations.map(EntityRelation::getFrom).collect(Collectors.toList()));
        parentEntities.addAll(groupRelations.map(EntityRelation::getFrom).collect(Collectors.toList()));
        return parentEntities;
    }

    private ListenableFuture<Alarm> updateAlarm(Alarm update) {
        alarmDataValidator.validate(update, Alarm::getTenantId);
        return getAndUpdate(update.getTenantId(), update.getId(), new Function<Alarm, Alarm>() {
            @Nullable
            @Override
            public Alarm apply(@Nullable Alarm alarm) {
                if (alarm == null) {
                    return null;
                } else {
                    return updateAlarm(alarm, update);
                }
            }
        });
    }

    private Alarm updateAlarm(Alarm oldAlarm, Alarm newAlarm) {
        AlarmStatus oldStatus = oldAlarm.getStatus();
        AlarmStatus newStatus = newAlarm.getStatus();
        boolean oldPropagate = oldAlarm.isPropagate();
        boolean newPropagate = newAlarm.isPropagate();
        Alarm result = alarmDao.save(newAlarm.getTenantId(), merge(oldAlarm, newAlarm));
        if (!oldPropagate && newPropagate) {
            try {
                createAlarmRelations(result);
            } catch (InterruptedException | ExecutionException e) {
                log.warn("Failed to update alarm relations [{}]", result, e);
                throw new RuntimeException(e);
            }
        } else if (oldStatus != newStatus) {
            updateRelations(oldAlarm, oldStatus, newStatus);
        }
        return result;
    }

    @Override
    public ListenableFuture<Boolean> ackAlarm(TenantId tenantId, AlarmId alarmId, long ackTime) {
        return getAndUpdate(tenantId, alarmId, new Function<Alarm, Boolean>() {
            @Nullable
            @Override
            public Boolean apply(@Nullable Alarm alarm) {
                if (alarm == null || alarm.getStatus().isAck()) {
                    return false;
                } else {
                    AlarmStatus oldStatus = alarm.getStatus();
                    AlarmStatus newStatus = oldStatus.isCleared() ? AlarmStatus.CLEARED_ACK : AlarmStatus.ACTIVE_ACK;
                    alarm.setStatus(newStatus);
                    alarm.setAckTs(ackTime);
                    alarmDao.save(alarm.getTenantId(), alarm);
                    updateRelations(alarm, oldStatus, newStatus);
                    return true;
                }
            }
        });
    }

    @Override
    public ListenableFuture<Boolean> clearAlarm(TenantId tenantId, AlarmId alarmId, JsonNode details, long clearTime) {
        return getAndUpdate(tenantId, alarmId, new Function<Alarm, Boolean>() {
            @Nullable
            @Override
            public Boolean apply(@Nullable Alarm alarm) {
                if (alarm == null || alarm.getStatus().isCleared()) {
                    return false;
                } else {
                    AlarmStatus oldStatus = alarm.getStatus();
                    AlarmStatus newStatus = oldStatus.isAck() ? AlarmStatus.CLEARED_ACK : AlarmStatus.CLEARED_UNACK;
                    alarm.setStatus(newStatus);
                    alarm.setClearTs(clearTime);
                    if (details != null) {
                        alarm.setDetails(details);
                    }
                    alarmDao.save(alarm.getTenantId(), alarm);
                    updateRelations(alarm, oldStatus, newStatus);
                    return true;
                }
            }
        });
    }

    @Override
    public ListenableFuture<Alarm> findAlarmByIdAsync(TenantId tenantId, AlarmId alarmId) {
        log.trace("Executing findAlarmById [{}]", alarmId);
        validateId(alarmId, "Incorrect alarmId " + alarmId);
        return alarmDao.findAlarmByIdAsync(tenantId, alarmId.getId());
    }

    @Override
    public ListenableFuture<AlarmInfo> findAlarmInfoByIdAsync(TenantId tenantId, AlarmId alarmId) {
        log.trace("Executing findAlarmInfoByIdAsync [{}]", alarmId);
        validateId(alarmId, "Incorrect alarmId " + alarmId);
        return Futures.transformAsync(alarmDao.findAlarmByIdAsync(tenantId, alarmId.getId()),
                a -> {
                    AlarmInfo alarmInfo = new AlarmInfo(a);
                    return Futures.transform(
                            entityService.fetchEntityNameAsync(tenantId, alarmInfo.getOriginator()), originatorName -> {
                                alarmInfo.setOriginatorName(originatorName);
                                return alarmInfo;
                            }
                    );
                });
    }

    @Override
    public ListenableFuture<TimePageData<AlarmInfo>> findAlarms(TenantId tenantId, AlarmQuery query) {
        ListenableFuture<List<AlarmInfo>> alarms = alarmDao.findAlarms(tenantId, query);
        if (query.getFetchOriginator() != null && query.getFetchOriginator().booleanValue()) {
            alarms = Futures.transformAsync(alarms, input -> {
                List<ListenableFuture<AlarmInfo>> alarmFutures = new ArrayList<>(input.size());
                for (AlarmInfo alarmInfo : input) {
                    alarmFutures.add(Futures.transform(
                            entityService.fetchEntityNameAsync(tenantId, alarmInfo.getOriginator()), originatorName -> {
                                if (originatorName == null) {
                                    originatorName = "Deleted";
                                }
                                alarmInfo.setOriginatorName(originatorName);
                                return alarmInfo;
                            }
                    ));
                }
                return Futures.successfulAsList(alarmFutures);
            });
        }
        return Futures.transform(alarms, new Function<List<AlarmInfo>, TimePageData<AlarmInfo>>() {
            @Nullable
            @Override
            public TimePageData<AlarmInfo> apply(@Nullable List<AlarmInfo> alarms) {
                return new TimePageData<>(alarms, query.getPageLink());
            }
        });
    }

    @Override
    public List<Long> findAlarmCounts(TenantId tenantId, AlarmQuery query, List<Predicate<AlarmInfo>> filters) {
        List<Long> alarmCounts = new ArrayList<>();
        for (Predicate filter : filters) {
            alarmCounts.add(0l);
        }
        TimePageData<AlarmInfo> alarms;
        do {
            try {
                alarms = findAlarms(tenantId, query).get();
                for (int i = 0; i < filters.size(); i++) {
                    Predicate<AlarmInfo> filter = filters.get(i);
                    long count = alarms.getData().stream().filter(filter).map(AlarmInfo::getId).distinct().count() + alarmCounts.get(i);
                    alarmCounts.set(i, count);
                }
                if (alarms.hasNext()) {
                    query = new AlarmQuery(query.getAffectedEntityId(),
                            alarms.getNextPageLink(),
                            query.getSearchStatus(), query.getStatus(), false);
                }
            } catch (ExecutionException | InterruptedException e) {
                log.warn("Failed to find alarms by query. Query: [{}]", query);
                throw new RuntimeException(e);
            }
        } while (alarms.hasNext());
        return alarmCounts;
    }

    public AlarmSeverity findHighestAlarmSeverity(TenantId tenantId, EntityId entityId, AlarmSearchStatus alarmSearchStatus,
                                                  AlarmStatus alarmStatus) {
        TimePageLink nextPageLink = new TimePageLink(100);
        boolean hasNext = true;
        AlarmSeverity highestSeverity = null;
        AlarmQuery query;
        while (hasNext && AlarmSeverity.CRITICAL != highestSeverity) {
            query = new AlarmQuery(entityId, nextPageLink, alarmSearchStatus, alarmStatus, false);
            List<AlarmInfo> alarms;
            try {
                alarms = alarmDao.findAlarms(tenantId, query).get();
            } catch (ExecutionException | InterruptedException e) {
                log.warn("Failed to find highest alarm severity. EntityId: [{}], AlarmSearchStatus: [{}], AlarmStatus: [{}]",
                        entityId, alarmSearchStatus, alarmStatus);
                throw new RuntimeException(e);
            }
            hasNext = alarms.size() == nextPageLink.getLimit();
            if (hasNext) {
                nextPageLink = new TimePageData<>(alarms, nextPageLink).getNextPageLink();
            }
            AlarmSeverity severity = detectHighestSeverity(alarms);
            if (severity == null) {
                continue;
            }
            if (severity == AlarmSeverity.CRITICAL || highestSeverity == null) {
                highestSeverity = severity;
            } else {
                highestSeverity = highestSeverity.compareTo(severity) < 0 ? highestSeverity : severity;
            }
        }
        return highestSeverity;
    }

    private AlarmSeverity detectHighestSeverity(List<AlarmInfo> alarms) {
        if (!alarms.isEmpty()) {
            List<AlarmInfo> sorted = new ArrayList(alarms);
            sorted.sort(Comparator.comparing(Alarm::getSeverity));
            return sorted.get(0).getSeverity();
        } else {
            return null;
        }
    }

    private void deleteRelation(TenantId tenantId, EntityRelation alarmRelation) {
        log.debug("Deleting Alarm relation: {}", alarmRelation);
        relationService.deleteRelation(tenantId, alarmRelation);
    }

    private void createRelation(TenantId tenantId, EntityRelation alarmRelation) {
        log.debug("Creating Alarm relation: {}", alarmRelation);
        relationService.saveRelation(tenantId, alarmRelation);
    }

    private Alarm merge(Alarm existing, Alarm alarm) {
        if (alarm.getStartTs() > existing.getEndTs()) {
            existing.setEndTs(alarm.getStartTs());
        }
        if (alarm.getEndTs() > existing.getEndTs()) {
            existing.setEndTs(alarm.getEndTs());
        }
        if (alarm.getClearTs() > existing.getClearTs()) {
            existing.setClearTs(alarm.getClearTs());
        }
        if (alarm.getAckTs() > existing.getAckTs()) {
            existing.setAckTs(alarm.getAckTs());
        }
        existing.setStatus(alarm.getStatus());
        existing.setSeverity(alarm.getSeverity());
        existing.setDetails(alarm.getDetails());
        existing.setPropagate(existing.isPropagate() || alarm.isPropagate());
        List<String> existingPropagateRelationTypes = existing.getPropagateRelationTypes();
        List<String> newRelationTypes = alarm.getPropagateRelationTypes();
        if (!CollectionUtils.isEmpty(newRelationTypes)) {
            if (!CollectionUtils.isEmpty(existingPropagateRelationTypes)) {
                existing.setPropagateRelationTypes(Stream.concat(existingPropagateRelationTypes.stream(), newRelationTypes.stream())
                        .distinct()
                        .collect(Collectors.toList()));
            } else {
                existing.setPropagateRelationTypes(newRelationTypes);
            }
        }
        return existing;
    }

    private void updateRelations(Alarm alarm, AlarmStatus oldStatus, AlarmStatus newStatus) {
        try {
            List<EntityRelation> relations = relationService.findByToAsync(alarm.getTenantId(), alarm.getId(), RelationTypeGroup.ALARM).get();

            List<String> propagateRelationTypes = alarm.getPropagateRelationTypes();
            Stream<EntityRelation> relationStream = relations.stream();
            if (!CollectionUtils.isEmpty(propagateRelationTypes)) {
                relationStream = relationStream.filter(entityRelation -> propagateRelationTypes.contains(entityRelation.getType()));
            }
            Set<EntityId> parents = relationStream.map(EntityRelation::getFrom).collect(Collectors.toSet());
            for (EntityId parentId : parents) {
                updateAlarmRelation(alarm.getTenantId(), parentId, alarm.getId(), oldStatus, newStatus);
            }
        } catch (ExecutionException | InterruptedException e) {
            log.warn("[{}] Failed to update relations. Old status: [{}], New status: [{}]", alarm.getId(), oldStatus, newStatus);
            throw new RuntimeException(e);
        }
    }

    private void createAlarmRelation(TenantId tenantId, EntityId entityId, EntityId alarmId, AlarmStatus status, boolean createAnyRelation) {
        if (createAnyRelation) {
            createRelation(tenantId, new EntityRelation(entityId, alarmId, ALARM_RELATION_PREFIX + AlarmSearchStatus.ANY.name(), RelationTypeGroup.ALARM));
        }
        createRelation(tenantId, new EntityRelation(entityId, alarmId, ALARM_RELATION_PREFIX + status.name(), RelationTypeGroup.ALARM));
        createRelation(tenantId, new EntityRelation(entityId, alarmId, ALARM_RELATION_PREFIX + status.getClearSearchStatus().name(), RelationTypeGroup.ALARM));
        createRelation(tenantId, new EntityRelation(entityId, alarmId, ALARM_RELATION_PREFIX + status.getAckSearchStatus().name(), RelationTypeGroup.ALARM));
    }

    private void deleteAlarmRelation(TenantId tenantId, EntityId entityId, EntityId alarmId, AlarmStatus status) {
        deleteRelation(tenantId, new EntityRelation(entityId, alarmId, ALARM_RELATION_PREFIX + status.name(), RelationTypeGroup.ALARM));
        deleteRelation(tenantId, new EntityRelation(entityId, alarmId, ALARM_RELATION_PREFIX + status.getClearSearchStatus().name(), RelationTypeGroup.ALARM));
        deleteRelation(tenantId, new EntityRelation(entityId, alarmId, ALARM_RELATION_PREFIX + status.getAckSearchStatus().name(), RelationTypeGroup.ALARM));
    }

    private void updateAlarmRelation(TenantId tenantId, EntityId entityId, EntityId alarmId, AlarmStatus oldStatus, AlarmStatus newStatus) {
        deleteAlarmRelation(tenantId, entityId, alarmId, oldStatus);
        createAlarmRelation(tenantId, entityId, alarmId, newStatus, false);
    }

    private <T> ListenableFuture<T> getAndUpdate(TenantId tenantId, AlarmId alarmId, Function<Alarm, T> function) {
        validateId(alarmId, "Alarm id should be specified!");
        ListenableFuture<Alarm> entity = alarmDao.findAlarmByIdAsync(tenantId, alarmId.getId());
        return Futures.transform(entity, function, readResultsProcessingExecutor);
    }

    private DataValidator<Alarm> alarmDataValidator =
            new DataValidator<Alarm>() {

                @Override
                protected void validateDataImpl(TenantId tenantId, Alarm alarm) {
                    if (StringUtils.isEmpty(alarm.getType())) {
                        throw new DataValidationException("Alarm type should be specified!");
                    }
                    if (alarm.getOriginator() == null) {
                        throw new DataValidationException("Alarm originator should be specified!");
                    }
                    if (alarm.getSeverity() == null) {
                        throw new DataValidationException("Alarm severity should be specified!");
                    }
                    if (alarm.getStatus() == null) {
                        throw new DataValidationException("Alarm status should be specified!");
                    }
                    if (alarm.getTenantId() == null) {
                        throw new DataValidationException("Alarm should be assigned to tenant!");
                    } else {
                        Tenant tenant = tenantDao.findById(alarm.getTenantId(), alarm.getTenantId().getId());
                        if (tenant == null) {
                            throw new DataValidationException("Alarm is referencing to non-existent tenant!");
                        }
                    }
                }
            };
}
