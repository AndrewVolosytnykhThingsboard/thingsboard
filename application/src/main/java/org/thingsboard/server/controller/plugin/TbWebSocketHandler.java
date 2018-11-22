/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2018 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.controller.plugin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.NativeWebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.msg.tools.TbRateLimits;
import org.thingsboard.server.config.WebSocketConfiguration;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.security.model.UserPrincipal;
import org.thingsboard.server.service.telemetry.SessionEvent;
import org.thingsboard.server.service.telemetry.TelemetryWebSocketMsgEndpoint;
import org.thingsboard.server.service.telemetry.TelemetryWebSocketService;
import org.thingsboard.server.service.telemetry.TelemetryWebSocketSessionRef;

import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;
import java.io.IOException;
import java.net.URI;
import java.security.InvalidParameterException;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

@Service
@Slf4j
public class TbWebSocketHandler extends TextWebSocketHandler implements TelemetryWebSocketMsgEndpoint {

    private static final ConcurrentMap<String, SessionMetaData> internalSessionMap = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> externalSessionMap = new ConcurrentHashMap<>();

    @Autowired
    private TelemetryWebSocketService webSocketService;

    @Value("${server.ws.send_timeout:5000}")
    private long sendTimeout;

    @Value("${server.ws.limits.max_sessions_per_tenant:0}")
    private int maxSessionsPerTenant;
    @Value("${server.ws.limits.max_sessions_per_customer:0}")
    private int maxSessionsPerCustomer;
    @Value("${server.ws.limits.max_sessions_per_regular_user:0}")
    private int maxSessionsPerRegularUser;
    @Value("${server.ws.limits.max_sessions_per_public_user:0}")
    private int maxSessionsPerPublicUser;

    @Value("${server.ws.limits.max_updates_per_session:}")
    private String perSessionUpdatesConfiguration;

    private ConcurrentMap<String, TelemetryWebSocketSessionRef> blacklistedSessions = new ConcurrentHashMap<>();
    private ConcurrentMap<String, TbRateLimits> perSessionUpdateLimits = new ConcurrentHashMap<>();

    private ConcurrentMap<TenantId, Set<String>> tenantSessionsMap = new ConcurrentHashMap<>();
    private ConcurrentMap<CustomerId, Set<String>> customerSessionsMap = new ConcurrentHashMap<>();
    private ConcurrentMap<UserId, Set<String>> regularUserSessionsMap = new ConcurrentHashMap<>();
    private ConcurrentMap<UserId, Set<String>> publicUserSessionsMap = new ConcurrentHashMap<>();

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            SessionMetaData sessionMd = internalSessionMap.get(session.getId());
            if (sessionMd != null) {
                log.info("[{}][{}] Processing {}", sessionMd.sessionRef.getSecurityCtx().getTenantId(), session.getId(), message.getPayload());
                webSocketService.handleWebSocketMsg(sessionMd.sessionRef, message.getPayload());
            } else {
                log.warn("[{}] Failed to find session", session.getId());
                session.close(CloseStatus.SERVER_ERROR.withReason("Session not found!"));
            }
        } catch (IOException e) {
            log.warn("IO error", e);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        try {
            if (session instanceof NativeWebSocketSession) {
                Session nativeSession = ((NativeWebSocketSession)session).getNativeSession(Session.class);
                if (nativeSession != null) {
                    nativeSession.getAsyncRemote().setSendTimeout(sendTimeout);
                }
            }
            String internalSessionId = session.getId();
            TelemetryWebSocketSessionRef sessionRef = toRef(session);
            String externalSessionId = sessionRef.getSessionId();
            if (!checkLimits(session, sessionRef)) {
                return;
            }
            internalSessionMap.put(internalSessionId, new SessionMetaData(session, sessionRef));
            externalSessionMap.put(externalSessionId, internalSessionId);
            processInWebSocketService(sessionRef, SessionEvent.onEstablished());
            log.info("[{}][{}][{}] Session is opened", sessionRef.getSecurityCtx().getTenantId(), externalSessionId, session.getId());
        } catch (InvalidParameterException e) {
            log.warn("[{}] Failed to start session", session.getId(), e);
            session.close(CloseStatus.BAD_DATA.withReason(e.getMessage()));
        } catch (Exception e) {
            log.warn("[{}] Failed to start session", session.getId(), e);
            session.close(CloseStatus.SERVER_ERROR.withReason(e.getMessage()));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable tError) throws Exception {
        super.handleTransportError(session, tError);
        SessionMetaData sessionMd = internalSessionMap.get(session.getId());
        if (sessionMd != null) {
            processInWebSocketService(sessionMd.sessionRef, SessionEvent.onError(tError));
        } else {
            log.warn("[{}] Failed to find session", session.getId());
        }
        log.trace("[{}] Session transport error", session.getId(), tError);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        super.afterConnectionClosed(session, closeStatus);
        SessionMetaData sessionMd = internalSessionMap.remove(session.getId());
        if (sessionMd != null) {
            cleanupLimits(session, sessionMd.sessionRef);
            externalSessionMap.remove(sessionMd.sessionRef.getSessionId());
            processInWebSocketService(sessionMd.sessionRef, SessionEvent.onClosed());
        }
        log.info("[{}] Session is closed", session.getId());
    }

    private void processInWebSocketService(TelemetryWebSocketSessionRef sessionRef, SessionEvent event) {
        try {
            webSocketService.handleWebSocketSessionEvent(sessionRef, event);
        } catch (BeanCreationNotAllowedException e) {
            log.warn("[{}] Failed to close session due to possible shutdown state", sessionRef.getSessionId());
        }
    }

    private TelemetryWebSocketSessionRef toRef(WebSocketSession session) throws IOException {
        URI sessionUri = session.getUri();
        String path = sessionUri.getPath();
        path = path.substring(WebSocketConfiguration.WS_PLUGIN_PREFIX.length());
        if (path.length() == 0) {
            throw new IllegalArgumentException("URL should contain plugin token!");
        }
        String[] pathElements = path.split("/");
        String serviceToken = pathElements[0];
        if (!"telemetry".equalsIgnoreCase(serviceToken)) {
            throw new InvalidParameterException("Can't find plugin with specified token!");
        } else {
            SecurityUser currentUser = (SecurityUser) ((Authentication)session.getPrincipal()).getPrincipal();
            return new TelemetryWebSocketSessionRef(UUID.randomUUID().toString(), currentUser, session.getLocalAddress(), session.getRemoteAddress());
        }
    }

    private static class SessionMetaData implements SendHandler {
        private final WebSocketSession session;
        private final RemoteEndpoint.Async asyncRemote;
        private final TelemetryWebSocketSessionRef sessionRef;

        private volatile boolean isSending = false;

        private Queue<String> msgQueue = new LinkedBlockingQueue<>();

        SessionMetaData(WebSocketSession session, TelemetryWebSocketSessionRef sessionRef) {
            super();
            this.session = session;
            Session nativeSession = ((NativeWebSocketSession)session).getNativeSession(Session.class);
            this.asyncRemote = nativeSession.getAsyncRemote();
            this.sessionRef = sessionRef;
        }

        public synchronized void sendMsg(String msg) {
            if (isSending) {
                msgQueue.add(msg);
            } else {
                isSending = true;
                sendMsgInternal(msg);
            }
        }

        private void sendMsgInternal(String msg) {
            try {
                this.asyncRemote.sendText(msg, this);
            } catch (Exception e) {
                log.error("[{}] Failed to send msg", session.getId(), e);
            }
        }

        @Override
        public void onResult(SendResult result) {
            if (!result.isOK()) {
                log.error("[{}] Failed to send msg", session.getId(), result.getException());
            }
            String msg = msgQueue.poll();
            if (msg != null) {
                sendMsgInternal(msg);
            } else {
                isSending = false;
            }
        }
    }

    @Override
    public void send(TelemetryWebSocketSessionRef sessionRef, int subscriptionId, String msg) throws IOException {
        String externalId = sessionRef.getSessionId();
        log.debug("[{}] Processing {}", externalId, msg);
        String internalId = externalSessionMap.get(externalId);
        if (internalId != null) {
            SessionMetaData sessionMd = internalSessionMap.get(internalId);
            if (sessionMd != null) {
                if (!StringUtils.isEmpty(perSessionUpdatesConfiguration)) {
                    TbRateLimits rateLimits = perSessionUpdateLimits.computeIfAbsent(sessionRef.getSessionId(), sid -> new TbRateLimits(perSessionUpdatesConfiguration));
                    if (!rateLimits.tryConsume()) {
                        if (blacklistedSessions.putIfAbsent(externalId, sessionRef) == null) {
                            log.info("[{}][{}][{}] Failed to process session update. Max session updates limit reached"
                                    , sessionRef.getSecurityCtx().getTenantId(), sessionRef.getSecurityCtx().getId(), externalId);
                            sessionMd.sendMsg("{\"subscriptionId\":" + subscriptionId + ", \"errorCode\":" + ThingsboardErrorCode.TOO_MANY_UPDATES.getErrorCode() + ", \"errorMsg\":\"Too many updates!\"}");
                        }
                        return;
                    } else {
                        log.debug("[{}][{}][{}] Session is no longer blacklisted.", sessionRef.getSecurityCtx().getTenantId(), sessionRef.getSecurityCtx().getId(), externalId);
                        blacklistedSessions.remove(externalId);
                    }
                }
                sessionMd.sendMsg(msg);
            } else {
                log.warn("[{}][{}] Failed to find session by internal id", externalId, internalId);
            }
        } else {
            log.warn("[{}] Failed to find session by external id", externalId);
        }
    }

    @Override
    public void close(TelemetryWebSocketSessionRef sessionRef, CloseStatus reason) throws IOException {
        String externalId = sessionRef.getSessionId();
        log.debug("[{}] Processing close request", externalId);
        String internalId = externalSessionMap.get(externalId);
        if (internalId != null) {
            SessionMetaData sessionMd = internalSessionMap.get(internalId);
            if (sessionMd != null) {
                sessionMd.session.close(reason);
            } else {
                log.warn("[{}][{}] Failed to find session by internal id", externalId, internalId);
            }
        } else {
            log.warn("[{}] Failed to find session by external id", externalId);
        }
    }

    private boolean checkLimits(WebSocketSession session, TelemetryWebSocketSessionRef sessionRef) throws Exception {
        String sessionId = session.getId();
        if (maxSessionsPerTenant > 0) {
            Set<String> tenantSessions = tenantSessionsMap.computeIfAbsent(sessionRef.getSecurityCtx().getTenantId(), id -> ConcurrentHashMap.newKeySet());
            synchronized (tenantSessions) {
                if (tenantSessions.size() < maxSessionsPerTenant) {
                    tenantSessions.add(sessionId);
                } else {
                    log.info("[{}][{}][{}] Failed to start session. Max tenant sessions limit reached"
                            , sessionRef.getSecurityCtx().getTenantId(), sessionRef.getSecurityCtx().getId(), sessionId);
                    session.close(CloseStatus.POLICY_VIOLATION.withReason("Max tenant sessions limit reached!"));
                    return false;
                }
            }
        }

        if (sessionRef.getSecurityCtx().isCustomerUser()) {
            if (maxSessionsPerCustomer > 0) {
                Set<String> customerSessions = customerSessionsMap.computeIfAbsent(sessionRef.getSecurityCtx().getCustomerId(), id -> ConcurrentHashMap.newKeySet());
                synchronized (customerSessions) {
                    if (customerSessions.size() < maxSessionsPerCustomer) {
                        customerSessions.add(sessionId);
                    } else {
                        log.info("[{}][{}][{}] Failed to start session. Max customer sessions limit reached"
                                , sessionRef.getSecurityCtx().getTenantId(), sessionRef.getSecurityCtx().getId(), sessionId);
                        session.close(CloseStatus.POLICY_VIOLATION.withReason("Max customer sessions limit reached"));
                        return false;
                    }
                }
            }
            if (maxSessionsPerRegularUser > 0 && UserPrincipal.Type.USER_NAME.equals(sessionRef.getSecurityCtx().getUserPrincipal().getType())) {
                Set<String> regularUserSessions = regularUserSessionsMap.computeIfAbsent(sessionRef.getSecurityCtx().getId(), id -> ConcurrentHashMap.newKeySet());
                synchronized (regularUserSessions) {
                    if (regularUserSessions.size() < maxSessionsPerRegularUser) {
                        regularUserSessions.add(sessionId);
                    } else {
                        log.info("[{}][{}][{}] Failed to start session. Max regular user sessions limit reached"
                                , sessionRef.getSecurityCtx().getTenantId(), sessionRef.getSecurityCtx().getId(), sessionId);
                        session.close(CloseStatus.POLICY_VIOLATION.withReason("Max regular user sessions limit reached"));
                        return false;
                    }
                }
            }
            if (maxSessionsPerPublicUser > 0 && UserPrincipal.Type.PUBLIC_ID.equals(sessionRef.getSecurityCtx().getUserPrincipal().getType())) {
                Set<String> publicUserSessions = publicUserSessionsMap.computeIfAbsent(sessionRef.getSecurityCtx().getId(), id -> ConcurrentHashMap.newKeySet());
                synchronized (publicUserSessions) {
                    if (publicUserSessions.size() < maxSessionsPerPublicUser) {
                        publicUserSessions.add(sessionId);
                    } else {
                        log.info("[{}][{}][{}] Failed to start session. Max public user sessions limit reached"
                                , sessionRef.getSecurityCtx().getTenantId(), sessionRef.getSecurityCtx().getId(), sessionId);
                        session.close(CloseStatus.POLICY_VIOLATION.withReason("Max public user sessions limit reached"));
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void cleanupLimits(WebSocketSession session, TelemetryWebSocketSessionRef sessionRef) {
        String sessionId = session.getId();
        perSessionUpdateLimits.remove(sessionRef.getSessionId());
        blacklistedSessions.remove(sessionRef.getSessionId());
        if (maxSessionsPerTenant > 0) {
            Set<String> tenantSessions = tenantSessionsMap.computeIfAbsent(sessionRef.getSecurityCtx().getTenantId(), id -> ConcurrentHashMap.newKeySet());
            synchronized (tenantSessions) {
                tenantSessions.remove(sessionId);
            }
        }
        if (sessionRef.getSecurityCtx().isCustomerUser()) {
            if (maxSessionsPerCustomer > 0) {
                Set<String> customerSessions = customerSessionsMap.computeIfAbsent(sessionRef.getSecurityCtx().getCustomerId(), id -> ConcurrentHashMap.newKeySet());
                synchronized (customerSessions) {
                    customerSessions.remove(sessionId);
                }
            }
            if (maxSessionsPerRegularUser > 0 && UserPrincipal.Type.USER_NAME.equals(sessionRef.getSecurityCtx().getUserPrincipal().getType())) {
                Set<String> regularUserSessions = regularUserSessionsMap.computeIfAbsent(sessionRef.getSecurityCtx().getId(), id -> ConcurrentHashMap.newKeySet());
                synchronized (regularUserSessions) {
                    regularUserSessions.remove(sessionId);
                }
            }
            if (maxSessionsPerPublicUser > 0 && UserPrincipal.Type.PUBLIC_ID.equals(sessionRef.getSecurityCtx().getUserPrincipal().getType())) {
                Set<String> publicUserSessions = publicUserSessionsMap.computeIfAbsent(sessionRef.getSecurityCtx().getId(), id -> ConcurrentHashMap.newKeySet());
                synchronized (publicUserSessions) {
                    publicUserSessions.remove(sessionId);
                }
            }
        }
    }

}
