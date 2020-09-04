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
package org.thingsboard.server.service.edge.rpc.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.server.common.data.Edge;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.edge.EdgeEventType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.alarm.AlarmService;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.customer.CustomerService;
import org.thingsboard.server.dao.dashboard.DashboardService;
import org.thingsboard.server.dao.device.DeviceCredentialsService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.edge.EdgeEventService;
import org.thingsboard.server.dao.entityview.EntityViewService;
import org.thingsboard.server.dao.group.EntityGroupService;
import org.thingsboard.server.dao.relation.RelationService;
import org.thingsboard.server.dao.user.UserService;
import org.thingsboard.server.service.executors.DbCallbackExecutorService;
import org.thingsboard.server.service.queue.TbClusterService;
import org.thingsboard.server.service.state.DeviceStateService;

import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public abstract class BaseProcessor {

    protected static final ObjectMapper mapper = new ObjectMapper();

    private static final ReentrantLock entityGroupCreationLock = new ReentrantLock();

    @Autowired
    protected AlarmService alarmService;

    @Autowired
    protected DeviceService deviceService;

    @Autowired
    protected DashboardService dashboardService;

    @Autowired
    protected AssetService assetService;

    @Autowired
    protected EntityViewService entityViewService;

    @Autowired
    protected CustomerService customerService;

    @Autowired
    protected UserService userService;

    @Autowired
    protected RelationService relationService;

    @Autowired
    protected DeviceCredentialsService deviceCredentialsService;

    @Autowired
    protected AttributesService attributesService;

    @Autowired
    protected TbClusterService tbClusterService;

    @Autowired
    protected DeviceStateService deviceStateService;

    @Autowired
    protected EdgeEventService edgeEventService;

    @Autowired
    protected EntityGroupService entityGroupService;

    @Autowired
    protected DbCallbackExecutorService dbCallbackExecutorService;

    protected ListenableFuture<EdgeEvent> saveEdgeEvent(TenantId tenantId,
                                                        EdgeId edgeId,
                                                        EdgeEventType edgeEventType,
                                                        ActionType edgeEventAction,
                                                        EntityId entityId,
                                                        JsonNode entityBody) {
        log.debug("Pushing event to edge queue. tenantId [{}], edgeId [{}], edgeEventType[{}], " +
                        "edgeEventAction [{}], entityId [{}], entityBody [{}]",
                tenantId, edgeId, edgeEventType, edgeEventAction, entityId, entityBody);

        EdgeEvent edgeEvent = new EdgeEvent();
        edgeEvent.setTenantId(tenantId);
        edgeEvent.setEdgeId(edgeId);
        edgeEvent.setEdgeEventType(edgeEventType);
        edgeEvent.setEdgeEventAction(edgeEventAction.name());
        if (entityId != null) {
            edgeEvent.setEntityId(entityId.getId());
        }
        edgeEvent.setEntityBody(entityBody);
        return edgeEventService.saveAsync(edgeEvent);
    }

    protected ListenableFuture<EntityGroup> findEdgeAllGroup(TenantId tenantId, Edge edge, EntityType groupType) {
        String deviceGroupName = String.format(EntityGroup.GROUP_EDGE_ALL_NAME_PATTERN, edge.getName());
        ListenableFuture<Optional<EntityGroup>> futureEntityGroup = entityGroupService
                .findEntityGroupByTypeAndName(tenantId, edge.getOwnerId(), groupType, deviceGroupName);

        return Futures.transform(futureEntityGroup, optionalEntityGroup -> {
            if (optionalEntityGroup != null && optionalEntityGroup.isPresent()) {
                return optionalEntityGroup.get();
            } else {
                try {
                    entityGroupCreationLock.lock();
                    Optional<EntityGroup> currentEntityGroup = entityGroupService
                            .findEntityGroupByTypeAndName(tenantId, edge.getOwnerId(), groupType, deviceGroupName).get();
                    if (!currentEntityGroup.isPresent()) {
                        EntityGroup entityGroup = createEntityGroup(deviceGroupName, edge.getOwnerId(), tenantId);
                        entityGroupService.assignEntityGroupToEdge(tenantId, entityGroup.getId(), edge.getId(), EntityType.DEVICE);
                        return entityGroup;
                    } else {
                        return currentEntityGroup.get();
                    }
                } catch (Exception e) {
                    log.error("[{}] Can't get entity group by name edge owner id [{}], groupType [{}], deviceGroupName [{}]",
                            tenantId, edge.getOwnerId(), groupType, deviceGroupName, e);
                    throw new RuntimeException(e);
                } finally {
                    entityGroupCreationLock.unlock();
                }
            }
        }, dbCallbackExecutorService);
    }

    private EntityGroup createEntityGroup(String entityGroupName, EntityId parentEntityId, TenantId tenantId) {
        EntityGroup entityGroup = new EntityGroup();
        entityGroup.setName(entityGroupName);
        entityGroup.setType(EntityType.DEVICE);
        return entityGroupService.saveEntityGroup(tenantId, parentEntityId, entityGroup);
    }
}
