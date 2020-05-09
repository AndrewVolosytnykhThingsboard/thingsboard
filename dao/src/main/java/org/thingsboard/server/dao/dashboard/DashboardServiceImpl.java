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
package org.thingsboard.server.dao.dashboard;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DashboardInfo;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.ShortEntityView;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.Edge;
import org.thingsboard.server.common.data.group.EntityField;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.page.TimePageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.dao.customer.CustomerDao;
import org.thingsboard.server.dao.edge.EdgeDao;
import org.thingsboard.server.dao.entity.AbstractEntityService;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.service.PaginatedRemover;
import org.thingsboard.server.dao.service.TimePaginatedRemover;
import org.thingsboard.server.dao.service.Validator;
import org.thingsboard.server.dao.tenant.TenantDao;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;

import static org.thingsboard.server.dao.DaoUtil.toUUIDs;
import static org.thingsboard.server.dao.service.Validator.*;

@Service
@Slf4j
public class DashboardServiceImpl extends AbstractEntityService implements DashboardService {

    public static final String INCORRECT_DASHBOARD_ID = "Incorrect dashboardId ";
    public static final String INCORRECT_TENANT_ID = "Incorrect tenantId ";
    @Autowired
    private DashboardDao dashboardDao;

    @Autowired
    private DashboardInfoDao dashboardInfoDao;

    @Autowired
    private TenantDao tenantDao;

    @Autowired
    private CustomerDao customerDao;

    @Autowired
    private EdgeDao edgeDao;

    @Override
    public Dashboard findDashboardById(TenantId tenantId, DashboardId dashboardId) {
        log.trace("Executing findDashboardById [{}]", dashboardId);
        Validator.validateId(dashboardId, INCORRECT_DASHBOARD_ID + dashboardId);
        return dashboardDao.findById(tenantId, dashboardId.getId());
    }

    @Override
    public ListenableFuture<Dashboard> findDashboardByIdAsync(TenantId tenantId, DashboardId dashboardId) {
        log.trace("Executing findDashboardByIdAsync [{}]", dashboardId);
        validateId(dashboardId, INCORRECT_DASHBOARD_ID + dashboardId);
        return dashboardDao.findByIdAsync(tenantId, dashboardId.getId());
    }

    @Override
    public DashboardInfo findDashboardInfoById(TenantId tenantId, DashboardId dashboardId) {
        log.trace("Executing findDashboardInfoById [{}]", dashboardId);
        Validator.validateId(dashboardId, INCORRECT_DASHBOARD_ID + dashboardId);
        return dashboardInfoDao.findById(tenantId, dashboardId.getId());
    }

    @Override
    public ListenableFuture<DashboardInfo> findDashboardInfoByIdAsync(TenantId tenantId, DashboardId dashboardId) {
        log.trace("Executing findDashboardInfoByIdAsync [{}]", dashboardId);
        validateId(dashboardId, INCORRECT_DASHBOARD_ID + dashboardId);
        return dashboardInfoDao.findByIdAsync(tenantId, dashboardId.getId());
    }

    @Override
    public ListenableFuture<List<DashboardInfo>> findDashboardInfoByIdsAsync(TenantId tenantId, List<DashboardId> dashboardIds) {
        log.trace("Executing findDashboardInfoByIdsAsync, dashboardIds [{}]", dashboardIds);
        validateIds(dashboardIds, "Incorrect dashboardIds " + dashboardIds);
        return dashboardInfoDao.findDashboardsByIdsAsync(tenantId.getId(), toUUIDs(dashboardIds));
    }

    @Override
    public Dashboard saveDashboard(Dashboard dashboard) {
        log.trace("Executing saveDashboard [{}]", dashboard);
        dashboardValidator.validate(dashboard, DashboardInfo::getTenantId);
        Dashboard savedDashboard = dashboardDao.save(dashboard.getTenantId(), dashboard);
        if (dashboard.getId() == null) {
            entityGroupService.addEntityToEntityGroupAll(savedDashboard.getTenantId(), savedDashboard.getOwnerId(), savedDashboard.getId());
        }
        return savedDashboard;
    }

    @Override
    public Dashboard assignDashboardToCustomer(TenantId tenantId, DashboardId dashboardId, CustomerId customerId) {
        Dashboard dashboard = findDashboardById(tenantId, dashboardId);
        Customer customer = customerDao.findById(tenantId, customerId.getId());
        if (customer == null) {
            throw new DataValidationException("Can't assign dashboard to non-existent customer!");
        }
        if (!customer.getTenantId().getId().equals(dashboard.getTenantId().getId())) {
            throw new DataValidationException("Can't assign dashboard to customer from different tenant!");
        }
        if (dashboard.addAssignedCustomer(customer)) {
            try {
                createRelation(tenantId, new EntityRelation(customerId, dashboardId, EntityRelation.CONTAINS_TYPE, RelationTypeGroup.DASHBOARD));
            } catch (ExecutionException | InterruptedException e) {
                log.warn("[{}] Failed to create dashboard relation. Customer Id: [{}]", dashboardId, customerId);
                throw new RuntimeException(e);
            }
            return saveDashboard(dashboard);
        } else {
            return dashboard;
        }
    }

    @Override
    public Dashboard unassignDashboardFromCustomer(TenantId tenantId, DashboardId dashboardId, CustomerId customerId) {
        Dashboard dashboard = findDashboardById(tenantId, dashboardId);
        Customer customer = customerDao.findById(tenantId, customerId.getId());
        if (customer == null) {
            throw new DataValidationException("Can't unassign dashboard from non-existent customer!");
        }
        if (dashboard.removeAssignedCustomer(customer)) {
            try {
                deleteRelation(tenantId, new EntityRelation(customerId, dashboardId, EntityRelation.CONTAINS_TYPE, RelationTypeGroup.DASHBOARD));
            } catch (ExecutionException | InterruptedException e) {
                log.warn("[{}] Failed to delete dashboard relation. Customer Id: [{}]", dashboardId, customerId);
                throw new RuntimeException(e);
            }
            return saveDashboard(dashboard);
        } else {
            return dashboard;
        }
    }

    private void deleteRelation(TenantId tenantId, EntityRelation dashboardRelation) throws ExecutionException, InterruptedException {
        log.debug("Deleting Dashboard relation: {}", dashboardRelation);
        relationService.deleteRelationAsync(tenantId, dashboardRelation).get();
    }

    private void createRelation(TenantId tenantId, EntityRelation dashboardRelation) throws ExecutionException, InterruptedException {
        log.debug("Creating Dashboard relation: {}", dashboardRelation);
        relationService.saveRelationAsync(tenantId, dashboardRelation).get();
    }

    @Override
    public void deleteDashboard(TenantId tenantId, DashboardId dashboardId) {
        log.trace("Executing deleteDashboard [{}]", dashboardId);
        Validator.validateId(dashboardId, INCORRECT_DASHBOARD_ID + dashboardId);
        deleteEntityRelations(tenantId, dashboardId);
        dashboardDao.removeById(tenantId, dashboardId.getId());
    }

    @Override
    public TextPageData<DashboardInfo> findDashboardsByTenantId(TenantId tenantId, TextPageLink pageLink) {
        log.trace("Executing findDashboardsByTenantId, tenantId [{}], pageLink [{}]", tenantId, pageLink);
        Validator.validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validatePageLink(pageLink, "Incorrect page link " + pageLink);
        List<DashboardInfo> dashboards = dashboardInfoDao.findDashboardsByTenantId(tenantId.getId(), pageLink);
        return new TextPageData<>(dashboards, pageLink);
    }

    @Override
    public void deleteDashboardsByTenantId(TenantId tenantId) {
        log.trace("Executing deleteDashboardsByTenantId, tenantId [{}]", tenantId);
        Validator.validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        tenantDashboardsRemover.removeEntities(tenantId, tenantId);
    }

    @Override
    public void deleteDashboardsByTenantIdAndCustomerId(TenantId tenantId, CustomerId customerId) {
        try {
            List<DashboardId> dashboardIds = fetchCustomerDashboards(tenantId, customerId);
            for (DashboardId dashboardId : dashboardIds) {
                deleteDashboard(tenantId, dashboardId);
            }
        } catch (Exception e) {
            log.error("Failed to delete dashboards", e);
            throw new RuntimeException(e);
        }
    }

    private List<DashboardId> fetchCustomerDashboards(TenantId tenantId, CustomerId customerId) throws Exception {
        List<DashboardId> dashboardIds = new ArrayList<>();
        Optional<EntityGroup> entityGroup = entityGroupService.findEntityGroupByTypeAndName(tenantId, customerId, EntityType.DASHBOARD, EntityGroup.GROUP_ALL_NAME).get();
        if (entityGroup.isPresent()) {
            List<EntityId> childDashboardIds = entityGroupService.findAllEntityIds(tenantId, entityGroup.get().getId(), new TimePageLink(Integer.MAX_VALUE)).get();
            childDashboardIds.forEach(entityId -> dashboardIds.add(new DashboardId(entityId.getId())));
        }
        return dashboardIds;
    }

    @Override
    public ShortEntityView findGroupDashboard(TenantId tenantId, EntityGroupId entityGroupId, EntityId entityId) {
        log.trace("Executing findGroupDashboard, entityGroupId [{}], entityId [{}]", entityGroupId, entityId);
        validateId(entityGroupId, "Incorrect entityGroupId " + entityGroupId);
        validateEntityId(entityId, "Incorrect entityId " + entityId);
        return entityGroupService.findGroupEntity(tenantId, entityGroupId, entityId,
                (dashboardEntityId) -> new DashboardId(dashboardEntityId.getId()),
                (dashboardId) -> findDashboardInfoById(tenantId, dashboardId),
                new DashboardViewFunction());
    }

    @Override
    public ListenableFuture<TimePageData<ShortEntityView>> findDashboardsByEntityGroupId(TenantId tenantId, EntityGroupId entityGroupId, TimePageLink pageLink) {
        log.trace("Executing findDashboardsByEntityGroupId, entityGroupId [{}], pageLink [{}]", entityGroupId, pageLink);
        validateId(entityGroupId, "Incorrect entityGroupId " + entityGroupId);
        validatePageLink(pageLink, "Incorrect page link " + pageLink);
        return entityGroupService.findEntities(tenantId, entityGroupId, pageLink,
                (entityId) -> new DashboardId(entityId.getId()),
                (entityIds) -> findDashboardInfoByIdsAsync(tenantId, entityIds),
                new DashboardViewFunction());
    }

    @Override
    public ListenableFuture<TimePageData<DashboardInfo>> findDashboardEntitiesByEntityGroupId(TenantId tenantId, EntityGroupId entityGroupId, TimePageLink pageLink) {
        log.trace("Executing findDashboardEntitiesByEntityGroupId, entityGroupId [{}], pageLink [{}]", entityGroupId, pageLink);
        validateId(entityGroupId, "Incorrect entityGroupId " + entityGroupId);
        validatePageLink(pageLink, "Incorrect page link " + pageLink);
        return entityGroupService.findEntities(tenantId, entityGroupId, pageLink,
                (entityId) -> new DashboardId(entityId.getId()),
                (entityIds) -> findDashboardInfoByIdsAsync(tenantId, entityIds));
    }

    class DashboardViewFunction implements BiFunction<DashboardInfo, List<EntityField>, ShortEntityView> {

        @Override
        public ShortEntityView apply(DashboardInfo dashboard, List<EntityField> entityFields) {
            ShortEntityView entityView = new ShortEntityView(dashboard.getId());
            entityView.put(EntityField.NAME.name().toLowerCase(), dashboard.getName());
            for (EntityField field : entityFields) {
                String key = field.name().toLowerCase();
                switch (field) {
                    case TITLE:
                        entityView.put(key, dashboard.getTitle());
                        break;
                }
            }
            return entityView;
        }
    }

    @Override
    public Dashboard assignDashboardToEdge(TenantId tenantId, DashboardId dashboardId, EdgeId edgeId) {
        Dashboard dashboard = findDashboardById(tenantId, dashboardId);
        Edge edge = edgeDao.findById(tenantId, edgeId.getId());
        if (edge == null) {
            throw new DataValidationException("Can't assign dashboard to non-existent edge!");
        }
        if (!edge.getTenantId().getId().equals(dashboard.getTenantId().getId())) {
            throw new DataValidationException("Can't assign dashboard to edge from different tenant!");
        }
        if (dashboard.addAssignedEdge(edge)) {
            try {
                createRelation(tenantId, new EntityRelation(edgeId, dashboardId, EntityRelation.CONTAINS_TYPE, RelationTypeGroup.EDGE));
            } catch (ExecutionException | InterruptedException e) {
                log.warn("[{}] Failed to create dashboard relation. Edge Id: [{}]", dashboardId, edgeId);
                throw new RuntimeException(e);
            }
            return saveDashboard(dashboard);
        } else {
            return dashboard;
        }
    }

    @Override
    public Dashboard unassignDashboardFromEdge(TenantId tenantId, DashboardId dashboardId, EdgeId edgeId) {
        Dashboard dashboard = findDashboardById(tenantId, dashboardId);
        Edge edge = edgeDao.findById(tenantId, edgeId.getId());
        if (edge == null) {
            throw new DataValidationException("Can't unassign dashboard from non-existent edge!");
        }
        if (dashboard.removeAssignedEdge(edge)) {
            try {
                deleteRelation(tenantId, new EntityRelation(edgeId, dashboardId, EntityRelation.CONTAINS_TYPE, RelationTypeGroup.EDGE));
            } catch (ExecutionException | InterruptedException e) {
                log.warn("[{}] Failed to delete dashboard relation. Edge Id: [{}]", dashboardId, edgeId);
                throw new RuntimeException(e);
            }
            return saveDashboard(dashboard);
        } else {
            return dashboard;
        }
    }

    @Override
    public void unassignEdgeDashboards(TenantId tenantId, EdgeId edgeId) {
        log.trace("Executing unassignEdgeDashboards, edgeId [{}]", edgeId);
        Validator.validateId(edgeId, "Incorrect edgeId " + edgeId);
        Edge edge = edgeDao.findById(tenantId, edgeId.getId());
        if (edge == null) {
            throw new DataValidationException("Can't unassign dashboards from non-existent edge!");
        }
        new EdgeDashboardsUnassigner(edge).removeEntities(tenantId, edge);
    }

    @Override
    public void updateEdgeDashboards(TenantId tenantId, EdgeId edgeId) {
        log.trace("Executing updateEdgeDashboards, edgeId [{}]", edgeId);
        Validator.validateId(edgeId, "Incorrect edgeId " + edgeId);
        Edge edge = edgeDao.findById(tenantId, edgeId.getId());
        if (edge == null) {
            throw new DataValidationException("Can't update dashboards for non-existent edge!");
        }
        new EdgeDashboardsUpdater(edge).removeEntities(tenantId, edge);
    }

    @Override
    public ListenableFuture<TimePageData<DashboardInfo>> findDashboardsByTenantIdAndEdgeId(TenantId tenantId, EdgeId edgeId, TimePageLink pageLink) {
        log.trace("Executing findDashboardsByTenantIdAndEdgeId, tenantId [{}], edgeId [{}], pageLink [{}]", tenantId, edgeId, pageLink);
        Validator.validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        Validator.validateId(edgeId, "Incorrect customerId " + edgeId);
        Validator.validatePageLink(pageLink, "Incorrect page link " + pageLink);
        ListenableFuture<List<DashboardInfo>> dashboards = dashboardInfoDao.findDashboardsByTenantIdAndEdgeId(tenantId.getId(), edgeId.getId(), pageLink);

        return Futures.transform(dashboards, new Function<List<DashboardInfo>, TimePageData<DashboardInfo>>() {
            @Nullable
            @Override
            public TimePageData<DashboardInfo> apply(@Nullable List<DashboardInfo> dashboards) {
                return new TimePageData<>(dashboards, pageLink);
            }
        }, MoreExecutors.directExecutor());
    }

    private Dashboard updateAssignedEdge(TenantId tenantId, DashboardId dashboardId, Edge edge) {
        Dashboard dashboard = findDashboardById(tenantId, dashboardId);
        if (dashboard.updateAssignedEdge(edge)) {
            return saveDashboard(dashboard);
        } else {
            return dashboard;
        }
    }

    private DataValidator<Dashboard> dashboardValidator =
            new DataValidator<Dashboard>() {
                @Override
                protected void validateDataImpl(TenantId tenantId, Dashboard dashboard) {
                    if (StringUtils.isEmpty(dashboard.getTitle())) {
                        throw new DataValidationException("Dashboard title should be specified!");
                    }
                    if (dashboard.getTenantId() == null) {
                        throw new DataValidationException("Dashboard should be assigned to tenant!");
                    } else {
                        Tenant tenant = tenantDao.findById(tenantId, dashboard.getTenantId().getId());
                        if (tenant == null) {
                            throw new DataValidationException("Dashboard is referencing to non-existent tenant!");
                        }
                    }
                }
            };

    private PaginatedRemover<TenantId, DashboardInfo> tenantDashboardsRemover =
            new PaginatedRemover<TenantId, DashboardInfo>() {

                @Override
                protected List<DashboardInfo> findEntities(TenantId tenantId, TenantId id, TextPageLink pageLink) {
                    return dashboardInfoDao.findDashboardsByTenantId(id.getId(), pageLink);
                }

                @Override
                protected void removeEntity(TenantId tenantId, DashboardInfo entity) {
                    deleteDashboard(tenantId, new DashboardId(entity.getUuidId()));
                }
    };

    private class EdgeDashboardsUnassigner extends TimePaginatedRemover<Edge, DashboardInfo> {

        private Edge edge;

        EdgeDashboardsUnassigner(Edge edge) {
            this.edge = edge;
        }

        @Override
        protected List<DashboardInfo> findEntities(TenantId tenantId, Edge edge, TimePageLink pageLink) {
            try {
                return dashboardInfoDao.findDashboardsByTenantIdAndEdgeId(edge.getTenantId().getId(), edge.getId().getId(), pageLink).get();
            } catch (InterruptedException | ExecutionException e) {
                log.warn("Failed to get dashboards by tenantId [{}] and edgeId [{}].", edge.getTenantId().getId(), edge.getId().getId());
                throw new RuntimeException(e);
            }
        }

        @Override
        protected void removeEntity(TenantId tenantId, DashboardInfo entity) {
            unassignDashboardFromEdge(edge.getTenantId(), new DashboardId(entity.getUuidId()), this.edge.getId());
        }

    }

    private class EdgeDashboardsUpdater extends TimePaginatedRemover<Edge, DashboardInfo> {

        private Edge edge;

        EdgeDashboardsUpdater(Edge edge) {
            this.edge = edge;
        }

        @Override
        protected List<DashboardInfo> findEntities(TenantId tenantId, Edge edge, TimePageLink pageLink) {
            try {
                return dashboardInfoDao.findDashboardsByTenantIdAndEdgeId(edge.getTenantId().getId(), edge.getId().getId(), pageLink).get();
            } catch (InterruptedException | ExecutionException e) {
                log.warn("Failed to get dashboards by tenantId [{}] and edgeId [{}].", edge.getTenantId().getId(), edge.getId().getId());
                throw new RuntimeException(e);
            }
        }

        @Override
        protected void removeEntity(TenantId tenantId, DashboardInfo entity) {
            updateAssignedEdge(edge.getTenantId(), new DashboardId(entity.getUuidId()), this.edge);
        }

    }
}
