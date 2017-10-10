/**
 * Thingsboard OÜ ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2017 Thingsboard OÜ. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of Thingsboard OÜ and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Thingsboard OÜ
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
package org.thingsboard.server.extensions.core.plugin.telemetry.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.kv.*;
import org.thingsboard.server.common.msg.core.TelemetryUploadRequest;
import org.thingsboard.server.common.transport.adaptor.JsonConverter;
import org.thingsboard.server.extensions.api.exception.InvalidParametersException;
import org.thingsboard.server.extensions.api.exception.ToErrorResponseEntity;
import org.thingsboard.server.extensions.api.exception.UncheckedApiException;
import org.thingsboard.server.extensions.api.plugins.PluginCallback;
import org.thingsboard.server.extensions.api.plugins.PluginContext;
import org.thingsboard.server.extensions.api.plugins.handlers.DefaultRestMsgHandler;
import org.thingsboard.server.extensions.api.plugins.rest.PluginRestMsg;
import org.thingsboard.server.extensions.api.plugins.rest.RestRequest;
import org.thingsboard.server.extensions.core.plugin.telemetry.AttributeData;
import org.thingsboard.server.extensions.core.plugin.telemetry.SubscriptionManager;
import org.thingsboard.server.extensions.core.plugin.telemetry.TsData;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class TelemetryRestMsgHandler extends DefaultRestMsgHandler {

    private final SubscriptionManager subscriptionManager;

    public TelemetryRestMsgHandler(SubscriptionManager subscriptionManager) {
        this.subscriptionManager = subscriptionManager;
    }

    @Override
    public void handleHttpGetRequest(PluginContext ctx, PluginRestMsg msg) throws ServletException {
        RestRequest request = msg.getRequest();
        String[] pathParams = request.getPathParams();
        if (pathParams.length < 4) {
            msg.getResponseHolder().setResult(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
            return;
        }

        String entityType = pathParams[0];
        String entityIdStr = pathParams[1];
        String method = pathParams[2];
        TelemetryFeature feature = TelemetryFeature.forName(pathParams[3]);
        String scope = pathParams.length >= 5 ? pathParams[4] : null;
        if (StringUtils.isEmpty(entityType) || EntityType.valueOf(entityType) == null || StringUtils.isEmpty(entityIdStr) || StringUtils.isEmpty(method) || StringUtils.isEmpty(feature)) {
            msg.getResponseHolder().setResult(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
            return;
        }

        EntityId entityId = EntityIdFactory.getByTypeAndId(entityType, entityIdStr);

        if (method.equals("keys")) {
            handleHttpGetKeysMethod(ctx, msg, feature, scope, entityId);
        } else if (method.equals("values")) {
            handleHttpGetValuesMethod(ctx, msg, request, feature, scope, entityId);
        }
    }

    private void handleHttpGetKeysMethod(PluginContext ctx, PluginRestMsg msg, TelemetryFeature feature, String scope, EntityId entityId) {
        if (feature == TelemetryFeature.TIMESERIES) {
            ctx.loadLatestTimeseries(entityId, new PluginCallback<List<TsKvEntry>>() {
                @Override
                public void onSuccess(PluginContext ctx, List<TsKvEntry> value) {
                    List<String> keys = value.stream().map(KvEntry::getKey).collect(Collectors.toList());
                    msg.getResponseHolder().setResult(new ResponseEntity<>(keys, HttpStatus.OK));
                }

                @Override
                public void onFailure(PluginContext ctx, Exception e) {
                    handleError(e, msg, HttpStatus.INTERNAL_SERVER_ERROR);
                }
            });
        } else if (feature == TelemetryFeature.ATTRIBUTES) {
            PluginCallback<List<AttributeKvEntry>> callback = getAttributeKeysPluginCallback(msg);
            if (!StringUtils.isEmpty(scope)) {
                ctx.loadAttributes(entityId, scope, callback);
            } else {
                ctx.loadAttributes(entityId, Arrays.asList(DataConstants.allScopes()), callback);
            }
        }
    }

    private void handleHttpGetValuesMethod(PluginContext ctx, PluginRestMsg msg,
                                           RestRequest request, TelemetryFeature feature,
                                           String scope, EntityId entityId) throws ServletException {
        if (feature == TelemetryFeature.TIMESERIES) {
            handleHttpGetTimeseriesValues(ctx, msg, request, entityId);
        } else if (feature == TelemetryFeature.ATTRIBUTES) {
            handleHttpGetAttributesValues(ctx, msg, request, scope, entityId);
        }
    }

    private void handleHttpGetTimeseriesValues(PluginContext ctx, PluginRestMsg msg, RestRequest request, EntityId entityId) throws ServletException {
        String keysStr = request.getParameter("keys");
        List<String> keys = Arrays.asList(keysStr.split(","));

        Optional<Long> startTs = request.getLongParamValue("startTs");
        Optional<Long> endTs = request.getLongParamValue("endTs");
        Optional<Long> interval = request.getLongParamValue("interval");
        Optional<Integer> limit = request.getIntParamValue("limit");

        if (startTs.isPresent() || endTs.isPresent() || interval.isPresent() || limit.isPresent()) {
            if (!startTs.isPresent() || !endTs.isPresent() || !interval.isPresent()) {
                msg.getResponseHolder().setResult(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
                return;
            }
            Aggregation agg = Aggregation.valueOf(request.getParameter("agg", Aggregation.NONE.name()));

            List<TsKvQuery> queries = keys.stream().map(key -> new BaseTsKvQuery(key, startTs.get(), endTs.get(), interval.get(), limit.orElse(TelemetryWebsocketMsgHandler.DEFAULT_LIMIT), agg))
                    .collect(Collectors.toList());
            ctx.loadTimeseries(entityId, queries, getTsKvListCallback(msg));
        } else {
            ctx.loadLatestTimeseries(entityId, keys, getTsKvListCallback(msg));
        }
    }

    private void handleHttpGetAttributesValues(PluginContext ctx, PluginRestMsg msg,
                                               RestRequest request, String scope, EntityId entityId) throws ServletException {
        String keys = request.getParameter("keys", "");

        PluginCallback<List<AttributeKvEntry>> callback = getAttributeValuesPluginCallback(msg);
        if (!StringUtils.isEmpty(scope)) {
            if (!StringUtils.isEmpty(keys)) {
                List<String> keyList = Arrays.asList(keys.split(","));
                ctx.loadAttributes(entityId, scope, keyList, callback);
            } else {
                ctx.loadAttributes(entityId, scope, callback);
            }
        } else {
            if (!StringUtils.isEmpty(keys)) {
                List<String> keyList = Arrays.asList(keys.split(","));
                ctx.loadAttributes(entityId, Arrays.asList(DataConstants.allScopes()), keyList, callback);
            } else {
                ctx.loadAttributes(entityId, Arrays.asList(DataConstants.allScopes()), callback);
            }
        }
    }

    @Override
    public void handleHttpPostRequest(PluginContext ctx, PluginRestMsg msg) throws ServletException {
        RestRequest request = msg.getRequest();
        Exception error = null;
        try {
            String[] pathParams = request.getPathParams();
            EntityId entityId;
            String scope;
            long ttl = 0L;
            TelemetryFeature feature;
            if (pathParams.length == 2) {
                entityId = DeviceId.fromString(pathParams[0]);
                scope = pathParams[1];
                feature = TelemetryFeature.ATTRIBUTES;
            } else if (pathParams.length == 3) {
                entityId = EntityIdFactory.getByTypeAndId(pathParams[0], pathParams[1]);
                scope = pathParams[2];
                feature = TelemetryFeature.ATTRIBUTES;
            } else if (pathParams.length == 4) {
                entityId = EntityIdFactory.getByTypeAndId(pathParams[0], pathParams[1]);
                feature = TelemetryFeature.forName(pathParams[2].toUpperCase());
                scope = pathParams[3];
            } else if (pathParams.length == 5) {
                entityId = EntityIdFactory.getByTypeAndId(pathParams[0], pathParams[1]);
                feature = TelemetryFeature.forName(pathParams[2].toUpperCase());
                scope = pathParams[3];
                ttl = Long.parseLong(pathParams[4]);
            } else {
                msg.getResponseHolder().setResult(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
                return;
            }
            if (feature == TelemetryFeature.ATTRIBUTES) {
                if (handleHttpPostAttributes(ctx, msg, request, entityId, scope)) {
                    return;
                }
            } else if (feature == TelemetryFeature.TIMESERIES) {
                handleHttpPostTimeseries(ctx, msg, request, entityId, ttl);
                return;
            }
        } catch (IOException | RuntimeException e) {
            log.debug("Failed to process POST request due to exception", e);
            error = e;
        }
        handleError(error, msg, HttpStatus.BAD_REQUEST);
    }

    private boolean handleHttpPostAttributes(PluginContext ctx, PluginRestMsg msg, RestRequest request,
                                          EntityId entityId, String scope) throws ServletException, IOException {
        if (DataConstants.SERVER_SCOPE.equals(scope) ||
                DataConstants.SHARED_SCOPE.equals(scope)) {
            JsonNode jsonNode = jsonMapper.readTree(request.getRequestBody());
            if (jsonNode.isObject()) {
                List<AttributeKvEntry> attributes = extractRequestAttributes(jsonNode);
                if (!attributes.isEmpty()) {
                    ctx.saveAttributes(ctx.getSecurityCtx().orElseThrow(IllegalArgumentException::new).getTenantId(), entityId, scope, attributes, new PluginCallback<Void>() {
                        @Override
                        public void onSuccess(PluginContext ctx, Void value) {
                            msg.getResponseHolder().setResult(new ResponseEntity<>(HttpStatus.OK));
                            subscriptionManager.onAttributesUpdateFromServer(ctx, entityId, scope, attributes);
                        }

                        @Override
                        public void onFailure(PluginContext ctx, Exception e) {
                            log.error("Failed to save attributes", e);
                            handleError(e, msg, HttpStatus.BAD_REQUEST);
                        }
                    });
                    return true;
                }
            }
        }
        return false;
    }

    private List<AttributeKvEntry> extractRequestAttributes(JsonNode jsonNode) {
        long ts = System.currentTimeMillis();
        List<AttributeKvEntry> attributes = new ArrayList<>();
        jsonNode.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (entry.getValue().isTextual()) {
                attributes.add(new BaseAttributeKvEntry(new StringDataEntry(key, value.textValue()), ts));
            } else if (entry.getValue().isBoolean()) {
                attributes.add(new BaseAttributeKvEntry(new BooleanDataEntry(key, value.booleanValue()), ts));
            } else if (entry.getValue().isDouble()) {
                attributes.add(new BaseAttributeKvEntry(new DoubleDataEntry(key, value.doubleValue()), ts));
            } else if (entry.getValue().isNumber()) {
                if (entry.getValue().isBigInteger()) {
                    throw new UncheckedApiException(new InvalidParametersException("Big integer values are not supported!"));
                } else {
                    attributes.add(new BaseAttributeKvEntry(new LongDataEntry(key, value.longValue()), ts));
                }
            }
        });
        return  attributes;
    }

    private void handleHttpPostTimeseries(PluginContext ctx, PluginRestMsg msg, RestRequest request, EntityId entityId, long ttl) {
        TelemetryUploadRequest telemetryRequest;
        try {
            telemetryRequest = JsonConverter.convertToTelemetry(new JsonParser().parse(request.getRequestBody()));
        } catch (JsonSyntaxException e) {
            throw new UncheckedApiException(new InvalidParametersException(e.getMessage()));
        }
        List<TsKvEntry> entries = new ArrayList<>();
        for (Map.Entry<Long, List<KvEntry>> entry : telemetryRequest.getData().entrySet()) {
            for (KvEntry kv : entry.getValue()) {
                entries.add(new BasicTsKvEntry(entry.getKey(), kv));
            }
        }
        ctx.saveTsData(entityId, entries, ttl, new PluginCallback<Void>() {
            @Override
            public void onSuccess(PluginContext ctx, Void value) {
                msg.getResponseHolder().setResult(new ResponseEntity<>(HttpStatus.OK));
                subscriptionManager.onTimeseriesUpdateFromServer(ctx, entityId, entries);
            }

            @Override
            public void onFailure(PluginContext ctx, Exception e) {
                log.error("Failed to save attributes", e);
                handleError(e, msg, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        });
    }

    @Override
    public void handleHttpDeleteRequest(PluginContext ctx, PluginRestMsg msg) throws ServletException {
        RestRequest request = msg.getRequest();
        Exception error = null;
        try {
            String[] pathParams = request.getPathParams();
            EntityId entityId;
            String scope;
            if (pathParams.length == 2) {
                entityId = DeviceId.fromString(pathParams[0]);
                scope = pathParams[1];
            } else if (pathParams.length == 3) {
                entityId = EntityIdFactory.getByTypeAndId(pathParams[0], pathParams[1]);
                scope = pathParams[2];
            } else {
                msg.getResponseHolder().setResult(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
                return;
            }

            if (DataConstants.SERVER_SCOPE.equals(scope) ||
                    DataConstants.SHARED_SCOPE.equals(scope) ||
                    DataConstants.CLIENT_SCOPE.equals(scope)) {
                String keysParam = request.getParameter("keys");
                if (!StringUtils.isEmpty(keysParam)) {
                    String[] keys = keysParam.split(",");
                    ctx.removeAttributes(ctx.getSecurityCtx().orElseThrow(IllegalArgumentException::new).getTenantId(), entityId, scope, Arrays.asList(keys), new PluginCallback<Void>() {
                        @Override
                        public void onSuccess(PluginContext ctx, Void value) {
                            msg.getResponseHolder().setResult(new ResponseEntity<>(HttpStatus.OK));
                        }

                        @Override
                        public void onFailure(PluginContext ctx, Exception e) {
                            log.error("Failed to remove attributes", e);
                            handleError(e, msg, HttpStatus.INTERNAL_SERVER_ERROR);
                        }
                    });
                    return;
                }
            }
        } catch (RuntimeException e) {
            log.debug("Failed to process DELETE request due to Runtime exception", e);
            error = e;
        }
        handleError(error, msg, HttpStatus.BAD_REQUEST);
    }


    private PluginCallback<List<AttributeKvEntry>> getAttributeKeysPluginCallback(final PluginRestMsg msg) {
        return new PluginCallback<List<AttributeKvEntry>>() {
            @Override
            public void onSuccess(PluginContext ctx, List<AttributeKvEntry> attributes) {
                List<String> keys = attributes.stream().map(KvEntry::getKey).collect(Collectors.toList());
                msg.getResponseHolder().setResult(new ResponseEntity<>(keys, HttpStatus.OK));
            }

            @Override
            public void onFailure(PluginContext ctx, Exception e) {
                log.error("Failed to fetch attributes", e);
                handleError(e, msg, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        };
    }

    private PluginCallback<List<AttributeKvEntry>> getAttributeValuesPluginCallback(final PluginRestMsg msg) {
        return new PluginCallback<List<AttributeKvEntry>>() {
            @Override
            public void onSuccess(PluginContext ctx, List<AttributeKvEntry> attributes) {
                List<AttributeData> values = attributes.stream().map(attribute -> new AttributeData(attribute.getLastUpdateTs(),
                        attribute.getKey(), attribute.getValue())).collect(Collectors.toList());
                msg.getResponseHolder().setResult(new ResponseEntity<>(values, HttpStatus.OK));
            }

            @Override
            public void onFailure(PluginContext ctx, Exception e) {
                log.error("Failed to fetch attributes", e);
                handleError(e, msg, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        };
    }


    private PluginCallback<List<TsKvEntry>> getTsKvListCallback(final PluginRestMsg msg) {
        return new PluginCallback<List<TsKvEntry>>() {
            @Override
            public void onSuccess(PluginContext ctx, List<TsKvEntry> data) {
                Map<String, List<TsData>> result = new LinkedHashMap<>();
                for (TsKvEntry entry : data) {
                    List<TsData> vList = result.get(entry.getKey());
                    if (vList == null) {
                        vList = new ArrayList<>();
                        result.put(entry.getKey(), vList);
                    }
                    vList.add(new TsData(entry.getTs(), entry.getValueAsString()));
                }
                msg.getResponseHolder().setResult(new ResponseEntity<>(result, HttpStatus.OK));
            }

            @Override
            public void onFailure(PluginContext ctx, Exception e) {
                log.error("Failed to fetch historical data", e);
                handleError(e, msg, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        };
    }

    private void handleError(Exception e, PluginRestMsg msg, HttpStatus defaultErrorStatus) {
        ResponseEntity responseEntity;
        if (e != null && e instanceof ToErrorResponseEntity) {
            responseEntity = ((ToErrorResponseEntity)e).toErrorResponseEntity();
        } else {
            responseEntity = new ResponseEntity<>(defaultErrorStatus);
        }
        msg.getResponseHolder().setResult(responseEntity);
    }

}
