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
package org.thingsboard.server.service.security.permission;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.customer.CustomerService;
import org.thingsboard.server.dao.group.EntityGroupService;
import org.thingsboard.server.gen.cluster.ClusterAPIProtos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.thingsboard.server.common.data.CacheConstants.ENTITY_OWNERS_CACHE;

@Slf4j
@Service
public class DefaultOwnersCacheService implements OwnersCacheService {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private EntityGroupService entityGroupService;

    @Autowired
    private CustomerService customerService;

    @Override
    public List<EntityId> getOwners(TenantId tenantId, EntityGroup entityGroup) throws Exception {
        return getOwners(tenantId, entityGroup.getId(), id -> entityGroup);
    }

    @Override
    public List<EntityId> getOwners(TenantId tenantId, EntityGroupId entityGroupId) throws Exception {
        return getOwners(tenantId, entityGroupId, id -> entityGroupService.findEntityGroupById(tenantId, id));
    }

    private List<EntityId> getOwners(TenantId tenantId, EntityGroupId entityGroupId, Function<EntityGroupId, EntityGroup> fetchEntityGroup) throws Exception {
        Cache cache = cacheManager.getCache(ENTITY_OWNERS_CACHE);
        String cacheKey = entityGroupId.getId().toString();
        byte[] data = cache.get(cacheKey, byte[].class);
        List<EntityId> result = null;
        if (data != null) {
            try {
                result = fromBytes(data);
            } catch (InvalidProtocolBufferException e) {
                log.warn("[{}][{}] Failed to decode owners list from cache: {}", tenantId, entityGroupId, Arrays.toString(data));
            }
        }
        if (result == null) {
            EntityGroup entityGroup = fetchEntityGroup.apply(entityGroupId);
            result = new ArrayList<>();
            //TODO: convert entityGroup to OwnerId;
            fetchOwners(tenantId, new CustomerId(UUID.randomUUID()), result);
            cache.put(cacheKey, toBytes(result));
        }
        return result;
    }

    private void fetchOwners(TenantId tenantId, EntityId entityId, List<EntityId> result) {
        result.add(entityId);
        if (entityId.getEntityType() == EntityType.CUSTOMER) {
            Customer customer = customerService.findCustomerById(tenantId, new CustomerId(entityId.getId()));
            fetchOwners(tenantId, customer.getOwnerId(), result);
        }
    }

    private List<EntityId> fromBytes(byte[] data) throws InvalidProtocolBufferException {
        ClusterAPIProtos.OwnersListProto proto = ClusterAPIProtos.OwnersListProto.parseFrom(data);
        return proto.getEntityIdsList().stream().map(entityIdProto ->
                EntityIdFactory.getByTypeAndUuid(entityIdProto.getEntityType(),
                        new UUID(entityIdProto.getEntityIdMSB(), entityIdProto.getEntityIdLSB()))).collect(Collectors.toList());
    }

    private byte[] toBytes(List<EntityId> result) {
        ClusterAPIProtos.OwnersListProto.Builder builder = ClusterAPIProtos.OwnersListProto.newBuilder();
        builder.addAllEntityIds(result.stream().map(entityId ->
                ClusterAPIProtos.EntityIdProto.newBuilder()
                        .setEntityIdMSB(entityId.getId().getMostSignificantBits())
                        .setEntityIdLSB(entityId.getId().getLeastSignificantBits())
                        .setEntityType(entityId.getEntityType().name()).build()).collect(Collectors.toList()));
        return builder.build().toByteArray();
    }

    @Override
    public void clearOwners(EntityGroupId entityGroupId) throws Exception {
        Cache cache = cacheManager.getCache(ENTITY_OWNERS_CACHE);
        cache.evict(entityGroupId.getId().toString());
    }
}
