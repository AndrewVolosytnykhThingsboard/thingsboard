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
package org.thingsboard.server.service.queue;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.msg.queue.RuleEngineException;
import org.thingsboard.server.gen.transport.TransportProtos.ToRuleEngineMsg;
import org.thingsboard.server.queue.common.TbProtoQueueMsg;
import org.thingsboard.server.service.queue.processing.TbRuleEngineProcessingResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Data
public class TbRuleEngineConsumerStats {

    public static final String TOTAL_MSGS = "totalMsgs";
    public static final String SUCCESSFUL_MSGS = "successfulMsgs";
    public static final String TMP_TIMEOUT = "tmpTimeout";
    public static final String TMP_FAILED = "tmpFailed";
    public static final String TIMEOUT_MSGS = "timeoutMsgs";
    public static final String FAILED_MSGS = "failedMsgs";
    public static final String SUCCESSFUL_ITERATIONS = "successfulIterations";
    public static final String FAILED_ITERATIONS = "failedIterations";

    private final AtomicInteger totalMsgCounter = new AtomicInteger(0);
    private final AtomicInteger successMsgCounter = new AtomicInteger(0);
    private final AtomicInteger tmpTimeoutMsgCounter = new AtomicInteger(0);
    private final AtomicInteger tmpFailedMsgCounter = new AtomicInteger(0);

    private final AtomicInteger timeoutMsgCounter = new AtomicInteger(0);
    private final AtomicInteger failedMsgCounter = new AtomicInteger(0);

    private final AtomicInteger successIterationsCounter = new AtomicInteger(0);
    private final AtomicInteger failedIterationsCounter = new AtomicInteger(0);

    private final Map<String, AtomicInteger> counters = new HashMap<>();
    private final ConcurrentMap<UUID, TbTenantRuleEngineStats> tenantStats = new ConcurrentHashMap<>();
    private final ConcurrentMap<TenantId, RuleEngineException> tenantExceptions = new ConcurrentHashMap<>();

    private final String queueName;

    public TbRuleEngineConsumerStats(String queueName) {
        this.queueName = queueName;
        counters.put(TOTAL_MSGS, totalMsgCounter);
        counters.put(SUCCESSFUL_MSGS, successMsgCounter);
        counters.put(TIMEOUT_MSGS, timeoutMsgCounter);
        counters.put(FAILED_MSGS, failedMsgCounter);

        counters.put(TMP_TIMEOUT, tmpTimeoutMsgCounter);
        counters.put(TMP_FAILED, tmpFailedMsgCounter);
        counters.put(SUCCESSFUL_ITERATIONS, successIterationsCounter);
        counters.put(FAILED_ITERATIONS, failedIterationsCounter);
    }

    public void log(TbRuleEngineProcessingResult msg, boolean finalIterationForPack) {
        int success = msg.getSuccessMap().size();
        int pending = msg.getPendingMap().size();
        int failed = msg.getFailedMap().size();
        totalMsgCounter.addAndGet(success + pending + failed);
        successMsgCounter.addAndGet(success);
        msg.getSuccessMap().values().forEach(m -> getTenantStats(m).logSuccess());
        if (finalIterationForPack) {
            if (pending > 0 || failed > 0) {
                timeoutMsgCounter.addAndGet(pending);
                failedMsgCounter.addAndGet(failed);
                if (pending > 0) {
                    msg.getPendingMap().values().forEach(m -> getTenantStats(m).logTimeout());
                }
                if (failed > 0) {
                    msg.getFailedMap().values().forEach(m -> getTenantStats(m).logFailed());
                }
                failedIterationsCounter.incrementAndGet();
            } else {
                successIterationsCounter.incrementAndGet();
            }
        } else {
            failedIterationsCounter.incrementAndGet();
            tmpTimeoutMsgCounter.addAndGet(pending);
            tmpFailedMsgCounter.addAndGet(failed);
            if (pending > 0) {
                msg.getPendingMap().values().forEach(m -> getTenantStats(m).logTmpTimeout());
            }
            if (failed > 0) {
                msg.getFailedMap().values().forEach(m -> getTenantStats(m).logTmpFailed());
            }
        }
        msg.getExceptionsMap().forEach(tenantExceptions::putIfAbsent);
    }

    private TbTenantRuleEngineStats getTenantStats(TbProtoQueueMsg<ToRuleEngineMsg> m) {
        ToRuleEngineMsg reMsg = m.getValue();
        return tenantStats.computeIfAbsent(new UUID(reMsg.getTenantIdMSB(), reMsg.getTenantIdLSB()), TbTenantRuleEngineStats::new);
    }

    public void printStats() {
        int total = totalMsgCounter.get();
        if (total > 0) {
            StringBuilder stats = new StringBuilder();
            counters.forEach((label, value) -> {
                stats.append(label).append(" = [").append(value.get()).append("] ");
            });
            log.info("[{}] Stats: {}", queueName, stats);
        }
    }

    public void reset() {
        counters.values().forEach(counter -> counter.set(0));
        tenantStats.clear();
        tenantExceptions.clear();
    }
}