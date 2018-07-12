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
package org.thingsboard.server.actors.shared;

import akka.actor.ActorContext;
import akka.event.LoggingAdapter;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.thingsboard.server.actors.ActorSystemContext;
import org.thingsboard.server.actors.stats.StatsPersistTick;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.plugin.ComponentLifecycleState;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.cluster.ClusterEventMsg;
import org.thingsboard.server.service.queue.MsgQueueService;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public abstract class ComponentMsgProcessor<T extends EntityId> extends AbstractContextAwareMsgProcessor {

    protected final TenantId tenantId;
    protected final T entityId;
    protected final MsgQueueService queue;
    protected ComponentLifecycleState state;

    protected ComponentMsgProcessor(ActorSystemContext systemContext, LoggingAdapter logger, TenantId tenantId, T id) {
        super(systemContext, logger);
        this.tenantId = tenantId;
        this.entityId = id;
        this.queue = systemContext.getMsgQueueService();
    }

    public abstract void start(ActorContext context) throws Exception;

    public abstract void stop(ActorContext context) throws Exception;

    public abstract void onClusterEventMsg(ClusterEventMsg msg) throws Exception;

    public void onCreated(ActorContext context) throws Exception {
        start(context);
    }

    public void onUpdate(ActorContext context) throws Exception {
        restart(context);
    }

    public void onActivate(ActorContext context) throws Exception {
        restart(context);
    }

    public void onSuspend(ActorContext context) throws Exception {
        stop(context);
    }

    public void onStop(ActorContext context) throws Exception {
        stop(context);
    }

    private void restart(ActorContext context) throws Exception {
        stop(context);
        start(context);
    }

    public void scheduleStatsPersistTick(ActorContext context, long statsPersistFrequency) {
        schedulePeriodicMsgWithDelay(context, new StatsPersistTick(), statsPersistFrequency, statsPersistFrequency);
    }

    protected void checkActive() {
        if (state != ComponentLifecycleState.ACTIVE) {
            throw new IllegalStateException("Rule chain is not active!");
        }
    }

    protected void putToQueue(final TbMsg tbMsg, final Consumer<TbMsg> onSuccess) {
        EntityId entityId = tbMsg.getRuleNodeId() != null ? tbMsg.getRuleNodeId() : tbMsg.getRuleChainId();
        Futures.addCallback(queue.put(this.tenantId, tbMsg, entityId.getId(), tbMsg.getClusterPartition()), new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                onSuccess.accept(tbMsg);
            }

            @Override
            public void onFailure(Throwable t) {
                logger.debug("Failed to push message [{}] to queue due to [{}]", tbMsg, t);
            }
        });
    }
}
