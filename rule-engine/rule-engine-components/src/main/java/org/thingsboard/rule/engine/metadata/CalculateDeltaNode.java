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
package org.thingsboard.rule.engine.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.common.util.DonAsynchron;
import org.thingsboard.rule.engine.api.RuleNode;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNode;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.TbRelationTypes;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.rule.engine.telemetry.TbMsgTimeseriesNode;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.session.SessionMsgType;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.common.util.JacksonUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RuleNode(type = ComponentType.ENRICHMENT,
        name = "calculate delta", relationTypes = {"Success", "Failure", "Other"},
        configClazz = CalculateDeltaNodeConfiguration.class,
        nodeDescription = "Calculates and adds 'delta' value into message based on the incoming and previous value",
        nodeDetails = "Calculates delta and period based on the previous time-series reading and current data. " +
                "Delta calculation is done in scope of the message originator, e.g. device, asset or customer. " +
                "If there is input key, the output relation will be 'Success' unless delta is negative and corresponding configuration parameter is set. " +
                "If there is no input value key in the incoming message, the output relation will be 'Other'.",
        uiResources = {"static/rulenode/rulenode-core-config.js"},
        configDirective = "tbEnrichmentNodeCalculateDeltaConfig")
public class CalculateDeltaNode implements TbNode {
    private Map<EntityId, ValueWithTs> cache;
    private CalculateDeltaNodeConfiguration config;
    private TbContext ctx;
    private TimeseriesService timeseriesService;
    private boolean useCache;

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        this.config = TbNodeUtils.convert(configuration, CalculateDeltaNodeConfiguration.class);
        this.ctx = ctx;
        this.timeseriesService = ctx.getTimeseriesService();
        this.useCache = config.isUseCache();

        if (useCache) {
            cache = new ConcurrentHashMap<>();
        }
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) {
        if (msg.getType().equals(SessionMsgType.POST_TELEMETRY_REQUEST.name())) {
            JsonNode json = JacksonUtil.toJsonNode(msg.getData());
            String inputKey = config.getInputValueKey();
            if (json.has(inputKey)) {
                DonAsynchron.withCallback(getLastValue(msg.getOriginator()),
                        previousData -> {
                            double currentValue = json.get(inputKey).asDouble();
                            long currentTs = TbMsgTimeseriesNode.getTs(msg);

                            if (useCache) {
                                cache.put(msg.getOriginator(), new ValueWithTs(currentTs, currentValue));
                            }

                            BigDecimal delta = BigDecimal.valueOf(previousData != null ? currentValue - previousData.value : 0.0);

                            if (config.isTellFailureIfDeltaIsNegative() && delta.doubleValue() < 0) {
                                ctx.tellNext(msg, TbRelationTypes.FAILURE);
                                return;
                            }


                            if (config.getRound() != null) {
                                delta = delta.setScale(config.getRound(), RoundingMode.HALF_UP);
                            }

                            ObjectNode result = (ObjectNode) json;
                            if (delta.stripTrailingZeros().scale() > 0) {
                                result.put(config.getOutputValueKey(), delta.doubleValue());
                            } else {
                                result.put(config.getOutputValueKey(), delta.longValueExact());
                            }

                            if (config.isAddPeriodBetweenMsgs()) {
                                long period = previousData != null ? currentTs - previousData.ts : 0;
                                result.put(config.getPeriodValueKey(), period);
                            }
                            ctx.tellSuccess(TbMsg.transformMsg(msg, msg.getType(), msg.getOriginator(), msg.getMetaData(), JacksonUtil.toString(result)));
                        },
                        t -> ctx.tellFailure(msg, t), ctx.getDbCallbackExecutor());
            } else {
                ctx.tellNext(msg, "Other");
            }
        } else {
            ctx.tellNext(msg, "Other");
        }
    }

    @Override
    public void destroy() {
        if (useCache) {
            cache.clear();
        }
    }

    private ListenableFuture<ValueWithTs> fetchLatestValue(EntityId entityId) {
        return Futures.transform(timeseriesService.findLatest(ctx.getTenantId(), entityId, Collections.singletonList(config.getInputValueKey())),
                list -> extractValue(list.get(0))
                , ctx.getDbCallbackExecutor());
    }

    private ListenableFuture<ValueWithTs> getLastValue(EntityId entityId) {
        ValueWithTs latestValue;
        if (useCache && (latestValue = cache.get(entityId)) != null) {
            return Futures.immediateFuture(latestValue);
        } else {
            return fetchLatestValue(entityId);
        }
    }

    private ValueWithTs extractValue(TsKvEntry kvEntry) {
        if (kvEntry == null || kvEntry.getValue() == null) {
            return null;
        }
        double result = 0.0;
        long ts = kvEntry.getTs();
        switch (kvEntry.getDataType()) {
            case LONG:
                result = kvEntry.getLongValue().get();
                break;
            case DOUBLE:
                result = kvEntry.getDoubleValue().get();
                break;
            case STRING:
                try {
                    result = Double.parseDouble(kvEntry.getStrValue().get());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Calculation failed. Unable to parse value [" + kvEntry.getStrValue().get() + "]" +
                            " of telemetry [" + kvEntry.getKey() + "] to Double");
                }
                break;
            case BOOLEAN:
                throw new IllegalArgumentException("Calculation failed. Boolean values are not supported!");
            case JSON:
                throw new IllegalArgumentException("Calculation failed. JSON values are not supported!");
        }
        return new ValueWithTs(ts, result);
    }

    private static class ValueWithTs {
        private final long ts;
        private final double value;

        private ValueWithTs(long ts, double value) {
            this.ts = ts;
            this.value = value;
        }
    }
}
