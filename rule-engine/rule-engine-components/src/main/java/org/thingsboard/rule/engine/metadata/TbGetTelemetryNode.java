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
package org.thingsboard.rule.engine.metadata;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.rule.engine.api.*;
import org.thingsboard.rule.engine.api.util.DonAsynchron;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.server.common.data.kv.BaseReadTsKvQuery;
import org.thingsboard.server.common.data.kv.ReadTsKvQuery;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.msg.TbMsg;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.thingsboard.rule.engine.api.TbRelationTypes.SUCCESS;
import static org.thingsboard.rule.engine.metadata.TbGetTelemetryNodeConfiguration.*;
import static org.thingsboard.server.common.data.kv.Aggregation.NONE;

/**
 * Created by mshvayka on 04.09.18.
 */
@Slf4j
@RuleNode(type = ComponentType.ENRICHMENT,
        name = "originator telemetry",
        configClazz = TbGetTelemetryNodeConfiguration.class,
        nodeDescription = "Add Message Originator Telemetry for selected time range into Message Metadata\n",
        nodeDetails = "The node allows you to select fetch mode <b>FIRST/LAST/ALL</b> to fetch telemetry of certain time range that are added into Message metadata without any prefix. " +
                "If selected fetch mode <b>ALL</b> Telemetry will be added like array into Message Metadata where <b>key</b> is Timestamp and <b>value</b> is value of Telemetry. " +
                "If selected fetch mode <b>FIRST</b> or <b>LAST</b> Telemetry will be added like string without Timestamp",
        uiResources = {"static/rulenode/rulenode-core-config.js"},
        configDirective = "tbEnrichmentNodeGetTelemetryFromDatabase")
public class TbGetTelemetryNode implements TbNode {

    private TbGetTelemetryNodeConfiguration config;
    private List<String> tsKeyNames;
    private long startTsOffset;
    private long endTsOffset;
    private int limit;
    private ObjectMapper mapper;
    private String fetchMode;

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        this.config = TbNodeUtils.convert(configuration, TbGetTelemetryNodeConfiguration.class);
        tsKeyNames = config.getLatestTsKeyNames();
        startTsOffset = TimeUnit.valueOf(config.getStartIntervalTimeUnit()).toMillis(config.getStartInterval());
        endTsOffset = TimeUnit.valueOf(config.getEndIntervalTimeUnit()).toMillis(config.getEndInterval());
        limit = config.getFetchMode().equals(FETCH_MODE_ALL) ? MAX_FETCH_SIZE : 1;
        fetchMode = config.getFetchMode();
        mapper = new ObjectMapper();
        mapper.configure(JsonGenerator.Feature.QUOTE_FIELD_NAMES, false);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) throws ExecutionException, InterruptedException, TbNodeException {
        if (tsKeyNames.isEmpty()) {
            ctx.tellFailure(msg, new IllegalStateException("Telemetry is not selected!"));
        } else {
            try {
                List<ReadTsKvQuery> queries = buildQueries();
                ListenableFuture<List<TsKvEntry>> list = ctx.getTimeseriesService().findAll(ctx.getTenantId(), msg.getOriginator(), queries);
                DonAsynchron.withCallback(list, data -> {
                    process(data, msg);
                    TbMsg newMsg = ctx.transformMsg(msg, msg.getType(), msg.getOriginator(), msg.getMetaData(), msg.getData());
                    ctx.tellNext(newMsg, SUCCESS);
                }, error -> ctx.tellFailure(msg, error), ctx.getDbCallbackExecutor());
            } catch (Exception e) {
                ctx.tellFailure(msg, e);
            }
        }
    }

    private List<ReadTsKvQuery> buildQueries() {
        long ts = System.currentTimeMillis();
        long startTs = ts - startTsOffset;
        long endTs = ts - endTsOffset;
        String orderBy;
        if (fetchMode.equals(FETCH_MODE_FIRST) || fetchMode.equals(FETCH_MODE_ALL)) {
            orderBy = "ASC";
        } else {
            orderBy = "DESC";
        }
        return tsKeyNames.stream()
                .map(key -> new BaseReadTsKvQuery(key, startTs, endTs, 1, limit, NONE, orderBy))
                .collect(Collectors.toList());
    }

    private void process(List<TsKvEntry> entries, TbMsg msg) {
        ObjectNode resultNode = mapper.createObjectNode();
        if (limit == MAX_FETCH_SIZE) {
            entries.forEach(entry -> processArray(resultNode, entry));
        } else {
            entries.forEach(entry -> processSingle(resultNode, entry));
        }

        for (String key : tsKeyNames) {
            if (resultNode.has(key)) {
                msg.getMetaData().putValue(key, resultNode.get(key).toString());
            }
        }
    }

    private void processSingle(ObjectNode node, TsKvEntry entry) {
        node.put(entry.getKey(), entry.getValueAsString());
    }

    private void processArray(ObjectNode node, TsKvEntry entry) {
        if (node.has(entry.getKey())) {
            ArrayNode arrayNode = (ArrayNode) node.get(entry.getKey());
            ObjectNode obj = buildNode(entry);
            arrayNode.add(obj);
        } else {
            ArrayNode arrayNode = mapper.createArrayNode();
            ObjectNode obj = buildNode(entry);
            arrayNode.add(obj);
            node.set(entry.getKey(), arrayNode);
        }
    }

    private ObjectNode buildNode(TsKvEntry entry) {
        ObjectNode obj = mapper.createObjectNode()
                .put("ts", entry.getTs());
        switch (entry.getDataType()) {
            case STRING:
                obj.put("value", entry.getValueAsString());
                break;
            case LONG:
                obj.put("value", entry.getLongValue().get());
                break;
            case BOOLEAN:
                obj.put("value", entry.getBooleanValue().get());
                break;
            case DOUBLE:
                obj.put("value", entry.getDoubleValue().get());
                break;
        }
        return obj;
    }

    @Override
    public void destroy() {

    }
}
