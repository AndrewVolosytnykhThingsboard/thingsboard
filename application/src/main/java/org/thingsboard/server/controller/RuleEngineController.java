/**
 * Copyright © 2016-2021 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.FutureCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.ruleengine.LocalRequestMetaData;
import org.thingsboard.server.service.ruleengine.RuleEngineCallService;
import org.thingsboard.server.service.security.AccessValidator;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.telemetry.exception.ToErrorResponseEntity;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Created by ashvayka on 22.03.18.
 */
@RestController
@TbCoreComponent
@RequestMapping(TbUrlConstants.RULE_ENGINE_URL_PREFIX)
@Slf4j
public class RuleEngineController extends BaseController {

    public static final int DEFAULT_TIMEOUT = 10000;
    protected final ObjectMapper jsonMapper = new ObjectMapper();

    @Autowired
    private RuleEngineCallService ruleEngineCallService;

    @Autowired
    private AccessValidator accessValidator;

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/", method = RequestMethod.POST)
    @ResponseBody
    public DeferredResult<ResponseEntity> handleRuleEngineRequest(@RequestBody String requestBody) throws ThingsboardException {
        return handleRuleEngineRequest(null, null, DEFAULT_TIMEOUT, requestBody);
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/{entityType}/{entityId}", method = RequestMethod.POST)
    @ResponseBody
    public DeferredResult<ResponseEntity> handleRuleEngineRequest(@PathVariable("entityType") String entityType,
                                                                  @PathVariable("entityId") String entityIdStr,
                                                                  @RequestBody String requestBody) throws ThingsboardException {
        return handleRuleEngineRequest(entityType, entityIdStr, DEFAULT_TIMEOUT, requestBody);
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/{entityType}/{entityId}/{timeout}", method = RequestMethod.POST)
    @ResponseBody
    public DeferredResult<ResponseEntity> handleRuleEngineRequest(@PathVariable("entityType") String entityType,
                                                                  @PathVariable("entityId") String entityIdStr,
                                                                  @PathVariable("timeout") int timeout,
                                                                  @RequestBody String requestBody) throws ThingsboardException {
        try {
            SecurityUser currentUser = getCurrentUser();
            EntityId entityId;
            if (StringUtils.isEmpty(entityType) || StringUtils.isEmpty(entityIdStr)) {
                entityId = currentUser.getId();
            } else {
                entityId = EntityIdFactory.getByTypeAndId(entityType, entityIdStr);
            }
            //Check that this is a valid JSON
            jsonMapper.readTree(requestBody);
            final DeferredResult<ResponseEntity> response = new DeferredResult<>();
            accessValidator.validate(currentUser, Operation.WRITE, entityId, new HttpValidationCallback(response, new FutureCallback<DeferredResult<ResponseEntity>>() {
                @Override
                public void onSuccess(@Nullable DeferredResult<ResponseEntity> result) {
                    long expTime = System.currentTimeMillis() + timeout;
                    HashMap<String, String> metaData = new HashMap<>();
                    UUID requestId = UUID.randomUUID();
                    metaData.put("serviceId", serviceInfoProvider.getServiceId());
                    metaData.put("requestUUID", requestId.toString());
                    metaData.put("expirationTime", Long.toString(expTime));
                    TbMsg msg = TbMsg.newMsg(DataConstants.REST_API_REQUEST, entityId, new TbMsgMetaData(metaData), requestBody);
                    ruleEngineCallService.processRestAPICallToRuleEngine(currentUser.getTenantId(), requestId, msg,
                            reply -> reply(new LocalRequestMetaData(msg, currentUser, result), reply));
                }

                @Override
                public void onFailure(Throwable e) {
                    ResponseEntity entity;
                    if (e instanceof ToErrorResponseEntity) {
                        entity = ((ToErrorResponseEntity) e).toErrorResponseEntity();
                    } else {
                        entity = new ResponseEntity(HttpStatus.UNAUTHORIZED);
                    }
                    logRuleEngineCall(currentUser, entityId, requestBody, null, e);
                    response.setResult(entity);
                }
            }));
            return response;
        } catch (IOException ioe) {
            throw new ThingsboardException("Invalid request body", ioe, ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
    }

    private void reply(LocalRequestMetaData rpcRequest, TbMsg response) {
        DeferredResult<ResponseEntity> responseWriter = rpcRequest.getResponseWriter();
        if (response == null) {
            logRuleEngineCall(rpcRequest, null, new TimeoutException("Processing timeout detected!"));
            responseWriter.setResult(new ResponseEntity<>(HttpStatus.REQUEST_TIMEOUT));
        } else {
            String responseData = response.getData();
            if (!StringUtils.isEmpty(responseData)) {
                try {
                    logRuleEngineCall(rpcRequest, response, null);
                    responseWriter.setResult(new ResponseEntity<>(jsonMapper.readTree(responseData), HttpStatus.OK));
                } catch (IOException e) {
                    log.debug("Failed to decode device response: {}", responseData, e);
                    logRuleEngineCall(rpcRequest, response, e);
                    responseWriter.setResult(new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE));
                }
            } else {
                logRuleEngineCall(rpcRequest, response, null);
                responseWriter.setResult(new ResponseEntity<>(HttpStatus.OK));
            }
        }
    }

    private void logRuleEngineCall(LocalRequestMetaData rpcRequest, TbMsg response, Throwable e) {
        logRuleEngineCall(rpcRequest.getUser(), rpcRequest.getRequest().getOriginator(), rpcRequest.getRequest().getData(), response, e);
    }

    private void logRuleEngineCall(SecurityUser user, EntityId entityId, String request, TbMsg response, Throwable e) {
        auditLogService.logEntityAction(
                user.getTenantId(),
                user.getCustomerId(),
                user.getId(),
                user.getName(),
                entityId,
                null,
                ActionType.REST_API_RULE_ENGINE_CALL,
                BaseController.toException(e),
                request,
                response != null ? response.getData() : "");
    }
}
