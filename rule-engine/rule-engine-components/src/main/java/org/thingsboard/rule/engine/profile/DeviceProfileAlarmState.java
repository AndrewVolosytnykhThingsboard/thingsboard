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
package org.thingsboard.rule.engine.profile;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import org.thingsboard.rule.engine.action.TbAlarmResult;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.profile.state.PersistedAlarmRuleState;
import org.thingsboard.rule.engine.profile.state.PersistedAlarmState;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.alarm.AlarmStatus;
import org.thingsboard.server.common.data.device.profile.DeviceProfileAlarm;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.common.msg.queue.ServiceQueue;
import org.thingsboard.server.dao.util.mapping.JacksonUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;

@Data
class DeviceProfileAlarmState {

    private final EntityId originator;
    private DeviceProfileAlarm alarmDefinition;
    private volatile List<AlarmRuleState> createRulesSortedBySeverityDesc;
    private volatile AlarmRuleState clearState;
    private volatile Alarm currentAlarm;
    private volatile boolean initialFetchDone;
    private volatile TbMsgMetaData lastMsgMetaData;
    private volatile String lastMsgQueueName;

    public DeviceProfileAlarmState(EntityId originator, DeviceProfileAlarm alarmDefinition, PersistedAlarmState alarmState) {
        this.originator = originator;
        this.updateState(alarmDefinition, alarmState);
    }

    public boolean process(TbContext ctx, TbMsg msg, DeviceDataSnapshot data) throws ExecutionException, InterruptedException {
        initCurrentAlarm(ctx);
        lastMsgMetaData = msg.getMetaData();
        lastMsgQueueName = msg.getQueueName();
        return createOrClearAlarms(ctx, data, AlarmRuleState::eval);
    }

    public boolean process(TbContext ctx, long ts) throws ExecutionException, InterruptedException {
        initCurrentAlarm(ctx);
        return createOrClearAlarms(ctx, ts, AlarmRuleState::eval);
    }

    public <T> boolean createOrClearAlarms(TbContext ctx, T data, BiFunction<AlarmRuleState, T, Boolean> evalFunction) {
        boolean stateUpdate = false;
        AlarmSeverity resultSeverity = null;
        for (AlarmRuleState state : createRulesSortedBySeverityDesc) {
            boolean evalResult = evalFunction.apply(state, data);
            stateUpdate |= state.checkUpdate();
            if (evalResult) {
                resultSeverity = state.getSeverity();
                break;
            }
        }
        if (resultSeverity != null) {
            pushMsg(ctx, calculateAlarmResult(ctx, resultSeverity));
        } else if (currentAlarm != null && clearState != null) {
            Boolean evalResult = evalFunction.apply(clearState, data);
            if (evalResult) {
                stateUpdate |= clearState.checkUpdate();
                ctx.getAlarmService().clearAlarm(ctx.getTenantId(), currentAlarm.getId(), JacksonUtil.OBJECT_MAPPER.createObjectNode(), System.currentTimeMillis());
                pushMsg(ctx, new TbAlarmResult(false, false, true, currentAlarm));
                currentAlarm = null;
            }
        }
        return stateUpdate;
    }

    public void initCurrentAlarm(TbContext ctx) throws InterruptedException, ExecutionException {
        if (!initialFetchDone) {
            Alarm alarm = ctx.getAlarmService().findLatestByOriginatorAndType(ctx.getTenantId(), originator, alarmDefinition.getAlarmType()).get();
            if (alarm != null && !alarm.getStatus().isCleared()) {
                currentAlarm = alarm;
            }
            initialFetchDone = true;
        }
    }

    public void pushMsg(TbContext ctx, TbAlarmResult alarmResult) {
        JsonNode jsonNodes = JacksonUtil.valueToTree(alarmResult.getAlarm());
        String data = jsonNodes.toString();
        TbMsgMetaData metaData = lastMsgMetaData != null ? lastMsgMetaData.copy() : new TbMsgMetaData();
        String relationType;
        if (alarmResult.isCreated()) {
            relationType = "Alarm Created";
            metaData.putValue(DataConstants.IS_NEW_ALARM, Boolean.TRUE.toString());
        } else if (alarmResult.isUpdated()) {
            relationType = "Alarm Updated";
            metaData.putValue(DataConstants.IS_EXISTING_ALARM, Boolean.TRUE.toString());
        } else if (alarmResult.isSeverityUpdated()) {
            relationType = "Alarm Severity Updated";
            metaData.putValue(DataConstants.IS_EXISTING_ALARM, Boolean.TRUE.toString());
            metaData.putValue(DataConstants.IS_SEVERITY_UPDATED_ALARM, Boolean.TRUE.toString());
        } else {
            relationType = "Alarm Cleared";
            metaData.putValue(DataConstants.IS_CLEARED_ALARM, Boolean.TRUE.toString());
        }
        TbMsg newMsg = ctx.newMsg(lastMsgQueueName != null ? lastMsgQueueName : ServiceQueue.MAIN, "ALARM", originator, metaData, data);
        ctx.tellNext(newMsg, relationType);
    }

    public void updateState(DeviceProfileAlarm alarm, PersistedAlarmState alarmState) {
        this.alarmDefinition = alarm;
        this.createRulesSortedBySeverityDesc = new ArrayList<>();
        alarmDefinition.getCreateRules().forEach((severity, rule) -> {
            PersistedAlarmRuleState ruleState = null;
            if (alarmState != null) {
                ruleState = alarmState.getCreateRuleStates().get(severity);
                if (ruleState == null) {
                    ruleState = new PersistedAlarmRuleState();
                    alarmState.getCreateRuleStates().put(severity, ruleState);
                }
            }
            createRulesSortedBySeverityDesc.add(new AlarmRuleState(severity, rule, ruleState));
        });
        createRulesSortedBySeverityDesc.sort(Comparator.comparingInt(state -> state.getSeverity().ordinal()));
        PersistedAlarmRuleState ruleState = alarmState == null ? null : alarmState.getClearRuleState();
        if (alarmDefinition.getClearRule() != null) {
            clearState = new AlarmRuleState(null, alarmDefinition.getClearRule(), ruleState);
        }
    }

    private TbAlarmResult calculateAlarmResult(TbContext ctx, AlarmSeverity severity) {
        if (currentAlarm != null) {
            currentAlarm.setEndTs(System.currentTimeMillis());
            AlarmSeverity oldSeverity = currentAlarm.getSeverity();
            if (!oldSeverity.equals(severity)) {
                currentAlarm.setSeverity(severity);
                currentAlarm = ctx.getAlarmService().createOrUpdateAlarm(currentAlarm);
                return new TbAlarmResult(false, false, true, false, currentAlarm);
            } else {
                currentAlarm = ctx.getAlarmService().createOrUpdateAlarm(currentAlarm);
                return new TbAlarmResult(false, true, false, false, currentAlarm);
            }
        } else {
            currentAlarm = new Alarm();
            currentAlarm.setType(alarmDefinition.getAlarmType());
            currentAlarm.setStatus(AlarmStatus.ACTIVE_UNACK);
            currentAlarm.setSeverity(severity);
            currentAlarm.setStartTs(System.currentTimeMillis());
            currentAlarm.setEndTs(currentAlarm.getStartTs());
            currentAlarm.setDetails(JacksonUtil.OBJECT_MAPPER.createObjectNode());
            currentAlarm.setOriginator(originator);
            currentAlarm.setTenantId(ctx.getTenantId());
            currentAlarm.setPropagate(alarmDefinition.isPropagate());
            if (alarmDefinition.getPropagateRelationTypes() != null) {
                currentAlarm.setPropagateRelationTypes(alarmDefinition.getPropagateRelationTypes());
            }
            currentAlarm = ctx.getAlarmService().createOrUpdateAlarm(currentAlarm);
            boolean updated = currentAlarm.getStartTs() != currentAlarm.getEndTs();
            return new TbAlarmResult(!updated, updated, false, false, currentAlarm);
        }
    }

    public boolean processAlarmClear(TbContext ctx, Alarm alarmNf) {
        boolean updated = false;
        if (currentAlarm != null && currentAlarm.getId().equals(alarmNf.getId())) {
            currentAlarm = null;
            for (AlarmRuleState state : createRulesSortedBySeverityDesc) {
                state.clear();
                updated |= state.checkUpdate();
            }
        }
        return updated;
    }
}
