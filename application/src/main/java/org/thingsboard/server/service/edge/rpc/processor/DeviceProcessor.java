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
package org.thingsboard.server.service.edge.rpc.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.stereotype.Component;
import org.thingsboard.rule.engine.api.RpcError;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.Edge;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.edge.EdgeEventType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.data.security.DeviceCredentialsType;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgDataType;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.dao.util.mapping.JacksonUtil;
import org.thingsboard.server.gen.edge.DeviceCredentialsUpdateMsg;
import org.thingsboard.server.gen.edge.DeviceRpcCallMsg;
import org.thingsboard.server.gen.edge.DeviceUpdateMsg;
import org.thingsboard.server.queue.TbQueueCallback;
import org.thingsboard.server.queue.TbQueueMsgMetadata;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.rpc.FromDeviceRpcResponse;
import org.thingsboard.server.service.rpc.FromDeviceRpcResponseActorMsg;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Slf4j
@TbCoreComponent
public class DeviceProcessor extends BaseProcessor {

    private static final ReentrantLock deviceCreationLock = new ReentrantLock();

    public ListenableFuture<Void> onDeviceUpdate(TenantId tenantId, Edge edge, DeviceUpdateMsg deviceUpdateMsg) {
        log.trace("[{}] onDeviceUpdate [{}] from edge [{}]", tenantId, deviceUpdateMsg, edge.getName());
        switch (deviceUpdateMsg.getMsgType()) {
            case ENTITY_CREATED_RPC_MESSAGE:
                String deviceName = deviceUpdateMsg.getName();
                Device device = deviceService.findDeviceByTenantIdAndName(tenantId, deviceName);
                if (device != null) {
                    ListenableFuture<List<EdgeId>> future = edgeService.findRelatedEdgeIdsByEntityId(tenantId, device.getId(), null);
                    SettableFuture<Void> futureToSet = SettableFuture.create();
                    Futures.addCallback(future, new FutureCallback<List<EdgeId>>() {
                        @Override
                        public void onSuccess(@Nullable List<EdgeId> edgeIds) {
                            boolean update = false;
                            if (edgeIds != null && !edgeIds.isEmpty()) {
                                if (edgeIds.contains(edge.getId())) {
                                    update = true;
                                }
                            }
                            Device device;
                            if (update) {
                                log.info("[{}] Device with name '{}' already exists on the cloud, and related to this edge [{}]. " +
                                        "deviceUpdateMsg [{}], Updating device", tenantId, deviceName, edge.getId(), deviceUpdateMsg);
                                updateDevice(tenantId, edge, deviceUpdateMsg);
                            } else {
                                log.info("[{}] Device with name '{}' already exists on the cloud, but not related to this edge [{}]. deviceUpdateMsg [{}]." +
                                        "Creating a new device with random prefix and relate to this edge", tenantId, deviceName, edge.getId(), deviceUpdateMsg);
                                String newDeviceName = deviceUpdateMsg.getName() + "_" + RandomStringUtils.randomAlphabetic(15);
                                device = createDevice(tenantId, edge, deviceUpdateMsg, newDeviceName);
                                ObjectNode body = mapper.createObjectNode();
                                body.put("conflictName", deviceName);
                                saveEdgeEvent(tenantId, edge.getId(), EdgeEventType.DEVICE, EdgeEventActionType.ENTITY_MERGE_REQUEST, device.getId(), body);
                                // TODO: voba - fix this
                                // deviceService.assignDeviceToEdge(edge.getTenantId(), device.getId(), edge.getId());
                            }
                            futureToSet.set(null);
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            log.error("[{}] Failed to get related edge ids by device id [{}], edge [{}]", tenantId, deviceUpdateMsg, edge.getId(), t);
                            futureToSet.setException(t);
                        }
                    }, dbCallbackExecutorService);
                    return futureToSet;
                } else {
                    log.info("[{}] Creating new device and replacing device entity on the edge [{}]", tenantId, deviceUpdateMsg);
                    device = createDevice(tenantId, edge, deviceUpdateMsg, deviceUpdateMsg.getName());
                    saveEdgeEvent(tenantId, edge.getId(), EdgeEventType.DEVICE, EdgeEventActionType.ENTITY_MERGE_REQUEST, device.getId(), null);
                    // TODO: voba - fix this
                    // deviceService.assignDeviceToEdge(edge.getTenantId(), device.getId(), edge.getId());
                }
                // TODO: voba - assign device only in case device is not assigned yet. Missing functionality to check this relation prior assignment
//               TODO: voba
//                entityGroupService.addEntityToEntityGroupAll(device.getTenantId(), device.getOwnerId(), device.getId());
//                addDeviceToDeviceGroup(tenantId, edge, device.getId());
                break;
            case ENTITY_UPDATED_RPC_MESSAGE:
                updateDevice(tenantId, edge, deviceUpdateMsg);
                break;
            case ENTITY_DELETED_RPC_MESSAGE:
                // TODO: voba
                // removeDeviceFromDeviceGroup(tenantId, edge, edgeDeviceId);

//                DeviceId deviceId = new DeviceId(new UUID(deviceUpdateMsg.getIdMSB(), deviceUpdateMsg.getIdLSB()));
//                Device deviceToDelete = deviceService.findDeviceById(tenantId, deviceId);
//                if (deviceToDelete != null) {
//                    deviceService.unassignDeviceFromEdge(tenantId, deviceId, edge.getId());
//                }
                break;
            case UNRECOGNIZED:
                log.error("Unsupported msg type {}", deviceUpdateMsg.getMsgType());
                return Futures.immediateFailedFuture(new RuntimeException("Unsupported msg type " + deviceUpdateMsg.getMsgType()));
        }
        return Futures.immediateFuture(null);
    }

    public ListenableFuture<Void> onDeviceCredentialsUpdate(TenantId tenantId, DeviceCredentialsUpdateMsg deviceCredentialsUpdateMsg) {
        log.debug("Executing onDeviceCredentialsUpdate, deviceCredentialsUpdateMsg [{}]", deviceCredentialsUpdateMsg);
        DeviceId deviceId = new DeviceId(new UUID(deviceCredentialsUpdateMsg.getDeviceIdMSB(), deviceCredentialsUpdateMsg.getDeviceIdLSB()));
        ListenableFuture<Device> deviceFuture = deviceService.findDeviceByIdAsync(tenantId, deviceId);
        return Futures.transform(deviceFuture, device -> {
            if (device != null) {
                log.debug("Updating device credentials for device [{}]. New device credentials Id [{}], value [{}]",
                        device.getName(), deviceCredentialsUpdateMsg.getCredentialsId(), deviceCredentialsUpdateMsg.getCredentialsValue());
                try {
                    DeviceCredentials deviceCredentials = deviceCredentialsService.findDeviceCredentialsByDeviceId(tenantId, device.getId());
                    deviceCredentials.setCredentialsType(DeviceCredentialsType.valueOf(deviceCredentialsUpdateMsg.getCredentialsType()));
                    deviceCredentials.setCredentialsId(deviceCredentialsUpdateMsg.getCredentialsId());
                    deviceCredentials.setCredentialsValue(deviceCredentialsUpdateMsg.getCredentialsValue());
                    deviceCredentialsService.updateDeviceCredentials(tenantId, deviceCredentials);
                } catch (Exception e) {
                    log.error("Can't update device credentials for device [{}], deviceCredentialsUpdateMsg [{}]", device.getName(), deviceCredentialsUpdateMsg, e);
                    throw new RuntimeException(e);
                }
            }
            return null;
        }, dbCallbackExecutorService);
    }

    private void updateDevice(TenantId tenantId, Edge edge, DeviceUpdateMsg deviceUpdateMsg) {
        DeviceId deviceId = new DeviceId(new UUID(deviceUpdateMsg.getIdMSB(), deviceUpdateMsg.getIdLSB()));
        Device device = deviceService.findDeviceById(tenantId, deviceId);
        if (device != null) {
            device.setName(deviceUpdateMsg.getName());
            device.setType(deviceUpdateMsg.getType());
            device.setLabel(deviceUpdateMsg.getLabel());
            device.setAdditionalInfo(JacksonUtil.toJsonNode(deviceUpdateMsg.getAdditionalInfo()));
            if (deviceUpdateMsg.getDeviceProfileIdMSB() != 0 && deviceUpdateMsg.getDeviceProfileIdLSB() != 0) {
                DeviceProfileId deviceProfileId = new DeviceProfileId(
                        new UUID(deviceUpdateMsg.getDeviceProfileIdMSB(), deviceUpdateMsg.getDeviceProfileIdLSB()));
                device.setDeviceProfileId(deviceProfileId);
            }
            deviceService.saveDevice(device);
            saveEdgeEvent(tenantId, edge.getId(), EdgeEventType.DEVICE, EdgeEventActionType.CREDENTIALS_REQUEST, deviceId, null);
        } else {
            log.warn("[{}] can't find device [{}], edge [{}]", tenantId, deviceUpdateMsg, edge.getId());
        }
    }

    private Device createDevice(TenantId tenantId, Edge edge, DeviceUpdateMsg deviceUpdateMsg, String deviceName) {
        Device device;
        try {
            deviceCreationLock.lock();
            log.debug("[{}] Creating device entity [{}] from edge [{}]", tenantId, deviceUpdateMsg, edge.getName());
            device = new Device();
            device.setTenantId(edge.getTenantId());
            device.setCustomerId(edge.getCustomerId());
            device.setName(deviceName);
            device.setType(deviceUpdateMsg.getType());
            device.setLabel(deviceUpdateMsg.getLabel());
            device.setAdditionalInfo(JacksonUtil.toJsonNode(deviceUpdateMsg.getAdditionalInfo()));
            if (deviceUpdateMsg.getDeviceProfileIdMSB() != 0 && deviceUpdateMsg.getDeviceProfileIdLSB() != 0) {
                DeviceProfileId deviceProfileId = new DeviceProfileId(
                        new UUID(deviceUpdateMsg.getDeviceProfileIdMSB(), deviceUpdateMsg.getDeviceProfileIdLSB()));
                device.setDeviceProfileId(deviceProfileId);
            }
            device = deviceService.saveDevice(device);
            createRelationFromEdge(tenantId, edge.getId(), device.getId());
            deviceStateService.onDeviceAdded(device);
            pushDeviceCreatedEventToRuleEngine(tenantId, edge, device);
        } finally {
            deviceCreationLock.unlock();
        }
        return device;
    }

    private void createRelationFromEdge(TenantId tenantId, EdgeId edgeId, EntityId entityId) {
        EntityRelation relation = new EntityRelation();
        relation.setFrom(edgeId);
        relation.setTo(entityId);
        relation.setTypeGroup(RelationTypeGroup.COMMON);
        relation.setType(EntityRelation.EDGE_TYPE);
        relationService.saveRelation(tenantId, relation);
    }

    private void pushDeviceCreatedEventToRuleEngine(TenantId tenantId, Edge edge, Device device) {
        try {
            DeviceId deviceId = device.getId();
            ObjectNode entityNode = mapper.valueToTree(device);
            TbMsg tbMsg = TbMsg.newMsg(DataConstants.ENTITY_CREATED, deviceId,
                    getActionTbMsgMetaData(edge, device.getCustomerId()), TbMsgDataType.JSON, mapper.writeValueAsString(entityNode));
            tbClusterService.pushMsgToRuleEngine(tenantId, deviceId, tbMsg, new TbQueueCallback() {
                @Override
                public void onSuccess(TbQueueMsgMetadata metadata) {
                    log.debug("Successfully send ENTITY_CREATED EVENT to rule engine [{}]", device);
                }

                @Override
                public void onFailure(Throwable t) {
                    log.debug("Failed to send ENTITY_CREATED EVENT to rule engine [{}]", device, t);
                }
            });
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("[{}] Failed to push device action to rule engine: {}", device.getId(), DataConstants.ENTITY_CREATED, e);
        }
    }

    private TbMsgMetaData getActionTbMsgMetaData(Edge edge, CustomerId customerId) {
        TbMsgMetaData metaData = getTbMsgMetaData(edge);
        if (customerId != null && !customerId.isNullUid()) {
            metaData.putValue("customerId", customerId.toString());
        }
        return metaData;
    }

    private TbMsgMetaData getTbMsgMetaData(Edge edge) {
        TbMsgMetaData metaData = new TbMsgMetaData();
        metaData.putValue("edgeId", edge.getId().toString());
        metaData.putValue("edgeName", edge.getName());
        return metaData;
    }

    private void removeDeviceFromDeviceGroup(TenantId tenantId, Edge edge, DeviceId deviceId) {
        Device deviceToDelete = deviceService.findDeviceById(tenantId, deviceId);
        if (deviceToDelete != null) {
            ListenableFuture<EntityGroup> edgeDeviceGroup = entityGroupService.findOrCreateEdgeAllGroup(tenantId, edge, edge.getName(), EntityType.DEVICE);
            Futures.addCallback(edgeDeviceGroup, new FutureCallback<EntityGroup>() {
                @Override
                public void onSuccess(@Nullable EntityGroup entityGroup) {
                    if (entityGroup != null) {
                        entityGroupService.removeEntityFromEntityGroup(tenantId, entityGroup.getId(), deviceToDelete.getId());
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    log.warn("Can't remove from edge device group, device id [{}]", deviceId, t);
                }
            }, dbCallbackExecutorService);
        }
    }

    private void addDeviceToDeviceGroup(TenantId tenantId, Edge edge, DeviceId deviceId) {
        ListenableFuture<EntityGroup> edgeDeviceGroup = entityGroupService.findOrCreateEdgeAllGroup(tenantId, edge, edge.getName(), EntityType.DEVICE);
        Futures.addCallback(edgeDeviceGroup, new FutureCallback<EntityGroup>() {
            @Override
            public void onSuccess(@Nullable EntityGroup entityGroup) {
                if (entityGroup != null) {
                    entityGroupService.addEntityToEntityGroup(tenantId, entityGroup.getId(), deviceId);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                log.warn("Can't add device to edge device group, device id [{}]", deviceId, t);
            }
        }, dbCallbackExecutorService);
    }

    public ListenableFuture<Void> processDeviceRpcCallResponseMsg(TenantId tenantId, DeviceRpcCallMsg deviceRpcCallMsg) {
        log.trace("[{}] processDeviceRpcCallResponseMsg [{}]", tenantId, deviceRpcCallMsg);
        SettableFuture<Void> futureToSet = SettableFuture.create();
        UUID requestUuid = new UUID(deviceRpcCallMsg.getRequestUuidMSB(), deviceRpcCallMsg.getRequestUuidLSB());
        DeviceId deviceId = new DeviceId(new UUID(deviceRpcCallMsg.getDeviceIdMSB(), deviceRpcCallMsg.getDeviceIdLSB()));

        FromDeviceRpcResponse response;
        if (!StringUtils.isEmpty(deviceRpcCallMsg.getResponseMsg().getError())) {
            response = new FromDeviceRpcResponse(requestUuid, null, RpcError.valueOf(deviceRpcCallMsg.getResponseMsg().getError()));
        } else {
            response = new FromDeviceRpcResponse(requestUuid, deviceRpcCallMsg.getResponseMsg().getResponse(), null);
        }
        TbQueueCallback callback = new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                futureToSet.set(null);
            }

            @Override
            public void onFailure(Throwable t) {
                log.error("Can't process push notification to core [{}]", deviceRpcCallMsg, t);
                futureToSet.setException(t);
            }
        };
        FromDeviceRpcResponseActorMsg msg =
                new FromDeviceRpcResponseActorMsg(deviceRpcCallMsg.getRequestId(),
                        tenantId,
                        deviceId, response);
        tbClusterService.pushMsgToCore(msg, callback);
        return futureToSet;
    }
}
