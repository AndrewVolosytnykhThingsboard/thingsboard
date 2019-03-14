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
package org.thingsboard.rule.engine.action;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNode;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.dao.group.EntityGroupService;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.thingsboard.rule.engine.api.util.DonAsynchron.withCallback;

@Slf4j
public abstract class TbAbstractGroupActionNode<C extends TbAbstractGroupActionConfigration> implements TbNode {

    protected C config;

    private LoadingCache<GroupKey, Optional<EntityGroupId>> groupIdCache;

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        this.config = loadGroupNodeActionConfig(configuration);
        CacheBuilder cacheBuilder = CacheBuilder.newBuilder();
        if (this.config.getGroupCacheExpiration() > 0) {
            cacheBuilder.expireAfterWrite(this.config.getGroupCacheExpiration(), TimeUnit.SECONDS);
        }
        groupIdCache = cacheBuilder
                .build(new EntityGroupCacheLoader(ctx, createGroupIfNotExists()));
    }

    protected abstract boolean createGroupIfNotExists();

    protected abstract C loadGroupNodeActionConfig(TbNodeConfiguration configuration) throws TbNodeException;

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) {
        withCallback(processEntityGroupAction(ctx, msg),
                m -> ctx.tellNext(msg, "Success"),
                t -> ctx.tellFailure(msg, t), ctx.getDbCallbackExecutor());
    }

    private ListenableFuture<Void> processEntityGroupAction(TbContext ctx, TbMsg msg) {
        EntityType originatorType = msg.getOriginator().getEntityType();
        if (originatorType != EntityType.CUSTOMER && originatorType != EntityType.ASSET && originatorType != EntityType.DEVICE && originatorType != EntityType.ENTITY_VIEW && originatorType != EntityType.DASHBOARD) {
             throw new RuntimeException("Unsupported originator type '" + originatorType +
                    "'! Only 'CUSTOMER', 'USER', 'ASSET', 'DEVICE', 'ENTITY_VIEW' or 'DASHBOARD' types are allowed.");
        }
        EntityId ownerId = getOwnerId(ctx, msg, originatorType);
        ListenableFuture<EntityGroupId> entityGroupIdFeature = getEntityGroup(ctx, msg, ownerId);
        return Futures.transform(entityGroupIdFeature, entityGroupId -> {
                    doProcessEntityGroupAction(ctx, msg, entityGroupId);
                    return null;
                }, ctx.getDbCallbackExecutor()
        );
    }

    private EntityId getOwnerId(TbContext ctx, TbMsg msg, EntityType originatorType) {
        switch (originatorType) {
            case DEVICE:
                Device device = ctx.getDeviceService().findDeviceById(ctx.getTenantId(),  new DeviceId(msg.getOriginator().getId()));
                return device.getOwnerId();
            case ASSET:
                Asset asset = ctx.getAssetService().findAssetById(ctx.getTenantId(),  new AssetId(msg.getOriginator().getId()));
                return asset.getOwnerId();
            case CUSTOMER:
                Customer customer = ctx.getCustomerService().findCustomerById(ctx.getTenantId(),  new CustomerId(msg.getOriginator().getId()));
                return customer.getOwnerId();
            case ENTITY_VIEW:
                EntityView entityView = ctx.getEntityViewService().findEntityViewById(ctx.getTenantId(),  new EntityViewId(msg.getOriginator().getId()));
                return entityView.getOwnerId();
            case DASHBOARD:
                Dashboard dashboard = ctx.getDashboardService().findDashboardById(ctx.getTenantId(),  new DashboardId(msg.getOriginator().getId()));
                return dashboard.getOwnerId();
            default:
                User user = ctx.getUserService().findUserById(ctx.getTenantId(), new UserId(msg.getOriginator().getId()));
                return user.getOwnerId();
        }
    }

    protected abstract void doProcessEntityGroupAction(TbContext ctx, TbMsg msg, EntityGroupId entityGroupId);

    protected ListenableFuture<EntityGroupId> getEntityGroup(TbContext ctx, TbMsg msg, EntityId ownerId) {
        String groupName = TbNodeUtils.processPattern(this.config.getGroupNamePattern(), msg.getMetaData());
        GroupKey key = new GroupKey(msg.getOriginator().getEntityType(), groupName, ownerId);
        return ctx.getDbCallbackExecutor().executeAsync(() -> {
            Optional<EntityGroupId> groupId = groupIdCache.get(key);
            if (!groupId.isPresent()) {
                throw new RuntimeException("No entity group found with type '" + key.getGroupType() + " ' and name '" + key.getGroupName() + "'.");
            }
            return groupId.get();
        });
    }

    @Override
    public void destroy() {
    }

    @Data
    @AllArgsConstructor
    private static class GroupKey {
        private EntityType groupType;
        private String groupName;
        private EntityId ownerId;
    }

    private static class EntityGroupCacheLoader extends CacheLoader<GroupKey, Optional<EntityGroupId>> {

        private final TbContext ctx;
        private final boolean createIfNotExists;

        private EntityGroupCacheLoader(TbContext ctx, boolean createIfNotExists) {
            this.ctx = ctx;
            this.createIfNotExists = createIfNotExists;
        }

        @Override
        public Optional<EntityGroupId> load(GroupKey key) throws Exception {
            EntityGroupService service = ctx.getPeContext().getEntityGroupService();
            Optional<EntityGroup> entityGroup =
                    service.findEntityGroupByTypeAndName(ctx.getTenantId(), key.getOwnerId(), key.getGroupType(), key.getGroupName()).get();
            if (entityGroup.isPresent()) {
                return Optional.of(entityGroup.get().getId());
            } else if (createIfNotExists) {
                EntityGroup newGroup = new EntityGroup();
                newGroup.setName(key.getGroupName());
                newGroup.setType(key.getGroupType());
                return Optional.of(service.saveEntityGroup(ctx.getTenantId(), key.getOwnerId(), newGroup).getId());
            }
            return Optional.empty();
        }
    }

}
