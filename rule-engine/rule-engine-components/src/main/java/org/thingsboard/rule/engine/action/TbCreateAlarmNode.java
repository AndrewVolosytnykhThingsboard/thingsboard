/**
 * Thingsboard OÜ ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2018 Thingsboard OÜ. All Rights Reserved.
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
package org.thingsboard.rule.engine.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.rule.engine.api.RuleNode;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmStatus;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.msg.TbMsg;

@Slf4j
@RuleNode(
        type = ComponentType.ACTION,
        name = "create alarm", relationTypes = {"Created", "Updated", "False"},
        configClazz = TbCreateAlarmNodeConfiguration.class,
        nodeDescription = "Create or Update Alarm",
        nodeDetails =
                "Details - JS function that creates JSON object based on incoming message. This object will be added into Alarm.details field.\n" +
                "Node output:\n" +
                "If alarm was not created, original message is returned. Otherwise new Message returned with type 'ALARM', Alarm object in 'msg' property and 'matadata' will contains one of those properties 'isNewAlarm/isExistingAlarm'. " +
                "Message payload can be accessed via <code>msg</code> property. For example <code>'temperature = ' + msg.temperature ;</code>. " +
                "Message metadata can be accessed via <code>metadata</code> property. For example <code>'name = ' + metadata.customerName;</code>.",
        uiResources = {"static/rulenode/rulenode-core-config.js"},
        configDirective = "tbActionNodeCreateAlarmConfig",
        icon = "notifications_active"
)
public class TbCreateAlarmNode extends TbAbstractAlarmNode<TbCreateAlarmNodeConfiguration> {

    @Override
    protected TbCreateAlarmNodeConfiguration loadAlarmNodeConfig(TbNodeConfiguration configuration) throws TbNodeException {
        return TbNodeUtils.convert(configuration, TbCreateAlarmNodeConfiguration.class);
    }

    @Override
    protected ListenableFuture<AlarmResult> processAlarm(TbContext ctx, TbMsg msg) {
        ListenableFuture<Alarm> latest = ctx.getAlarmService().findLatestByOriginatorAndType(ctx.getTenantId(), msg.getOriginator(), config.getAlarmType());
        return Futures.transformAsync(latest, a -> {
            if (a == null || a.getStatus().isCleared()) {
                return createNewAlarm(ctx, msg);
            } else {
                return updateAlarm(ctx, msg, a);
            }
        }, ctx.getDbCallbackExecutor());

    }

    private ListenableFuture<AlarmResult> createNewAlarm(TbContext ctx, TbMsg msg) {
        ListenableFuture<Alarm> asyncAlarm = Futures.transform(buildAlarmDetails(ctx, msg, null),
                details -> buildAlarm(msg, details, ctx.getTenantId()));
        ListenableFuture<Alarm> asyncCreated = Futures.transform(asyncAlarm,
                alarm -> ctx.getAlarmService().createOrUpdateAlarm(alarm), ctx.getDbCallbackExecutor());
        return Futures.transform(asyncCreated, alarm -> new AlarmResult(true, false, false, alarm));
    }

    private ListenableFuture<AlarmResult> updateAlarm(TbContext ctx, TbMsg msg, Alarm alarm) {
        ListenableFuture<Alarm> asyncUpdated = Futures.transform(buildAlarmDetails(ctx, msg, alarm.getDetails()), (Function<JsonNode, Alarm>) details -> {
            alarm.setSeverity(config.getSeverity());
            alarm.setPropagate(config.isPropagate());
            alarm.setDetails(details);
            alarm.setEndTs(System.currentTimeMillis());
            return ctx.getAlarmService().createOrUpdateAlarm(alarm);
        }, ctx.getDbCallbackExecutor());

        return Futures.transform(asyncUpdated, a -> new AlarmResult(false, true, false, a));
    }

    private Alarm buildAlarm(TbMsg msg, JsonNode details, TenantId tenantId) {
        return Alarm.builder()
                .tenantId(tenantId)
                .originator(msg.getOriginator())
                .status(AlarmStatus.ACTIVE_UNACK)
                .severity(config.getSeverity())
                .propagate(config.isPropagate())
                .type(config.getAlarmType())
                //todo-vp: alarm date should be taken from Message or current Time should be used?
//                .startTs(System.currentTimeMillis())
//                .endTs(System.currentTimeMillis())
                .details(details)
                .build();
    }

}
