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
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.dao.group.EntityGroupService;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.thingsboard.common.util.DonAsynchron.withCallback;

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
        EntityId ownerId = ctx.getPeContext().getOwner(ctx.getTenantId(), msg.getOriginator());
        if(ownerId ==  null) {
            ownerId = ctx.getTenantId();
        }
        ListenableFuture<EntityGroupId> entityGroupIdFeature = getEntityGroup(ctx, msg, ownerId);
        return Futures.transform(entityGroupIdFeature, entityGroupId -> {
                    doProcessEntityGroupAction(ctx, msg, entityGroupId);
                    return null;
                }, ctx.getDbCallbackExecutor()
        );
    }

    protected abstract void doProcessEntityGroupAction(TbContext ctx, TbMsg msg, EntityGroupId entityGroupId);

    private ListenableFuture<EntityGroupId> getEntityGroup(TbContext ctx, TbMsg msg, EntityId ownerId) {
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
