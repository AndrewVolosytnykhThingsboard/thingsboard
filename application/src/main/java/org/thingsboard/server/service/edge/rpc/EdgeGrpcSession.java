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
package org.thingsboard.server.service.edge.rpc;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.JsonElement;
import io.grpc.stub.StreamObserver;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.thingsboard.server.common.data.AdminSettings;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.Edge;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.edge.EdgeEventType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.AlarmId;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.GroupPermissionId;
import org.thingsboard.server.common.data.id.RoleId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.SchedulerEventId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.id.WidgetTypeId;
import org.thingsboard.server.common.data.id.WidgetsBundleId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.BaseAttributeKvEntry;
import org.thingsboard.server.common.data.kv.LongDataEntry;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.SortOrder;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.permission.GroupPermission;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.role.Role;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.scheduler.SchedulerEvent;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.data.security.UserCredentials;
import org.thingsboard.server.common.data.translation.CustomTranslation;
import org.thingsboard.server.common.data.widget.WidgetType;
import org.thingsboard.server.common.data.widget.WidgetsBundle;
import org.thingsboard.server.common.data.wl.LoginWhiteLabelingParams;
import org.thingsboard.server.common.data.wl.WhiteLabelingParams;
import org.thingsboard.server.common.transport.util.JsonUtils;
import org.thingsboard.server.gen.edge.AdminSettingsUpdateMsg;
import org.thingsboard.server.gen.edge.AlarmUpdateMsg;
import org.thingsboard.server.gen.edge.AssetUpdateMsg;
import org.thingsboard.server.gen.edge.AttributesRequestMsg;
import org.thingsboard.server.gen.edge.ConnectRequestMsg;
import org.thingsboard.server.gen.edge.ConnectResponseCode;
import org.thingsboard.server.gen.edge.ConnectResponseMsg;
import org.thingsboard.server.gen.edge.CustomTranslationProto;
import org.thingsboard.server.gen.edge.CustomerUpdateMsg;
import org.thingsboard.server.gen.edge.DashboardUpdateMsg;
import org.thingsboard.server.gen.edge.DeviceCredentialsRequestMsg;
import org.thingsboard.server.gen.edge.DeviceCredentialsUpdateMsg;
import org.thingsboard.server.gen.edge.DeviceRpcCallMsg;
import org.thingsboard.server.gen.edge.DeviceUpdateMsg;
import org.thingsboard.server.gen.edge.DownlinkMsg;
import org.thingsboard.server.gen.edge.DownlinkResponseMsg;
import org.thingsboard.server.gen.edge.EdgeConfiguration;
import org.thingsboard.server.gen.edge.EdgeUpdateMsg;
import org.thingsboard.server.gen.edge.EntityDataProto;
import org.thingsboard.server.gen.edge.EntityGroupRequestMsg;
import org.thingsboard.server.gen.edge.EntityViewUpdateMsg;
import org.thingsboard.server.gen.edge.LoginWhiteLabelingParamsProto;
import org.thingsboard.server.gen.edge.RelationRequestMsg;
import org.thingsboard.server.gen.edge.RelationUpdateMsg;
import org.thingsboard.server.gen.edge.RequestMsg;
import org.thingsboard.server.gen.edge.RequestMsgType;
import org.thingsboard.server.gen.edge.ResponseMsg;
import org.thingsboard.server.gen.edge.RuleChainMetadataRequestMsg;
import org.thingsboard.server.gen.edge.RuleChainMetadataUpdateMsg;
import org.thingsboard.server.gen.edge.RuleChainUpdateMsg;
import org.thingsboard.server.gen.edge.UpdateMsgType;
import org.thingsboard.server.gen.edge.UplinkMsg;
import org.thingsboard.server.gen.edge.UplinkResponseMsg;
import org.thingsboard.server.gen.edge.UserCredentialsRequestMsg;
import org.thingsboard.server.gen.edge.UserCredentialsUpdateMsg;
import org.thingsboard.server.gen.edge.WhiteLabelingParamsProto;
import org.thingsboard.server.gen.edge.WidgetTypeUpdateMsg;
import org.thingsboard.server.gen.edge.WidgetsBundleUpdateMsg;
import org.thingsboard.server.service.edge.EdgeContextComponent;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Slf4j
@Data
public final class EdgeGrpcSession implements Closeable {

    private static final ReentrantLock responseMsgLock = new ReentrantLock();

    private static final String QUEUE_START_TS_ATTR_KEY = "queueStartTs";

    private final UUID sessionId;
    private final BiConsumer<EdgeId, EdgeGrpcSession> sessionOpenListener;
    private final Consumer<EdgeId> sessionCloseListener;
    private final ObjectMapper mapper;

    private EdgeContextComponent ctx;
    private Edge edge;
    private StreamObserver<RequestMsg> inputStream;
    private StreamObserver<ResponseMsg> outputStream;
    private boolean connected;

    private CountDownLatch latch;

    EdgeGrpcSession(EdgeContextComponent ctx, StreamObserver<ResponseMsg> outputStream, BiConsumer<EdgeId, EdgeGrpcSession> sessionOpenListener,
                    Consumer<EdgeId> sessionCloseListener, ObjectMapper mapper) {
        this.sessionId = UUID.randomUUID();
        this.ctx = ctx;
        this.outputStream = outputStream;
        this.sessionOpenListener = sessionOpenListener;
        this.sessionCloseListener = sessionCloseListener;
        this.mapper = mapper;
        initInputStream();
    }

    private void initInputStream() {
        this.inputStream = new StreamObserver<RequestMsg>() {
            @Override
            public void onNext(RequestMsg requestMsg) {
                if (!connected && requestMsg.getMsgType().equals(RequestMsgType.CONNECT_RPC_MESSAGE)) {
                    ConnectResponseMsg responseMsg = processConnect(requestMsg.getConnectRequestMsg());
                    outputStream.onNext(ResponseMsg.newBuilder()
                            .setConnectResponseMsg(responseMsg)
                            .build());
                    if (ConnectResponseCode.ACCEPTED != responseMsg.getResponseCode()) {
                        outputStream.onError(new RuntimeException(responseMsg.getErrorMsg()));
                    }
                }
                if (!connected && requestMsg.getMsgType().equals(RequestMsgType.SYNC_REQUEST_RPC_MESSAGE)) {
                    connected = true;
                    ctx.getSyncEdgeService().sync(edge);
                }
                if (connected) {
                    if (requestMsg.getMsgType().equals(RequestMsgType.UPLINK_RPC_MESSAGE) && requestMsg.hasUplinkMsg()) {
                        onUplinkMsg(requestMsg.getUplinkMsg());
                    }
                    if (requestMsg.getMsgType().equals(RequestMsgType.UPLINK_RPC_MESSAGE) && requestMsg.hasDownlinkResponseMsg()) {
                        onDownlinkResponse(requestMsg.getDownlinkResponseMsg());
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("Failed to deliver message from client!", t);
            }

            @Override
            public void onCompleted() {
                connected = false;
                if (edge != null) {
                    sessionCloseListener.accept(edge.getId());
                }
                try {
                    outputStream.onCompleted();
                } catch (Exception ignored) {}
            }
        };
    }

    private void onUplinkMsg(UplinkMsg uplinkMsg) {
        ListenableFuture<List<Void>> future = processUplinkMsg(uplinkMsg);
        Futures.addCallback(future, new FutureCallback<List<Void>>() {
            @Override
            public void onSuccess(@Nullable List<Void> result) {
                UplinkResponseMsg uplinkResponseMsg = UplinkResponseMsg.newBuilder().setSuccess(true).build();
                sendResponseMsg(ResponseMsg.newBuilder()
                        .setUplinkResponseMsg(uplinkResponseMsg)
                        .build());
            }

            @Override
            public void onFailure(Throwable t) {
                UplinkResponseMsg uplinkResponseMsg = UplinkResponseMsg.newBuilder().setSuccess(false).setErrorMsg(t.getMessage()).build();
                sendResponseMsg(ResponseMsg.newBuilder()
                        .setUplinkResponseMsg(uplinkResponseMsg)
                        .build());
            }
        }, MoreExecutors.directExecutor());
    }

    private void onDownlinkResponse(DownlinkResponseMsg msg) {
        try {
            if (msg.getSuccess()) {
                log.debug("[{}] Msg has been processed successfully! {}", edge.getRoutingKey(), msg);
            } else {
                log.error("[{}] Msg processing failed! Error msg: {}", edge.getRoutingKey(), msg.getErrorMsg());
            }
            latch.countDown();
        } catch (Exception e) {
            log.error("Can't process downlink response message [{}]", msg, e);
        }
    }

    private void sendResponseMsg(ResponseMsg responseMsg) {
        if (isConnected()) {
            try {
                responseMsgLock.lock();
                outputStream.onNext(responseMsg);
            } catch (Exception e) {
                log.error("Failed to send response message [{}]", responseMsg, e);
                connected = false;
                sessionCloseListener.accept(edge.getId());
            } finally {
                responseMsgLock.unlock();
            }
        }
    }

    void onConfigurationUpdate(Edge edge) {
        try {
            this.edge = edge;
            EdgeUpdateMsg edgeConfig = EdgeUpdateMsg.newBuilder()
                    .setConfiguration(constructEdgeConfigProto(edge)).build();
            outputStream.onNext(ResponseMsg.newBuilder()
                    .setEdgeUpdateMsg(edgeConfig)
                    .build());
        } catch (Exception e) {
            log.error("Failed to construct proto objects!", e);
        }
    }

    void processHandleMessages() throws ExecutionException, InterruptedException {
        if (isConnected()) {
            Long queueStartTs = getQueueStartTs().get();
            TimePageLink pageLink = new TimePageLink(
                    ctx.getEdgeEventStorageSettings().getMaxReadRecordsCount(),
                    0,
                    null,
                    new SortOrder("createdTime", SortOrder.Direction.ASC),
                    queueStartTs,
                    null);
            PageData<EdgeEvent> pageData;
            UUID ifOffset = null;
            boolean success = true;
            do {
                pageData = ctx.getEdgeNotificationService().findEdgeEvents(edge.getTenantId(), edge.getId(), pageLink);
                if (isConnected() && !pageData.getData().isEmpty()) {
                    log.trace("[{}] [{}] event(s) are going to be processed.", this.sessionId, pageData.getData().size());
                    List<DownlinkMsg> downlinkMsgsPack = convertToDownlinkMsgsPack(pageData.getData());
                    log.trace("[{}] downlink msg(s) are going to be send.", downlinkMsgsPack.size());

                    latch = new CountDownLatch(downlinkMsgsPack.size());
                    for (DownlinkMsg downlinkMsg : downlinkMsgsPack) {
                        sendResponseMsg(ResponseMsg.newBuilder()
                                .setDownlinkMsg(downlinkMsg)
                                .build());
                    }

                    ifOffset = pageData.getData().get(pageData.getData().size() - 1).getUuidId();

                    success = latch.await(10, TimeUnit.SECONDS);
                    if (!success) {
                        log.warn("Failed to deliver the batch: {}", downlinkMsgsPack);
                    }
                }
                if (isConnected() && (!success || pageData.hasNext())) {
                    try {
                        Thread.sleep(ctx.getEdgeEventStorageSettings().getSleepIntervalBetweenBatches());
                    } catch (InterruptedException e) {
                        log.error("Error during sleep between batches", e);
                    }
                    if (success) {
                        pageLink = pageLink.nextPageLink();
                    }
                }
            } while (isConnected() && (!success || pageData.hasNext()));

            if (ifOffset != null) {
                Long newStartTs = Uuids.unixTimestamp(ifOffset);
                updateQueueStartTs(newStartTs);
            }
            try {
                Thread.sleep(ctx.getEdgeEventStorageSettings().getNoRecordsSleepInterval());
            } catch (InterruptedException e) {
                log.error("Error during sleep", e);
            }
        }
    }

    private List<DownlinkMsg> convertToDownlinkMsgsPack(List<EdgeEvent> edgeEvents) {
        List<DownlinkMsg> result = new ArrayList<>();
        for (EdgeEvent edgeEvent : edgeEvents) {
            log.trace("Processing edge event [{}]", edgeEvent);
            try {
                DownlinkMsg downlinkMsg = null;
                ActionType action = ActionType.valueOf(edgeEvent.getAction());
                switch (action) {
                    case UPDATED:
                    case ADDED:
                    case DELETED:
                    case ADDED_TO_ENTITY_GROUP:
                    case REMOVED_FROM_ENTITY_GROUP:
                    case ASSIGNED_TO_EDGE:
                    case UNASSIGNED_FROM_EDGE:
                    case ALARM_ACK:
                    case ALARM_CLEAR:
                    case CREDENTIALS_UPDATED:
                    case RELATION_ADD_OR_UPDATE:
                    case RELATION_DELETED:
                        downlinkMsg = processEntityMessage(edgeEvent, action);
                        break;
                    case ATTRIBUTES_UPDATED:
                    case ATTRIBUTES_DELETED:
                    case TIMESERIES_UPDATED:
                        downlinkMsg = processTelemetryMessage(edgeEvent);
                        break;
                    case CREDENTIALS_REQUEST:
                        downlinkMsg = processCredentialsRequestMessage(edgeEvent);
                        break;
                    case ENTITY_EXISTS_REQUEST:
                        downlinkMsg = processEntityExistsRequestMessage(edgeEvent);
                        break;
                    case RPC_CALL:
                        downlinkMsg = processRpcCallMsg(edgeEvent);
                        break;
                }
                if (downlinkMsg != null) {
                    result.add(downlinkMsg);
                }
            } catch (Exception e) {
                log.error("Exception during processing records from queue", e);
            }
        }
        return result;
    }

    private DownlinkMsg processEntityExistsRequestMessage(EdgeEvent edgeEvent) {
        DownlinkMsg downlinkMsg = null;
        if (EdgeEventType.DEVICE.equals(edgeEvent.getType())) {
            DeviceId deviceId = new DeviceId(edgeEvent.getEntityId());
            Device device = ctx.getDeviceService().findDeviceById(edge.getTenantId(), deviceId);
            DeviceUpdateMsg d =
                    ctx.getDeviceMsgConstructor().constructDeviceUpdatedMsg(UpdateMsgType.DEVICE_CONFLICT_RPC_MESSAGE, device, null);
            downlinkMsg = DownlinkMsg.newBuilder()
                    .addAllDeviceUpdateMsg(Collections.singletonList(d))
                    .build();
        }
        return downlinkMsg;
    }

    private DownlinkMsg processRpcCallMsg(EdgeEvent edgeEvent) {
        log.trace("Executing processRpcCall, edgeEvent [{}]", edgeEvent);
        DeviceRpcCallMsg deviceRpcCallMsg =
                ctx.getDeviceMsgConstructor().constructDeviceRpcCallMsg(edgeEvent.getBody());
        return DownlinkMsg.newBuilder()
                .addAllDeviceRpcCallMsg(Collections.singletonList(deviceRpcCallMsg))
                .build();
    }

    private DownlinkMsg processCredentialsRequestMessage(EdgeEvent edgeEvent) {
        DownlinkMsg downlinkMsg = null;
        if (EdgeEventType.DEVICE.equals(edgeEvent.getType())) {
            DeviceId deviceId = new DeviceId(edgeEvent.getEntityId());
            DeviceCredentialsRequestMsg deviceCredentialsRequestMsg = DeviceCredentialsRequestMsg.newBuilder()
                    .setDeviceIdMSB(deviceId.getId().getMostSignificantBits())
                    .setDeviceIdLSB(deviceId.getId().getLeastSignificantBits())
                    .build();
            DownlinkMsg.Builder builder = DownlinkMsg.newBuilder()
                    .addAllDeviceCredentialsRequestMsg(Collections.singletonList(deviceCredentialsRequestMsg));
            downlinkMsg = builder.build();
        }
        return downlinkMsg;
    }


    private ListenableFuture<Long> getQueueStartTs() {
        ListenableFuture<Optional<AttributeKvEntry>> future =
                ctx.getAttributesService().find(edge.getTenantId(), edge.getId(), DataConstants.SERVER_SCOPE, QUEUE_START_TS_ATTR_KEY);
        return Futures.transform(future, attributeKvEntryOpt -> {
            if (attributeKvEntryOpt != null && attributeKvEntryOpt.isPresent()) {
                AttributeKvEntry attributeKvEntry = attributeKvEntryOpt.get();
                return attributeKvEntry.getLongValue().isPresent() ? attributeKvEntry.getLongValue().get() : 0L;
            } else {
                return 0L;
            }
        }, ctx.getDbCallbackExecutor());
    }

    private void updateQueueStartTs(Long newStartTs) {
        newStartTs = ++newStartTs; // increments ts by 1 - next edge event search starts from current offset + 1
        List<AttributeKvEntry> attributes = Collections.singletonList(new BaseAttributeKvEntry(new LongDataEntry(QUEUE_START_TS_ATTR_KEY, newStartTs), System.currentTimeMillis()));
        ctx.getAttributesService().save(edge.getTenantId(), edge.getId(), DataConstants.SERVER_SCOPE, attributes);
    }

    private DownlinkMsg processTelemetryMessage(EdgeEvent edgeEvent) {
        log.trace("Executing processTelemetryMessage, edgeEvent [{}]", edgeEvent);
        EntityId entityId = null;
        switch (edgeEvent.getType()) {
            case DEVICE:
                entityId = new DeviceId(edgeEvent.getEntityId());
                break;
            case ASSET:
                entityId = new AssetId(edgeEvent.getEntityId());
                break;
            case ENTITY_VIEW:
                entityId = new EntityViewId(edgeEvent.getEntityId());
                break;
            case DASHBOARD:
                entityId = new DashboardId(edgeEvent.getEntityId());
                break;
            case TENANT:
                entityId = new TenantId(edgeEvent.getEntityId());
                break;
            case CUSTOMER:
                entityId = new CustomerId(edgeEvent.getEntityId());
                break;
            case ENTITY_GROUP:
                entityId = new EntityGroupId(edgeEvent.getEntityId());
                break;
        }
        DownlinkMsg downlinkMsg = null;
        if (entityId != null) {
            log.debug("Sending telemetry data msg, entityId [{}], body [{}]", edgeEvent.getEntityId(), edgeEvent.getBody());
            try {
                ActionType actionType = ActionType.valueOf(edgeEvent.getAction());
                downlinkMsg = constructEntityDataProtoMsg(entityId, actionType, JsonUtils.parse(mapper.writeValueAsString(edgeEvent.getBody())));
            } catch (Exception e) {
                log.warn("Can't send telemetry data msg, entityId [{}], body [{}]", edgeEvent.getEntityId(), edgeEvent.getBody(), e);
            }
        }
        return downlinkMsg;
    }

    private DownlinkMsg processEntityMessage(EdgeEvent edgeEvent, ActionType action) {
        UpdateMsgType msgType = getResponseMsgType(ActionType.valueOf(edgeEvent.getAction()));
        log.trace("Executing processEntityMessage, edgeEvent [{}], action [{}], msgType [{}]", edgeEvent, action, msgType);
        switch (edgeEvent.getType()) {
            case EDGE:
                // TODO: voba - add edge update logic
                return null;
            case DEVICE:
                return processDevice(edgeEvent, msgType, action);
            case ASSET:
                return processAsset(edgeEvent, msgType, action);
            case ENTITY_VIEW:
                return processEntityView(edgeEvent, msgType, action);
            case DASHBOARD:
                return processDashboard(edgeEvent, msgType, action);
            case CUSTOMER:
                return processCustomer(edgeEvent, msgType, action);
            case RULE_CHAIN:
                return processRuleChain(edgeEvent, msgType, action);
            case RULE_CHAIN_METADATA:
                return processRuleChainMetadata(edgeEvent, msgType);
            case ALARM:
                return processAlarm(edgeEvent, msgType);
            case USER:
                return processUser(edgeEvent, msgType, action);
            case RELATION:
                return processRelation(edgeEvent, msgType);
            case WIDGETS_BUNDLE:
                return processWidgetsBundle(edgeEvent, msgType, action);
            case WIDGET_TYPE:
                return processWidgetType(edgeEvent, msgType, action);
            case ADMIN_SETTINGS:
                return processAdminSettings(edgeEvent);
            case SCHEDULER_EVENT:
                return processSchedulerEvent(edgeEvent, msgType);
            case ENTITY_GROUP:
                return processEntityGroup(edgeEvent, msgType);
            case WHITE_LABELING:
                return processWhiteLabeling(edgeEvent);
            case LOGIN_WHITE_LABELING:
                return processLoginWhiteLabeling(edgeEvent);
            case CUSTOM_TRANSLATION:
                return processCustomTranslation(edgeEvent);
            case ROLE:
                return processRole(edgeEvent, msgType);
            case GROUP_PERMISSION:
                return processGroupPermission(edgeEvent, msgType);
            default:
                log.warn("Unsupported edge event type [{}]", edgeEvent);
                return null;
        }
    }

    private DownlinkMsg processDevice(EdgeEvent edgeEvent, UpdateMsgType msgType, ActionType edgeActionType) {
        DeviceId deviceId = new DeviceId(edgeEvent.getEntityId());
        DownlinkMsg downlinkMsg = null;
        switch (edgeActionType) {
            case ADDED:
            case ADDED_TO_ENTITY_GROUP:
            case UPDATED:
            case ASSIGNED_TO_EDGE:
                Device device = ctx.getDeviceService().findDeviceById(edgeEvent.getTenantId(), deviceId);
                if (device != null) {
                    EntityGroupId entityGroupId = edgeEvent.getEntityGroupId() != null ? new EntityGroupId(edgeEvent.getEntityGroupId()) : null;
                    DeviceUpdateMsg deviceUpdateMsg =
                            ctx.getDeviceMsgConstructor().constructDeviceUpdatedMsg(msgType, device, entityGroupId);
                    downlinkMsg = DownlinkMsg.newBuilder()
                            .addAllDeviceUpdateMsg(Collections.singletonList(deviceUpdateMsg))
                            .build();
                }
                break;
            case DELETED:
            case REMOVED_FROM_ENTITY_GROUP:
            case UNASSIGNED_FROM_EDGE:
                DeviceUpdateMsg deviceUpdateMsg =
                        ctx.getDeviceMsgConstructor().constructDeviceDeleteMsg(deviceId);
                downlinkMsg = DownlinkMsg.newBuilder()
                        .addAllDeviceUpdateMsg(Collections.singletonList(deviceUpdateMsg))
                        .build();
                break;
            case CREDENTIALS_UPDATED:
                DeviceCredentials deviceCredentials = ctx.getDeviceCredentialsService().findDeviceCredentialsByDeviceId(edge.getTenantId(), deviceId);
                if (deviceCredentials != null) {
                    DeviceCredentialsUpdateMsg deviceCredentialsUpdateMsg =
                            ctx.getDeviceMsgConstructor().constructDeviceCredentialsUpdatedMsg(deviceCredentials);
                    downlinkMsg = DownlinkMsg.newBuilder()
                            .addAllDeviceCredentialsUpdateMsg(Collections.singletonList(deviceCredentialsUpdateMsg))
                            .build();
                }
                break;
        }
        return downlinkMsg;
    }

    private DownlinkMsg processAsset(EdgeEvent edgeEvent, UpdateMsgType msgType, ActionType action) {
        AssetId assetId = new AssetId(edgeEvent.getEntityId());
        DownlinkMsg downlinkMsg = null;
        switch (action) {
            case ADDED:
            case ADDED_TO_ENTITY_GROUP:
            case UPDATED:
            case ASSIGNED_TO_EDGE:
                Asset asset = ctx.getAssetService().findAssetById(edgeEvent.getTenantId(), assetId);
                if (asset != null) {
                    EntityGroupId entityGroupId = edgeEvent.getEntityGroupId() != null ? new EntityGroupId(edgeEvent.getEntityGroupId()) : null;
                    AssetUpdateMsg assetUpdateMsg =
                            ctx.getAssetMsgConstructor().constructAssetUpdatedMsg(msgType, asset, entityGroupId);
                    downlinkMsg = DownlinkMsg.newBuilder()
                            .addAllAssetUpdateMsg(Collections.singletonList(assetUpdateMsg))
                            .build();
                }
                break;
            case DELETED:
            case REMOVED_FROM_ENTITY_GROUP:
            case UNASSIGNED_FROM_EDGE:
                AssetUpdateMsg assetUpdateMsg =
                        ctx.getAssetMsgConstructor().constructAssetDeleteMsg(assetId);
                downlinkMsg = DownlinkMsg.newBuilder()
                        .addAllAssetUpdateMsg(Collections.singletonList(assetUpdateMsg))
                        .build();
                break;
        }
        return downlinkMsg;
    }

    private DownlinkMsg processEntityView(EdgeEvent edgeEvent, UpdateMsgType msgType, ActionType action) {
        EntityViewId entityViewId = new EntityViewId(edgeEvent.getEntityId());
        DownlinkMsg downlinkMsg = null;
        switch (action) {
            case ADDED:
            case ADDED_TO_ENTITY_GROUP:
            case UPDATED:
            case ASSIGNED_TO_EDGE:
                EntityView entityView = ctx.getEntityViewService().findEntityViewById(edgeEvent.getTenantId(), entityViewId);
                if (entityView != null) {
                    EntityGroupId entityGroupId = edgeEvent.getEntityGroupId() != null ? new EntityGroupId(edgeEvent.getEntityGroupId()) : null;
                    EntityViewUpdateMsg entityViewUpdateMsg =
                            ctx.getEntityViewMsgConstructor().constructEntityViewUpdatedMsg(msgType, entityView, entityGroupId);
                    downlinkMsg = DownlinkMsg.newBuilder()
                            .addAllEntityViewUpdateMsg(Collections.singletonList(entityViewUpdateMsg))
                            .build();
                }
                break;
            case DELETED:
            case REMOVED_FROM_ENTITY_GROUP:
            case UNASSIGNED_FROM_EDGE:
                EntityViewUpdateMsg entityViewUpdateMsg =
                        ctx.getEntityViewMsgConstructor().constructEntityViewDeleteMsg(entityViewId);
                downlinkMsg = DownlinkMsg.newBuilder()
                        .addAllEntityViewUpdateMsg(Collections.singletonList(entityViewUpdateMsg))
                        .build();
                break;
        }
        return downlinkMsg;
    }

    private DownlinkMsg processDashboard(EdgeEvent edgeEvent, UpdateMsgType msgType, ActionType action) {
        DashboardId dashboardId = new DashboardId(edgeEvent.getEntityId());
        DownlinkMsg downlinkMsg = null;
        switch (action) {
            case ADDED:
            case ADDED_TO_ENTITY_GROUP:
            case UPDATED:
            case ASSIGNED_TO_EDGE:
                Dashboard dashboard = ctx.getDashboardService().findDashboardById(edgeEvent.getTenantId(), dashboardId);
                if (dashboard != null) {
                    EntityGroupId entityGroupId = edgeEvent.getEntityGroupId() != null ? new EntityGroupId(edgeEvent.getEntityGroupId()) : null;
                    DashboardUpdateMsg dashboardUpdateMsg =
                            ctx.getDashboardMsgConstructor().constructDashboardUpdatedMsg(msgType, dashboard, entityGroupId);
                    downlinkMsg = DownlinkMsg.newBuilder()
                            .addAllDashboardUpdateMsg(Collections.singletonList(dashboardUpdateMsg))
                            .build();
                }
                break;
            case DELETED:
            case REMOVED_FROM_ENTITY_GROUP:
            case UNASSIGNED_FROM_EDGE:
                DashboardUpdateMsg dashboardUpdateMsg =
                        ctx.getDashboardMsgConstructor().constructDashboardDeleteMsg(dashboardId);
                downlinkMsg = DownlinkMsg.newBuilder()
                        .addAllDashboardUpdateMsg(Collections.singletonList(dashboardUpdateMsg))
                        .build();
                break;
        }
        return downlinkMsg;
    }

    private DownlinkMsg processCustomer(EdgeEvent edgeEvent, UpdateMsgType msgType, ActionType action) {
        CustomerId customerId = new CustomerId(edgeEvent.getEntityId());
        DownlinkMsg downlinkMsg = null;
        switch (action) {
            case ADDED:
            case UPDATED:
                Customer customer = ctx.getCustomerService().findCustomerById(edgeEvent.getTenantId(), customerId);
                if (customer != null) {
                    CustomerUpdateMsg customerUpdateMsg =
                            ctx.getCustomerMsgConstructor().constructCustomerUpdatedMsg(msgType, customer);
                    downlinkMsg = DownlinkMsg.newBuilder()
                            .addAllCustomerUpdateMsg(Collections.singletonList(customerUpdateMsg))
                            .build();
                }
                break;
            case DELETED:
                CustomerUpdateMsg customerUpdateMsg =
                        ctx.getCustomerMsgConstructor().constructCustomerDeleteMsg(customerId);
                downlinkMsg = DownlinkMsg.newBuilder()
                        .addAllCustomerUpdateMsg(Collections.singletonList(customerUpdateMsg))
                        .build();
                break;
        }
        return downlinkMsg;
    }

    private DownlinkMsg processRuleChain(EdgeEvent edgeEvent, UpdateMsgType msgType, ActionType action) {
        RuleChainId ruleChainId = new RuleChainId(edgeEvent.getEntityId());
        DownlinkMsg downlinkMsg = null;
        switch (action) {
            case ADDED:
            case UPDATED:
            case ASSIGNED_TO_EDGE:
                RuleChain ruleChain = ctx.getRuleChainService().findRuleChainById(edgeEvent.getTenantId(), ruleChainId);
                if (ruleChain != null) {
                    RuleChainUpdateMsg ruleChainUpdateMsg =
                            ctx.getRuleChainMsgConstructor().constructRuleChainUpdatedMsg(edge.getRootRuleChainId(), msgType, ruleChain);
                    downlinkMsg = DownlinkMsg.newBuilder()
                            .addAllRuleChainUpdateMsg(Collections.singletonList(ruleChainUpdateMsg))
                            .build();
                }
                break;
            case DELETED:
            case UNASSIGNED_FROM_EDGE:
                downlinkMsg = DownlinkMsg.newBuilder()
                        .addAllRuleChainUpdateMsg(Collections.singletonList(ctx.getRuleChainMsgConstructor().constructRuleChainDeleteMsg(ruleChainId)))
                        .build();
                break;
        }
        return downlinkMsg;
    }

    private DownlinkMsg processRuleChainMetadata(EdgeEvent edgeEvent, UpdateMsgType msgType) {
        RuleChainId ruleChainId = new RuleChainId(edgeEvent.getEntityId());
        RuleChain ruleChain = ctx.getRuleChainService().findRuleChainById(edgeEvent.getTenantId(), ruleChainId);
        DownlinkMsg downlinkMsg = null;
        if (ruleChain != null) {
            RuleChainMetaData ruleChainMetaData = ctx.getRuleChainService().loadRuleChainMetaData(edgeEvent.getTenantId(), ruleChainId);
            RuleChainMetadataUpdateMsg ruleChainMetadataUpdateMsg =
                    ctx.getRuleChainMsgConstructor().constructRuleChainMetadataUpdatedMsg(msgType, ruleChainMetaData);
            if (ruleChainMetadataUpdateMsg != null) {
                downlinkMsg = DownlinkMsg.newBuilder()
                        .addAllRuleChainMetadataUpdateMsg(Collections.singletonList(ruleChainMetadataUpdateMsg))
                        .build();
            }
        }
        return downlinkMsg;
    }

    private DownlinkMsg processUser(EdgeEvent edgeEvent, UpdateMsgType msgType, ActionType edgeActionType) {
        UserId userId = new UserId(edgeEvent.getEntityId());
        DownlinkMsg downlinkMsg = null;
        switch (edgeActionType) {
            case ADDED:
            case ADDED_TO_ENTITY_GROUP:
            case UPDATED:
            case ASSIGNED_TO_EDGE:
                User user = ctx.getUserService().findUserById(edgeEvent.getTenantId(), userId);
                if (user != null) {
                    EntityGroupId entityGroupId = edgeEvent.getEntityGroupId() != null ? new EntityGroupId(edgeEvent.getEntityGroupId()) : null;
                    downlinkMsg = DownlinkMsg.newBuilder()
                            .addAllUserUpdateMsg(Collections.singletonList(ctx.getUserMsgConstructor().constructUserUpdatedMsg(msgType, user, entityGroupId)))
                            .build();
                }
                break;
            case DELETED:
            case REMOVED_FROM_ENTITY_GROUP:
            case UNASSIGNED_FROM_EDGE:
                downlinkMsg = DownlinkMsg.newBuilder()
                        .addAllUserUpdateMsg(Collections.singletonList(ctx.getUserMsgConstructor().constructUserDeleteMsg(userId)))
                        .build();
                break;
            case CREDENTIALS_UPDATED:
                UserCredentials userCredentialsByUserId = ctx.getUserService().findUserCredentialsByUserId(edge.getTenantId(), userId);
                if (userCredentialsByUserId != null && userCredentialsByUserId.isEnabled()) {
                    UserCredentialsUpdateMsg userCredentialsUpdateMsg =
                            ctx.getUserMsgConstructor().constructUserCredentialsUpdatedMsg(userCredentialsByUserId);
                    downlinkMsg = DownlinkMsg.newBuilder()
                            .addAllUserCredentialsUpdateMsg(Collections.singletonList(userCredentialsUpdateMsg))
                            .build();
                }
        }
        return downlinkMsg;
    }

    private DownlinkMsg processRelation(EdgeEvent edgeEvent, UpdateMsgType msgType) {
        EntityRelation entityRelation = mapper.convertValue(edgeEvent.getBody(), EntityRelation.class);
        RelationUpdateMsg r = ctx.getRelationMsgConstructor().constructRelationUpdatedMsg(msgType, entityRelation);
        return DownlinkMsg.newBuilder()
                .addAllRelationUpdateMsg(Collections.singletonList(r))
                .build();
    }

    private DownlinkMsg processAlarm(EdgeEvent edgeEvent, UpdateMsgType msgType) {
        DownlinkMsg downlinkMsg = null;
        try {
            AlarmId alarmId = new AlarmId(edgeEvent.getEntityId());
            Alarm alarm = ctx.getAlarmService().findAlarmByIdAsync(edgeEvent.getTenantId(), alarmId).get();
            if (alarm != null) {
                downlinkMsg = DownlinkMsg.newBuilder()
                        .addAllAlarmUpdateMsg(Collections.singletonList(ctx.getAlarmMsgConstructor().constructAlarmUpdatedMsg(edge.getTenantId(), msgType, alarm)))
                        .build();
            }
        } catch (Exception e) {
            log.error("Can't process alarm msg [{}] [{}]", edgeEvent, msgType, e);
        }
        return downlinkMsg;
    }

    private DownlinkMsg processWidgetsBundle(EdgeEvent edgeEvent, UpdateMsgType msgType, ActionType edgeActionType) {
        WidgetsBundleId widgetsBundleId = new WidgetsBundleId(edgeEvent.getEntityId());
        DownlinkMsg downlinkMsg = null;
        switch (edgeActionType) {
            case ADDED:
            case UPDATED:
                WidgetsBundle widgetsBundle = ctx.getWidgetsBundleService().findWidgetsBundleById(edgeEvent.getTenantId(), widgetsBundleId);
                if (widgetsBundle != null) {
                    WidgetsBundleUpdateMsg widgetsBundleUpdateMsg =
                            ctx.getWidgetsBundleMsgConstructor().constructWidgetsBundleUpdateMsg(msgType, widgetsBundle);
                    downlinkMsg = DownlinkMsg.newBuilder()
                            .addAllWidgetsBundleUpdateMsg(Collections.singletonList(widgetsBundleUpdateMsg))
                            .build();
                }
                break;
            case DELETED:
                WidgetsBundleUpdateMsg widgetsBundleUpdateMsg =
                        ctx.getWidgetsBundleMsgConstructor().constructWidgetsBundleDeleteMsg(widgetsBundleId);
                downlinkMsg = DownlinkMsg.newBuilder()
                        .addAllWidgetsBundleUpdateMsg(Collections.singletonList(widgetsBundleUpdateMsg))
                        .build();
                break;
        }
        return downlinkMsg;
    }

    private DownlinkMsg processWidgetType(EdgeEvent edgeEvent, UpdateMsgType msgType, ActionType edgeActionType) {
        WidgetTypeId widgetTypeId = new WidgetTypeId(edgeEvent.getEntityId());
        DownlinkMsg downlinkMsg = null;
        switch (edgeActionType) {
            case ADDED:
            case UPDATED:
                WidgetType widgetType = ctx.getWidgetTypeService().findWidgetTypeById(edgeEvent.getTenantId(), widgetTypeId);
                if (widgetType != null) {
                    WidgetTypeUpdateMsg widgetTypeUpdateMsg =
                            ctx.getWidgetTypeMsgConstructor().constructWidgetTypeUpdateMsg(msgType, widgetType);
                    downlinkMsg = DownlinkMsg.newBuilder()
                            .addAllWidgetTypeUpdateMsg(Collections.singletonList(widgetTypeUpdateMsg))
                            .build();
                }
                break;
            case DELETED:
                WidgetTypeUpdateMsg widgetTypeUpdateMsg =
                        ctx.getWidgetTypeMsgConstructor().constructWidgetTypeDeleteMsg(widgetTypeId);
                downlinkMsg = DownlinkMsg.newBuilder()
                        .addAllWidgetTypeUpdateMsg(Collections.singletonList(widgetTypeUpdateMsg))
                        .build();
                break;
        }
        return downlinkMsg;
    }

    private DownlinkMsg processSchedulerEvent(EdgeEvent edgeEvent, UpdateMsgType msgType) {
        SchedulerEventId schedulerEventId = new SchedulerEventId(edgeEvent.getEntityId());
        DownlinkMsg downlinkMsg = null;
        switch (msgType) {
            case ENTITY_CREATED_RPC_MESSAGE:
            case ENTITY_UPDATED_RPC_MESSAGE:
                SchedulerEvent schedulerEvent = ctx.getSchedulerEventService().findSchedulerEventById(edgeEvent.getTenantId(), schedulerEventId);
                if (schedulerEvent != null) {
                    downlinkMsg = DownlinkMsg.newBuilder()
                            .addAllSchedulerEventUpdateMsg(Collections.singletonList(ctx.getSchedulerEventMsgConstructor().constructSchedulerEventUpdatedMsg(msgType, schedulerEvent)))
                            .build();
                }
                break;
            case ENTITY_DELETED_RPC_MESSAGE:
                downlinkMsg = DownlinkMsg.newBuilder()
                        .addAllSchedulerEventUpdateMsg(Collections.singletonList(ctx.getSchedulerEventMsgConstructor().constructEventDeleteMsg(schedulerEventId)))
                        .build();
                break;
        }
        return downlinkMsg;
    }

    private DownlinkMsg processEntityGroup(EdgeEvent edgeEvent, UpdateMsgType msgType) {
        EntityGroupId entityGroupId = new EntityGroupId(edgeEvent.getEntityId());
        DownlinkMsg downlinkMsg = null;
        switch (msgType) {
            case ENTITY_CREATED_RPC_MESSAGE:
            case ENTITY_UPDATED_RPC_MESSAGE:
                EntityGroup entityGroup = ctx.getEntityGroupService().findEntityGroupById(edgeEvent.getTenantId(), entityGroupId);
                if (entityGroup != null) {
                    downlinkMsg = DownlinkMsg.newBuilder()
                            .addAllEntityGroupUpdateMsg(Collections.singletonList(ctx.getEntityGroupMsgConstructor().constructEntityGroupUpdatedMsg(msgType, entityGroup)))
                            .build();
                }
                break;
            case ENTITY_DELETED_RPC_MESSAGE:
                downlinkMsg = DownlinkMsg.newBuilder()
                        .addAllEntityGroupUpdateMsg(Collections.singletonList(ctx.getEntityGroupMsgConstructor().constructEntityGroupDeleteMsg(entityGroupId)))
                        .build();
                break;
        }
        return downlinkMsg;
    }

    private DownlinkMsg processWhiteLabeling(EdgeEvent edgeEvent) {
        WhiteLabelingParams whiteLabelingParams = mapper.convertValue(edgeEvent.getBody(), WhiteLabelingParams.class);
        WhiteLabelingParamsProto whiteLabelingParamsProto =
                ctx.getWhiteLabelingParamsProtoConstructor().constructWhiteLabelingParamsProto(whiteLabelingParams);
        return DownlinkMsg.newBuilder()
                .addAllWhiteLabelingParams(Collections.singletonList(whiteLabelingParamsProto))
                .build();
    }

    private DownlinkMsg processLoginWhiteLabeling(EdgeEvent edgeEvent) {
        LoginWhiteLabelingParams loginWhiteLabelingParams = mapper.convertValue(edgeEvent.getBody(), LoginWhiteLabelingParams.class);
        LoginWhiteLabelingParamsProto loginWhiteLabelingParamsProto =
                ctx.getWhiteLabelingParamsProtoConstructor().constructLoginWhiteLabelingParamsProto(loginWhiteLabelingParams);
        return DownlinkMsg.newBuilder()
                .addAllLoginWhiteLabelingParams(Collections.singletonList(loginWhiteLabelingParamsProto))
                .build();
    }

    private DownlinkMsg processCustomTranslation(EdgeEvent edgeEvent) {
        CustomTranslation customTranslation = mapper.convertValue(edgeEvent.getBody(), CustomTranslation.class);
        CustomTranslationProto customTranslationProto =
                ctx.getCustomTranslationProtoConstructor().constructCustomTranslationProto(customTranslation);
        return DownlinkMsg.newBuilder()
                .addAllCustomTranslationMsg(Collections.singletonList(customTranslationProto))
                .build();
    }

    private DownlinkMsg processRole(EdgeEvent edgeEvent, UpdateMsgType msgType) {
        RoleId roleId = new RoleId(edgeEvent.getEntityId());
        DownlinkMsg downlinkMsg = null;
        switch (msgType) {
            case ENTITY_CREATED_RPC_MESSAGE:
            case ENTITY_UPDATED_RPC_MESSAGE:
                Role role = ctx.getRoleService().findRoleById(edgeEvent.getTenantId(), roleId);
                if (role != null) {
                    downlinkMsg = DownlinkMsg.newBuilder()
                            .addAllRoleMsg(Collections.singletonList(ctx.getRoleProtoConstructor().constructRoleProto(msgType, role)))
                            .build();
                }
                break;
            case ENTITY_DELETED_RPC_MESSAGE:
                downlinkMsg = DownlinkMsg.newBuilder()
                        .addAllRoleMsg(Collections.singletonList(ctx.getRoleProtoConstructor().constructRoleDeleteMsg(roleId)))
                        .build();
                break;
        }
        return downlinkMsg;
    }

    private DownlinkMsg processGroupPermission(EdgeEvent edgeEvent, UpdateMsgType msgType) {
        GroupPermissionId groupPermissionId = new GroupPermissionId(edgeEvent.getEntityId());
        DownlinkMsg downlinkMsg = null;
        switch (msgType) {
            case ENTITY_CREATED_RPC_MESSAGE:
            case ENTITY_UPDATED_RPC_MESSAGE:
                GroupPermission groupPermission = ctx.getGroupPermissionService().findGroupPermissionById(edgeEvent.getTenantId(), groupPermissionId);
                if (groupPermission != null) {
                    downlinkMsg = DownlinkMsg.newBuilder()
                            .addAllGroupPermissionMsg(
                                    Collections.singletonList(
                                            ctx.getGroupPermissionProtoConstructor().constructGroupPermissionProto(msgType, groupPermission)))
                            .build();
                }
                break;
            case ENTITY_DELETED_RPC_MESSAGE:
                downlinkMsg = DownlinkMsg.newBuilder()
                        .addAllGroupPermissionMsg(
                                Collections.singletonList(
                                        ctx.getGroupPermissionProtoConstructor().constructGroupPermissionDeleteMsg(groupPermissionId)))
                        .build();
                break;
        }
        return downlinkMsg;
    }

    private DownlinkMsg processAdminSettings(EdgeEvent edgeEvent) {
        AdminSettings adminSettings = mapper.convertValue(edgeEvent.getBody(), AdminSettings.class);
        AdminSettingsUpdateMsg t = ctx.getAdminSettingsMsgConstructor().constructAdminSettingsUpdateMsg(adminSettings);
        return DownlinkMsg.newBuilder()
                .addAllAdminSettingsUpdateMsg(Collections.singletonList(t))
                .build();
    }

    private UpdateMsgType getResponseMsgType(ActionType actionType) {
        switch (actionType) {
            case UPDATED:
            case CREDENTIALS_UPDATED:
            case ASSIGNED_TO_CUSTOMER:
            case UNASSIGNED_FROM_CUSTOMER:
                return UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE;
            case ADDED:
            case ADDED_TO_ENTITY_GROUP:
            case ASSIGNED_TO_EDGE:
            case RELATION_ADD_OR_UPDATE:
                return UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE;
            case DELETED:
            case UNASSIGNED_FROM_EDGE:
            case RELATION_DELETED:
            case REMOVED_FROM_ENTITY_GROUP:
                return UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE;
            case ALARM_ACK:
                return UpdateMsgType.ALARM_ACK_RPC_MESSAGE;
            case ALARM_CLEAR:
                return UpdateMsgType.ALARM_CLEAR_RPC_MESSAGE;
            default:
                throw new RuntimeException("Unsupported actionType [" + actionType + "]");
        }
    }

    private DownlinkMsg constructEntityDataProtoMsg(EntityId entityId, ActionType actionType, JsonElement entityData) {
        EntityDataProto entityDataProto = ctx.getEntityDataMsgConstructor().constructEntityDataMsg(entityId, actionType, entityData);
        DownlinkMsg.Builder builder = DownlinkMsg.newBuilder()
                .addAllEntityData(Collections.singletonList(entityDataProto));
        return builder.build();
    }

    private ListenableFuture<List<Void>> processUplinkMsg(UplinkMsg uplinkMsg) {
        List<ListenableFuture<Void>> result = new ArrayList<>();
        try {
            if (uplinkMsg.getEntityDataList() != null && !uplinkMsg.getEntityDataList().isEmpty()) {
                for (EntityDataProto entityData : uplinkMsg.getEntityDataList()) {
                    result.addAll(ctx.getTelemetryProcessor().onTelemetryUpdate(edge.getTenantId(), entityData));
                }
            }

            if (uplinkMsg.getDeviceUpdateMsgList() != null && !uplinkMsg.getDeviceUpdateMsgList().isEmpty()) {
                for (DeviceUpdateMsg deviceUpdateMsg : uplinkMsg.getDeviceUpdateMsgList()) {
                    result.add(ctx.getDeviceProcessor().onDeviceUpdate(edge.getTenantId(), edge, deviceUpdateMsg));
                }
            }
            if (uplinkMsg.getDeviceCredentialsUpdateMsgList() != null && !uplinkMsg.getDeviceCredentialsUpdateMsgList().isEmpty()) {
                for (DeviceCredentialsUpdateMsg deviceCredentialsUpdateMsg : uplinkMsg.getDeviceCredentialsUpdateMsgList()) {
                    result.add(ctx.getDeviceProcessor().onDeviceCredentialsUpdate(edge.getTenantId(), deviceCredentialsUpdateMsg));
                }
            }
            if (uplinkMsg.getAlarmUpdateMsgList() != null && !uplinkMsg.getAlarmUpdateMsgList().isEmpty()) {
                for (AlarmUpdateMsg alarmUpdateMsg : uplinkMsg.getAlarmUpdateMsgList()) {
                    result.add(ctx.getAlarmProcessor().onAlarmUpdate(edge.getTenantId(), alarmUpdateMsg));
                }
            }
            if (uplinkMsg.getRelationUpdateMsgList() != null && !uplinkMsg.getRelationUpdateMsgList().isEmpty()) {
                for (RelationUpdateMsg relationUpdateMsg : uplinkMsg.getRelationUpdateMsgList()) {
                    result.add(ctx.getRelationProcessor().onRelationUpdate(edge.getTenantId(), relationUpdateMsg));
                }
            }
            if (uplinkMsg.getRuleChainMetadataRequestMsgList() != null && !uplinkMsg.getRuleChainMetadataRequestMsgList().isEmpty()) {
                for (RuleChainMetadataRequestMsg ruleChainMetadataRequestMsg : uplinkMsg.getRuleChainMetadataRequestMsgList()) {
                    result.add(ctx.getSyncEdgeService().processRuleChainMetadataRequestMsg(edge, ruleChainMetadataRequestMsg));
                }
            }
            if (uplinkMsg.getAttributesRequestMsgList() != null && !uplinkMsg.getAttributesRequestMsgList().isEmpty()) {
                for (AttributesRequestMsg attributesRequestMsg : uplinkMsg.getAttributesRequestMsgList()) {
                    result.add(ctx.getSyncEdgeService().processAttributesRequestMsg(edge, attributesRequestMsg));
                }
            }
            if (uplinkMsg.getRelationRequestMsgList() != null && !uplinkMsg.getRelationRequestMsgList().isEmpty()) {
                for (RelationRequestMsg relationRequestMsg : uplinkMsg.getRelationRequestMsgList()) {
                    result.add(ctx.getSyncEdgeService().processRelationRequestMsg(edge, relationRequestMsg));
                }
            }
            if (uplinkMsg.getUserCredentialsRequestMsgList() != null && !uplinkMsg.getUserCredentialsRequestMsgList().isEmpty()) {
                for (UserCredentialsRequestMsg userCredentialsRequestMsg : uplinkMsg.getUserCredentialsRequestMsgList()) {
                    result.add(ctx.getSyncEdgeService().processUserCredentialsRequestMsg(edge, userCredentialsRequestMsg));
                }
            }
            if (uplinkMsg.getDeviceCredentialsRequestMsgList() != null && !uplinkMsg.getDeviceCredentialsRequestMsgList().isEmpty()) {
                for (DeviceCredentialsRequestMsg deviceCredentialsRequestMsg : uplinkMsg.getDeviceCredentialsRequestMsgList()) {
                    result.add(ctx.getSyncEdgeService().processDeviceCredentialsRequestMsg(edge, deviceCredentialsRequestMsg));
                }
            }
            if (uplinkMsg.getDeviceRpcCallMsgList() != null && !uplinkMsg.getDeviceRpcCallMsgList().isEmpty()) {
                for (DeviceRpcCallMsg deviceRpcCallMsg : uplinkMsg.getDeviceRpcCallMsgList()) {
                    result.add(ctx.getDeviceProcessor().processDeviceRpcCallResponseMsg(edge.getTenantId(), deviceRpcCallMsg));
                }
            }
            if (uplinkMsg.getEntityGroupEntitiesRequestMsgList() != null && !uplinkMsg.getEntityGroupEntitiesRequestMsgList().isEmpty()) {
                for (EntityGroupRequestMsg entityGroupEntitiesRequestMsg : uplinkMsg.getEntityGroupEntitiesRequestMsgList()) {
                    result.add(ctx.getSyncEdgeService().processEntityGroupEntitiesRequest(edge, entityGroupEntitiesRequestMsg));
                }
            }
            if (uplinkMsg.getEntityGroupPermissionsRequestMsgList() != null && !uplinkMsg.getEntityGroupPermissionsRequestMsgList().isEmpty()) {
                for (EntityGroupRequestMsg userGroupPermissionsRequestMsg : uplinkMsg.getEntityGroupPermissionsRequestMsgList()) {
                    result.add(ctx.getSyncEdgeService().processEntityGroupPermissionsRequest(edge, userGroupPermissionsRequestMsg));
                }
            }
        } catch (Exception e) {
            log.error("Can't process uplink msg [{}]", uplinkMsg, e);
        }
        return Futures.allAsList(result);
    }

    private ConnectResponseMsg processConnect(ConnectRequestMsg request) {
        Optional<Edge> optional = ctx.getEdgeService().findEdgeByRoutingKey(TenantId.SYS_TENANT_ID, request.getEdgeRoutingKey());
        if (optional.isPresent()) {
            edge = optional.get();
            try {
                if (edge.getSecret().equals(request.getEdgeSecret())) {
                    sessionOpenListener.accept(edge.getId(), this);
                    return ConnectResponseMsg.newBuilder()
                            .setResponseCode(ConnectResponseCode.ACCEPTED)
                            .setErrorMsg("")
                            .setConfiguration(constructEdgeConfigProto(edge)).build();
                }
                return ConnectResponseMsg.newBuilder()
                        .setResponseCode(ConnectResponseCode.BAD_CREDENTIALS)
                        .setErrorMsg("Failed to validate the edge!")
                        .setConfiguration(EdgeConfiguration.getDefaultInstance()).build();
            } catch (Exception e) {
                log.error("[{}] Failed to process edge connection!", request.getEdgeRoutingKey(), e);
                return ConnectResponseMsg.newBuilder()
                        .setResponseCode(ConnectResponseCode.SERVER_UNAVAILABLE)
                        .setErrorMsg("Failed to process edge connection!")
                        .setConfiguration(EdgeConfiguration.getDefaultInstance()).build();
            }
        }
        return ConnectResponseMsg.newBuilder()
                .setResponseCode(ConnectResponseCode.BAD_CREDENTIALS)
                .setErrorMsg("Failed to find the edge! Routing key: " + request.getEdgeRoutingKey())
                .setConfiguration(EdgeConfiguration.getDefaultInstance()).build();
    }

    private EdgeConfiguration constructEdgeConfigProto(Edge edge) throws JsonProcessingException {
        return EdgeConfiguration.newBuilder()
                .setEdgeIdMSB(edge.getId().getId().getMostSignificantBits())
                .setEdgeIdLSB(edge.getId().getId().getLeastSignificantBits())
                .setTenantIdMSB(edge.getTenantId().getId().getMostSignificantBits())
                .setTenantIdLSB(edge.getTenantId().getId().getLeastSignificantBits())
                .setName(edge.getName())
                .setRoutingKey(edge.getRoutingKey())
                .setType(edge.getType())
                .setEdgeLicenseKey(edge.getEdgeLicenseKey())
                .setCloudEndpoint(edge.getCloudEndpoint())
                .setCloudType("PE")
                .build();
    }

    @Override
    public void close() {
        connected = false;
        try {
            outputStream.onCompleted();
        } catch (Exception e) {
            log.debug("[{}] Failed to close output stream: {}", sessionId, e.getMessage());
        }
    }
}
