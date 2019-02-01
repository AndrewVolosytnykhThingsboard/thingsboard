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
package org.thingsboard.server.common.transport.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.msg.tools.TbRateLimits;
import org.thingsboard.server.common.msg.tools.TbRateLimitsException;
import org.thingsboard.server.common.transport.SessionMsgListener;
import org.thingsboard.server.common.transport.TransportService;
import org.thingsboard.server.common.transport.TransportServiceCallback;
import org.thingsboard.server.gen.transport.TransportProtos;

import java.util.UUID;
import java.util.concurrent.*;

/**
 * Created by ashvayka on 17.10.18.
 */
@Slf4j
public abstract class AbstractTransportService implements TransportService {

    @Value("${transport.rate_limits.enabled}")
    private boolean rateLimitEnabled;
    @Value("${transport.rate_limits.tenant}")
    private String perTenantLimitsConf;
    @Value("${transport.rate_limits.device}")
    private String perDevicesLimitsConf;
    @Value("${transport.sessions.inactivity_timeout}")
    private long sessionInactivityTimeout;
    @Value("${transport.sessions.report_timeout}")
    private long sessionReportTimeout;

    protected ScheduledExecutorService schedulerExecutor;
    protected ExecutorService transportCallbackExecutor;

    private ConcurrentMap<UUID, SessionMetaData> sessions = new ConcurrentHashMap<>();

    //TODO: Implement cleanup of this maps.
    private ConcurrentMap<TenantId, TbRateLimits> perTenantLimits = new ConcurrentHashMap<>();
    private ConcurrentMap<DeviceId, TbRateLimits> perDeviceLimits = new ConcurrentHashMap<>();

    @Override
    public void registerAsyncSession(TransportProtos.SessionInfoProto sessionInfo, SessionMsgListener listener) {
        sessions.putIfAbsent(toId(sessionInfo), new SessionMetaData(sessionInfo, TransportProtos.SessionType.ASYNC, listener));
    }

    @Override
    public void process(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.SessionEventMsg msg, TransportServiceCallback<Void> callback) {
        if (checkLimits(sessionInfo, msg, callback)) {
            reportActivityInternal(sessionInfo);
            doProcess(sessionInfo, msg, callback);
        }
    }

    @Override
    public void process(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.PostTelemetryMsg msg, TransportServiceCallback<Void> callback) {
        if (checkLimits(sessionInfo, msg, callback)) {
            reportActivityInternal(sessionInfo);
            doProcess(sessionInfo, msg, callback);
        }
    }

    @Override
    public void process(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.PostAttributeMsg msg, TransportServiceCallback<Void> callback) {
        if (checkLimits(sessionInfo, msg, callback)) {
            reportActivityInternal(sessionInfo);
            doProcess(sessionInfo, msg, callback);
        }
    }

    @Override
    public void process(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.GetAttributeRequestMsg msg, TransportServiceCallback<Void> callback) {
        if (checkLimits(sessionInfo, msg, callback)) {
            reportActivityInternal(sessionInfo);
            doProcess(sessionInfo, msg, callback);
        }
    }

    @Override
    public void process(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.SubscribeToAttributeUpdatesMsg msg, TransportServiceCallback<Void> callback) {
        if (checkLimits(sessionInfo, msg, callback)) {
            SessionMetaData sessionMetaData = reportActivityInternal(sessionInfo);
            sessionMetaData.setSubscribedToAttributes(!msg.getUnsubscribe());
            doProcess(sessionInfo, msg, callback);
        }
    }

    @Override
    public void process(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.SubscribeToRPCMsg msg, TransportServiceCallback<Void> callback) {
        if (checkLimits(sessionInfo, msg, callback)) {
            SessionMetaData sessionMetaData = reportActivityInternal(sessionInfo);
            sessionMetaData.setSubscribedToRPC(!msg.getUnsubscribe());
            doProcess(sessionInfo, msg, callback);
        }
    }

    @Override
    public void process(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.ToDeviceRpcResponseMsg msg, TransportServiceCallback<Void> callback) {
        if (checkLimits(sessionInfo, msg, callback)) {
            reportActivityInternal(sessionInfo);
            doProcess(sessionInfo, msg, callback);
        }
    }

    @Override
    public void process(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.ToServerRpcRequestMsg msg, TransportServiceCallback<Void> callback) {
        if (checkLimits(sessionInfo, msg, callback)) {
            reportActivityInternal(sessionInfo);
            doProcess(sessionInfo, msg, callback);
        }
    }

    @Override
    public void reportActivity(TransportProtos.SessionInfoProto sessionInfo) {
        reportActivityInternal(sessionInfo);
    }

    protected abstract void doProcess(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.SessionEventMsg msg, TransportServiceCallback<Void> callback);

    protected abstract void doProcess(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.PostTelemetryMsg msg, TransportServiceCallback<Void> callback);

    protected abstract void doProcess(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.PostAttributeMsg msg, TransportServiceCallback<Void> callback);

    protected abstract void doProcess(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.GetAttributeRequestMsg msg, TransportServiceCallback<Void> callback);

    protected abstract void doProcess(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.SubscribeToAttributeUpdatesMsg msg, TransportServiceCallback<Void> callback);

    protected abstract void doProcess(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.SubscribeToRPCMsg msg, TransportServiceCallback<Void> callback);

    protected abstract void doProcess(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.ToDeviceRpcResponseMsg msg, TransportServiceCallback<Void> callback);

    protected abstract void doProcess(TransportProtos.SessionInfoProto sessionInfo, TransportProtos.ToServerRpcRequestMsg msg, TransportServiceCallback<Void> callback);

    private SessionMetaData reportActivityInternal(TransportProtos.SessionInfoProto sessionInfo) {
        UUID sessionId = toId(sessionInfo);
        SessionMetaData sessionMetaData = sessions.get(sessionId);
        if (sessionMetaData != null) {
            sessionMetaData.updateLastActivityTime();
        }
        return sessionMetaData;
    }

    private void checkInactivityAndReportActivity() {
        long expTime = System.currentTimeMillis() - sessionInactivityTimeout;
        sessions.forEach((uuid, sessionMD) -> {
            if (sessionMD.getLastActivityTime() < expTime) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Session has expired due to last activity time: {}", toId(sessionMD.getSessionInfo()), sessionMD.getLastActivityTime());
                }
                process(sessionMD.getSessionInfo(), getSessionEventMsg(TransportProtos.SessionEvent.CLOSED), null);
                sessions.remove(uuid);
                sessionMD.getListener().onRemoteSessionCloseCommand(TransportProtos.SessionCloseNotificationProto.getDefaultInstance());
            } else {
                process(sessionMD.getSessionInfo(), TransportProtos.SubscriptionInfoProto.newBuilder()
                        .setAttributeSubscription(sessionMD.isSubscribedToAttributes())
                        .setRpcSubscription(sessionMD.isSubscribedToRPC())
                        .setLastActivityTime(sessionMD.getLastActivityTime()).build(), null);
            }
        });
    }

    @Override
    public void registerSyncSession(TransportProtos.SessionInfoProto sessionInfo, SessionMsgListener listener, long timeout) {
        sessions.putIfAbsent(toId(sessionInfo), new SessionMetaData(sessionInfo, TransportProtos.SessionType.SYNC, listener));
        schedulerExecutor.schedule(() -> {
            listener.onRemoteSessionCloseCommand(TransportProtos.SessionCloseNotificationProto.getDefaultInstance());
            deregisterSession(sessionInfo);
        }, timeout, TimeUnit.MILLISECONDS);
    }

    @Override
    public void deregisterSession(TransportProtos.SessionInfoProto sessionInfo) {
        sessions.remove(toId(sessionInfo));
    }

    @Override
    public boolean checkLimits(TransportProtos.SessionInfoProto sessionInfo, Object msg, TransportServiceCallback<Void> callback) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Processing msg: {}", toId(sessionInfo), msg);
        }
        if (!rateLimitEnabled) {
            return true;
        }
        TenantId tenantId = new TenantId(new UUID(sessionInfo.getTenantIdMSB(), sessionInfo.getTenantIdLSB()));
        TbRateLimits rateLimits = perTenantLimits.computeIfAbsent(tenantId, id -> new TbRateLimits(perTenantLimitsConf));
        if (!rateLimits.tryConsume()) {
            if (callback != null) {
                callback.onError(new TbRateLimitsException(EntityType.TENANT));
            }
            if (log.isTraceEnabled()) {
                log.trace("[{}][{}] Tenant level rate limit detected: {}", toId(sessionInfo), tenantId, msg);
            }
            return false;
        }
        DeviceId deviceId = new DeviceId(new UUID(sessionInfo.getDeviceIdMSB(), sessionInfo.getDeviceIdLSB()));
        rateLimits = perDeviceLimits.computeIfAbsent(deviceId, id -> new TbRateLimits(perDevicesLimitsConf));
        if (!rateLimits.tryConsume()) {
            if (callback != null) {
                callback.onError(new TbRateLimitsException(EntityType.DEVICE));
            }
            if (log.isTraceEnabled()) {
                log.trace("[{}][{}] Device level rate limit detected: {}", toId(sessionInfo), deviceId, msg);
            }
            return false;
        }

        return true;
    }

    protected void processToTransportMsg(TransportProtos.DeviceActorToTransportMsg toSessionMsg) {
        UUID sessionId = new UUID(toSessionMsg.getSessionIdMSB(), toSessionMsg.getSessionIdLSB());
        SessionMetaData md = sessions.get(sessionId);
        if (md != null) {
            SessionMsgListener listener = md.getListener();
            transportCallbackExecutor.submit(() -> {
                if (toSessionMsg.hasGetAttributesResponse()) {
                    listener.onGetAttributesResponse(toSessionMsg.getGetAttributesResponse());
                }
                if (toSessionMsg.hasAttributeUpdateNotification()) {
                    listener.onAttributeUpdate(toSessionMsg.getAttributeUpdateNotification());
                }
                if (toSessionMsg.hasSessionCloseNotification()) {
                    listener.onRemoteSessionCloseCommand(toSessionMsg.getSessionCloseNotification());
                }
                if (toSessionMsg.hasToDeviceRequest()) {
                    listener.onToDeviceRpcRequest(toSessionMsg.getToDeviceRequest());
                }
                if (toSessionMsg.hasToServerResponse()) {
                    listener.onToServerRpcResponse(toSessionMsg.getToServerResponse());
                }
            });
            if (md.getSessionType() == TransportProtos.SessionType.SYNC) {
                deregisterSession(md.getSessionInfo());
            }
        } else {
            //TODO: should we notify the device actor about missed session?
            log.debug("[{}] Missing session.", sessionId);
        }
    }

    protected UUID toId(TransportProtos.SessionInfoProto sessionInfo) {
        return new UUID(sessionInfo.getSessionIdMSB(), sessionInfo.getSessionIdLSB());
    }

    protected String getRoutingKey(TransportProtos.SessionInfoProto sessionInfo) {
        return new UUID(sessionInfo.getDeviceIdMSB(), sessionInfo.getDeviceIdLSB()).toString();
    }

    public void init() {
        if (rateLimitEnabled) {
            //Just checking the configuration parameters
            new TbRateLimits(perTenantLimitsConf);
            new TbRateLimits(perDevicesLimitsConf);
        }
        this.schedulerExecutor = Executors.newSingleThreadScheduledExecutor();
        this.transportCallbackExecutor = Executors.newWorkStealingPool(20);
        this.schedulerExecutor.scheduleAtFixedRate(this::checkInactivityAndReportActivity, sessionReportTimeout, sessionReportTimeout, TimeUnit.MILLISECONDS);
    }

    public void destroy() {
        if (rateLimitEnabled) {
            perTenantLimits.clear();
            perDeviceLimits.clear();
        }
        if (schedulerExecutor != null) {
            schedulerExecutor.shutdownNow();
        }
        if (transportCallbackExecutor != null) {
            transportCallbackExecutor.shutdownNow();
        }
    }

    public static TransportProtos.SessionEventMsg getSessionEventMsg(TransportProtos.SessionEvent event) {
        return TransportProtos.SessionEventMsg.newBuilder()
                .setSessionType(TransportProtos.SessionType.ASYNC)
                .setEvent(event).build();
    }
}
