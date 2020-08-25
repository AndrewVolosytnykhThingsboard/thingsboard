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
package org.thingsboard.server.dao.edge;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Edge;
import org.thingsboard.server.common.data.EntitySubtype;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.ShortEntityView;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.edge.EdgeSearchQuery;
import org.thingsboard.server.common.data.group.EntityField;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.IdBased;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.SchedulerEventId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.page.TimePageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.EntitySearchDirection;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.dao.customer.CustomerDao;
import org.thingsboard.server.dao.entity.AbstractEntityService;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.group.EntityGroupService;
import org.thingsboard.server.dao.relation.RelationService;
import org.thingsboard.server.dao.rule.RuleChainService;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.service.PaginatedRemover;
import org.thingsboard.server.dao.service.Validator;
import org.thingsboard.server.dao.tenant.TenantDao;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.thingsboard.server.common.data.CacheConstants.EDGE_CACHE;
import static org.thingsboard.server.dao.DaoUtil.toUUIDs;
import static org.thingsboard.server.dao.model.ModelConstants.NULL_UUID;
import static org.thingsboard.server.dao.service.Validator.validateEntityId;
import static org.thingsboard.server.dao.service.Validator.validateId;
import static org.thingsboard.server.dao.service.Validator.validateIds;
import static org.thingsboard.server.dao.service.Validator.validatePageLink;
import static org.thingsboard.server.dao.service.Validator.validateString;

@Service
@Slf4j
public class EdgeServiceImpl extends AbstractEntityService implements EdgeService {

    public static final String INCORRECT_TENANT_ID = "Incorrect tenantId ";
    public static final String INCORRECT_PAGE_LINK = "Incorrect page link ";
    public static final String INCORRECT_CUSTOMER_ID = "Incorrect customerId ";
    public static final String INCORRECT_EDGE_ID = "Incorrect edgeId ";

    @Autowired
    private EdgeDao edgeDao;

    @Autowired
    private TenantDao tenantDao;

    @Autowired
    private CustomerDao customerDao;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RuleChainService ruleChainService;

    @Autowired
    private RelationService relationService;

    @Autowired
    private EntityGroupService entityGroupService;

    @Override
    public Edge findEdgeById(TenantId tenantId, EdgeId edgeId) {
        log.trace("Executing findEdgeById [{}]", edgeId);
        validateId(edgeId, INCORRECT_EDGE_ID + edgeId);
        return edgeDao.findById(tenantId, edgeId.getId());
    }

    @Override
    public ListenableFuture<Edge> findEdgeByIdAsync(TenantId tenantId, EdgeId edgeId) {
        log.trace("Executing findEdgeById [{}]", edgeId);
        validateId(edgeId, INCORRECT_EDGE_ID + edgeId);
        return edgeDao.findByIdAsync(tenantId, edgeId.getId());
    }

    @Cacheable(cacheNames = EDGE_CACHE, key = "{#tenantId, #name}")
    @Override
    public Edge findEdgeByTenantIdAndName(TenantId tenantId, String name) {
        log.trace("Executing findEdgeByTenantIdAndName [{}][{}]", tenantId, name);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        Optional<Edge> edgeOpt = edgeDao.findEdgeByTenantIdAndName(tenantId.getId(), name);
        return edgeOpt.orElse(null);
    }

    @Override
    public Optional<Edge> findEdgeByRoutingKey(TenantId tenantId, String routingKey) {
        log.trace("Executing findEdgeByRoutingKey [{}]", routingKey);
        Validator.validateString(routingKey, "Incorrect edge routingKey for search request.");
        return edgeDao.findByRoutingKey(tenantId.getId(), routingKey);
    }

    @CacheEvict(cacheNames = EDGE_CACHE, key = "{#edge.tenantId, #edge.name}")
    @Override
    public Edge saveEdge(Edge edge) {
        log.trace("Executing saveEdge [{}]", edge);
        edgeValidator.validate(edge, Edge::getTenantId);
        Edge savedEdge = edgeDao.save(edge.getTenantId(), edge);
        if (edge.getId() == null) {
            entityGroupService.addEntityToEntityGroupAll(savedEdge.getTenantId(), savedEdge.getOwnerId(), savedEdge.getId());
        }
        return savedEdge;
    }

    @Override
    public Edge assignEdgeToCustomer(TenantId tenantId, EdgeId edgeId, CustomerId customerId) {
        Edge edge = findEdgeById(tenantId, edgeId);
        edge.setCustomerId(customerId);
        return saveEdge(edge);
    }

    @Override
    public Edge unassignEdgeFromCustomer(TenantId tenantId, EdgeId edgeId) {
        Edge edge = findEdgeById(tenantId, edgeId);
        edge.setCustomerId(null);
        return saveEdge(edge);
    }

    @Override
    public void deleteEdge(TenantId tenantId, EdgeId edgeId) {
        log.trace("Executing deleteEdge [{}]", edgeId);
        validateId(edgeId, INCORRECT_EDGE_ID + edgeId);

        Edge edge = edgeDao.findById(tenantId, edgeId.getId());

        // TODO: voba - properly handle edge remove
        // dashboardService.unassignEdgeDashboards(tenantId, edgeId);
        // TODO: validate that rule chains are removed by deleteEntityRelations(tenantId, edgeId); call
        ruleChainService.unassignEdgeRuleChains(tenantId, edgeId);

        List<Object> list = new ArrayList<>();
        list.add(edge.getTenantId());
        list.add(edge.getName());
        Cache cache = cacheManager.getCache(EDGE_CACHE);
        cache.evict(list);

        deleteEntityRelations(tenantId, edgeId);

        edgeDao.removeById(tenantId, edgeId.getId());
    }

    @Override
    public TextPageData<Edge> findEdgesByTenantId(TenantId tenantId, TextPageLink pageLink) {
        log.trace("Executing findEdgesByTenantId, tenantId [{}], pageLink [{}]", tenantId, pageLink);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validatePageLink(pageLink, INCORRECT_PAGE_LINK + pageLink);
        List<Edge> edges = edgeDao.findEdgesByTenantId(tenantId.getId(), pageLink);
        return new TextPageData<>(edges, pageLink);
    }

    @Override
    public TextPageData<Edge> findEdgesByTenantIdAndType(TenantId tenantId, String type, TextPageLink pageLink) {
        log.trace("Executing findEdgesByTenantIdAndType, tenantId [{}], type [{}], pageLink [{}]", tenantId, type, pageLink);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateString(type, "Incorrect type " + type);
        validatePageLink(pageLink, INCORRECT_PAGE_LINK + pageLink);
        List<Edge> edges = edgeDao.findEdgesByTenantIdAndType(tenantId.getId(), type, pageLink);
        return new TextPageData<>(edges, pageLink);
    }

    @Override
    public ListenableFuture<List<Edge>> findEdgesByTenantIdAndIdsAsync(TenantId tenantId, List<EdgeId> edgeIds) {
        log.trace("Executing findEdgesByTenantIdAndIdsAsync, tenantId [{}], edgeIds [{}]", tenantId, edgeIds);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateIds(edgeIds, "Incorrect edgeIds " + edgeIds);
        return edgeDao.findEdgesByTenantIdAndIdsAsync(tenantId.getId(), toUUIDs(edgeIds));
    }

    @Override
    public void deleteEdgesByTenantId(TenantId tenantId) {
        log.trace("Executing deleteEdgesByTenantId, tenantId [{}]", tenantId);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        tenantEdgesRemover.removeEntities(tenantId, tenantId);
    }

    @Override
    public TextPageData<Edge> findEdgesByTenantIdAndCustomerId(TenantId tenantId, CustomerId customerId, TextPageLink pageLink) {
        log.trace("Executing findEdgesByTenantIdAndCustomerId, tenantId [{}], customerId [{}], pageLink [{}]", tenantId, customerId, pageLink);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateId(customerId, INCORRECT_CUSTOMER_ID + customerId);
        validatePageLink(pageLink, INCORRECT_PAGE_LINK + pageLink);
        List<Edge> edges = edgeDao.findEdgesByTenantIdAndCustomerId(tenantId.getId(), customerId.getId(), pageLink);
        return new TextPageData<>(edges, pageLink);
    }

    @Override
    public TextPageData<Edge> findEdgesByTenantIdAndCustomerIdAndType(TenantId tenantId, CustomerId customerId, String type, TextPageLink pageLink) {
        log.trace("Executing findEdgesByTenantIdAndCustomerIdAndType, tenantId [{}], customerId [{}], type [{}], pageLink [{}]", tenantId, customerId, type, pageLink);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateId(customerId, INCORRECT_CUSTOMER_ID + customerId);
        validateString(type, "Incorrect type " + type);
        validatePageLink(pageLink, INCORRECT_PAGE_LINK + pageLink);
        List<Edge> edges = edgeDao.findEdgesByTenantIdAndCustomerIdAndType(tenantId.getId(), customerId.getId(), type, pageLink);
        return new TextPageData<>(edges, pageLink);
    }

    @Override
    public ListenableFuture<List<Edge>> findEdgesByTenantIdCustomerIdAndIdsAsync(TenantId tenantId, CustomerId customerId, List<EdgeId> edgeIds) {
        log.trace("Executing findEdgesByTenantIdCustomerIdAndIdsAsync, tenantId [{}], customerId [{}], edgeIds [{}]", tenantId, customerId, edgeIds);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateId(customerId, INCORRECT_CUSTOMER_ID + customerId);
        validateIds(edgeIds, "Incorrect edgeIds " + edgeIds);
        return edgeDao.findEdgesByTenantIdCustomerIdAndIdsAsync(tenantId.getId(),
                customerId.getId(), toUUIDs(edgeIds));
    }

    @Override
    public void unassignCustomerEdges(TenantId tenantId, CustomerId customerId) {
        log.trace("Executing unassignCustomerEdges, tenantId [{}], customerId [{}]", tenantId, customerId);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateId(customerId, INCORRECT_CUSTOMER_ID + customerId);
        customerEdgeUnassigner.removeEntities(tenantId, customerId);
    }

    @Override
    public ListenableFuture<List<Edge>> findEdgesByQuery(TenantId tenantId, EdgeSearchQuery query) {
        ListenableFuture<List<EntityRelation>> relations = relationService.findByQuery(tenantId, query.toEntitySearchQuery());
        ListenableFuture<List<Edge>> edges = Futures.transformAsync(relations, r -> {
            EntitySearchDirection direction = query.toEntitySearchQuery().getParameters().getDirection();
            List<ListenableFuture<Edge>> futures = new ArrayList<>();
            for (EntityRelation relation : r) {
                EntityId entityId = direction == EntitySearchDirection.FROM ? relation.getTo() : relation.getFrom();
                if (entityId.getEntityType() == EntityType.EDGE) {
                    futures.add(findEdgeByIdAsync(tenantId, new EdgeId(entityId.getId())));
                }
            }
            return Futures.successfulAsList(futures);
        }, MoreExecutors.directExecutor());

        edges = Futures.transform(edges, new Function<List<Edge>, List<Edge>>() {
            @Nullable
            @Override
            public List<Edge> apply(@Nullable List<Edge> edgeList) {
                return edgeList == null ? Collections.emptyList() : edgeList.stream().filter(edge -> query.getEdgeTypes().contains(edge.getType())).collect(Collectors.toList());
            }
        }, MoreExecutors.directExecutor());

        return edges;
    }

    @Override
    public ListenableFuture<List<EntitySubtype>> findEdgeTypesByTenantId(TenantId tenantId) {
        log.trace("Executing findEdgeTypesByTenantId, tenantId [{}]", tenantId);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        ListenableFuture<List<EntitySubtype>> tenantEdgeTypes = edgeDao.findTenantEdgeTypesAsync(tenantId.getId());
        return Futures.transform(tenantEdgeTypes,
                edgeTypes -> {
                    edgeTypes.sort(Comparator.comparing(EntitySubtype::getType));
                    return edgeTypes;
                }, MoreExecutors.directExecutor());
    }

    @Override
    public ShortEntityView findGroupEdge(TenantId tenantId, EntityGroupId entityGroupId, EntityId entityId) {
        log.trace("Executing findGroupEdge, entityGroupId [{}], entityId [{}]", entityGroupId, entityId);
        validateId(entityGroupId, "Incorrect entityGroupId " + entityGroupId);
        validateEntityId(entityId, "Incorrect entityId " + entityId);
        return entityGroupService.findGroupEntity(tenantId, entityGroupId, entityId,
                (edgeEntityId) -> new EdgeId(edgeEntityId.getId()),
                (edgeId) -> findEdgeById(tenantId, edgeId),
                new EdgeViewFunction());
    }

    @Override
    public ListenableFuture<TimePageData<ShortEntityView>> findEdgesByEntityGroupId(TenantId tenantId, EntityGroupId entityGroupId, TimePageLink pageLink) {
        log.trace("Executing findEdgesByEntityGroupId, entityGroupId [{}], pageLink [{}]", entityGroupId, pageLink);
        validateId(entityGroupId, "Incorrect entityGroupId " + entityGroupId);
        validatePageLink(pageLink, "Incorrect page link " + pageLink);
        return entityGroupService.findEntities(tenantId, entityGroupId, pageLink,
                (entityId) -> new EdgeId(entityId.getId()),
                (entityIds) -> findEdgesByTenantIdAndIdsAsync(tenantId, entityIds),
                new EdgeViewFunction());
    }

    class EdgeViewFunction implements BiFunction<Edge, List<EntityField>, ShortEntityView> {

        @Override
        public ShortEntityView apply(Edge edge, List<EntityField> entityFields) {
            ShortEntityView shortEntityView = new ShortEntityView(edge.getId());
            shortEntityView.put(EntityField.NAME.name().toLowerCase(), edge.getName());
            for (EntityField field : entityFields) {
                String key = field.name().toLowerCase();
                switch (field) {
                    case TYPE:
                        shortEntityView.put(key, edge.getType());
                        break;
                }
            }
            return shortEntityView;
        }
    }

    @Override
    public void assignDefaultRuleChainsToEdge(TenantId tenantId, EdgeId edgeId) {
        log.trace("Executing assignDefaultRuleChainsToEdge, tenantId [{}], edgeId [{}]", tenantId, edgeId);
        ListenableFuture<List<RuleChain>> future = ruleChainService.findDefaultEdgeRuleChainsByTenantId(tenantId);
        Futures.transform(future, ruleChains -> {
            if (ruleChains != null && !ruleChains.isEmpty()) {
                for (RuleChain ruleChain : ruleChains) {
                    ruleChainService.assignRuleChainToEdge(tenantId, ruleChain.getId(), edgeId);
                }
            }
            return null;
        }, MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<List<Edge>> findEdgesByTenantIdAndRuleChainId(TenantId tenantId, RuleChainId ruleChainId) {
        log.trace("Executing findEdgesByTenantIdAndRuleChainId, tenantId [{}], ruleChainId [{}]", tenantId, ruleChainId);
        Validator.validateId(tenantId, "Incorrect tenantId " + tenantId);
        Validator.validateId(ruleChainId, "Incorrect ruleChainId " + ruleChainId);
        return edgeDao.findEdgesByTenantIdAndRuleChainId(tenantId.getId(), ruleChainId.getId());
    }

    @Override
    public ListenableFuture<List<Edge>> findEdgesByTenantIdAndSchedulerEventId(TenantId tenantId, SchedulerEventId schedulerEventId) {
        log.trace("Executing findEdgesByTenantIdAndSchedulerEventId, tenantId [{}], schedulerEventId [{}]", tenantId, schedulerEventId);
        Validator.validateId(tenantId, "Incorrect tenantId " + tenantId);
        Validator.validateId(schedulerEventId, "Incorrect schedulerEventId " + schedulerEventId);
        return edgeDao.findEdgesByTenantIdAndSchedulerEventId(tenantId.getId(), schedulerEventId.getId());
    }

    @Override
    public ListenableFuture<List<Edge>> findEdgesByTenantIdAndEntityGroupId(TenantId tenantId, EntityGroupId entityGroupId, EntityType groupType) {
        log.trace("Executing findEdgesByTenantIdAndEntityGroupId, tenantId [{}], entityGroupId [{}]", tenantId, entityGroupId);
        Validator.validateId(tenantId, "Incorrect tenantId " + tenantId);
        Validator.validateId(entityGroupId, "Incorrect schedulerEventId " + entityGroupId);
        return edgeDao.findEdgesByTenantIdAndEntityGroupId(tenantId.getId(), entityGroupId.getId(), groupType);
    }

    @Override
    public ListenableFuture<List<Edge>> findEdgesByTenantIdAndDashboardId(TenantId tenantId, DashboardId dashboardId) {
        log.trace("Executing findEdgesByTenantIdAndDashboardId, tenantId [{}], dashboardId [{}]", tenantId, dashboardId);
        Validator.validateId(tenantId, "Incorrect tenantId " + tenantId);
        Validator.validateId(dashboardId, "Incorrect dashboardId " + dashboardId);
        return edgeDao.findEdgesByTenantIdAndDashboardId(tenantId.getId(), dashboardId.getId());
    }

    private DataValidator<Edge> edgeValidator =
            new DataValidator<Edge>() {

                @Override
                protected void validateCreate(TenantId tenantId, Edge edge) {
                    if (!sqlDatabaseUsed) {
                        edgeDao.findEdgeByTenantIdAndName(edge.getTenantId().getId(), edge.getName()).ifPresent(
                                d -> {
                                    throw new DataValidationException("Edge with such name already exists!");
                                }
                        );
                        edgeDao.findByRoutingKey(edge.getTenantId().getId(), edge.getRoutingKey()).ifPresent(
                                d -> {
                                    throw new DataValidationException("Edge with such routing_key already exists");
                                }
                        );
                    }
                }

                @Override
                protected void validateUpdate(TenantId tenantId, Edge edge) {
                    if (!sqlDatabaseUsed) {
                        edgeDao.findEdgeByTenantIdAndName(edge.getTenantId().getId(), edge.getName()).ifPresent(
                                e -> {
                                    if (!e.getUuidId().equals(edge.getUuidId())) {
                                        throw new DataValidationException("Edge with such name already exists!");
                                    }
                                }
                        );
                        edgeDao.findByRoutingKey(edge.getTenantId().getId(), edge.getRoutingKey()).ifPresent(
                                e -> {
                                    if (!e.getUuidId().equals(edge.getUuidId())) {
                                        throw new DataValidationException("Edge with such routing_key already exists!");
                                    }
                                }
                        );
                    }
                }

                @Override
                protected void validateDataImpl(TenantId tenantId, Edge edge) {
                    if (StringUtils.isEmpty(edge.getType())) {
                        throw new DataValidationException("Edge type should be specified!");
                    }
                    if (StringUtils.isEmpty(edge.getName())) {
                        throw new DataValidationException("Edge name should be specified!");
                    }
                    if (StringUtils.isEmpty(edge.getSecret())) {
                        throw new DataValidationException("Edge secret should be specified!");
                    }
                    if (StringUtils.isEmpty(edge.getRoutingKey())) {
                        throw new DataValidationException("Edge routing key should be specified!");
                    }
                    if (edge.getTenantId() == null) {
                        throw new DataValidationException("Edge should be assigned to tenant!");
                    } else {
                        Tenant tenant = tenantDao.findById(edge.getTenantId(), edge.getTenantId().getId());
                        if (tenant == null) {
                            throw new DataValidationException("Edge is referencing to non-existent tenant!");
                        }
                    }
                    if (edge.getCustomerId() == null) {
                        edge.setCustomerId(new CustomerId(NULL_UUID));
                    } else if (!edge.getCustomerId().getId().equals(NULL_UUID)) {
                        Customer customer = customerDao.findById(edge.getTenantId(), edge.getCustomerId().getId());
                        if (customer == null) {
                            throw new DataValidationException("Can't assign edge to non-existent customer!");
                        }
                        if (!customer.getTenantId().getId().equals(edge.getTenantId().getId())) {
                            throw new DataValidationException("Can't assign edge to customer from different tenant!");
                        }
                    }
                }
            };

    private PaginatedRemover<TenantId, Edge> tenantEdgesRemover =
            new PaginatedRemover<TenantId, Edge>() {

                @Override
                protected List<Edge> findEntities(TenantId tenantId, TenantId id, TextPageLink pageLink) {
                    return edgeDao.findEdgesByTenantId(id.getId(), pageLink);
                }

                @Override
                protected void removeEntity(TenantId tenantId, Edge entity) {
                    deleteEdge(tenantId, new EdgeId(entity.getUuidId()));
                }
            };

    private PaginatedRemover<CustomerId, Edge> customerEdgeUnassigner = new PaginatedRemover<CustomerId, Edge>() {

        @Override
        protected List<Edge> findEntities(TenantId tenantId, CustomerId id, TextPageLink pageLink) {
            return edgeDao.findEdgesByTenantIdAndCustomerId(tenantId.getId(), id.getId(), pageLink);
        }

        @Override
        protected void removeEntity(TenantId tenantId, Edge entity) {
            unassignEdgeFromCustomer(tenantId, new EdgeId(entity.getUuidId()));
        }
    };

    @Override
    public ListenableFuture<List<EdgeId>> findRelatedEdgeIdsByEntityId(TenantId tenantId, EntityId entityId, String groupTypeStr) {
        switch (entityId.getEntityType()) {
            case USER:
            case DEVICE:
            case ASSET:
            case ENTITY_VIEW:
            case DASHBOARD:
                ListenableFuture<List<EntityGroupId>> entityGroupsForEntity = entityGroupService.findEntityGroupsForEntity(tenantId, entityId);
                ListenableFuture<List<List<Edge>>> mergedFuture = Futures.transformAsync(entityGroupsForEntity, entityGroupIds -> {
                    List<ListenableFuture<List<Edge>>> futures = new ArrayList<>();
                    if (entityGroupIds != null && !entityGroupIds.isEmpty()) {
                        for (EntityGroupId entityGroupId : entityGroupIds) {
                            futures.add(findEdgesByTenantIdAndEntityGroupId(tenantId, entityGroupId, entityId.getEntityType()));
                        }
                    }
                    return Futures.successfulAsList(futures);
                }, MoreExecutors.directExecutor());
                return Futures.transform(mergedFuture, listOfEdges -> {
                    Set<EdgeId> result = new HashSet<>();
                    if (listOfEdges != null && !listOfEdges.isEmpty()) {
                        for (List<Edge> edges : listOfEdges) {
                            if (edges != null && !edges.isEmpty()) {
                                for (Edge edge : edges) {
                                    result.add(edge.getId());
                                }
                            }
                        }
                    }
                    return new ArrayList<>(result);
                }, MoreExecutors.directExecutor());
            case RULE_CHAIN:
                return convertToEdgeIds(findEdgesByTenantIdAndRuleChainId(tenantId, new RuleChainId(entityId.getId())));
            case SCHEDULER_EVENT:
                return convertToEdgeIds(findEdgesByTenantIdAndSchedulerEventId(tenantId, new SchedulerEventId(entityId.getId())));
            case ENTITY_GROUP:
                EntityGroupId entityGroupId = new EntityGroupId(entityId.getId());
                EntityType groupType;
                if (groupTypeStr != null) {
                    groupType = EntityType.valueOf(groupTypeStr);
                } else {
                    groupType = entityGroupService.findEntityGroupById(tenantId, entityGroupId).getType();
                }
                return convertToEdgeIds(findEdgesByTenantIdAndEntityGroupId(tenantId, entityGroupId, groupType));
            default:
                return Futures.immediateFuture(Collections.emptyList());
        }
    }

    private ListenableFuture<List<EdgeId>> convertToEdgeIds(ListenableFuture<List<Edge>> future) {
        return Futures.transform(future, edges -> {
            if (edges != null && !edges.isEmpty()) {
                return edges.stream().map(IdBased::getId).collect(Collectors.toList());
            } else {
                return Collections.emptyList();
            }
        }, MoreExecutors.directExecutor());
    }

}
