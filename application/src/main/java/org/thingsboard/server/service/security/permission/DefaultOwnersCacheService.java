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
package org.thingsboard.server.service.security.permission;

import com.datastax.driver.core.utils.UUIDs;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.*;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.*;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.permission.MergedGroupTypePermissionInfo;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.permission.Resource;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.customer.CustomerService;
import org.thingsboard.server.dao.dashboard.DashboardService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.entityview.EntityViewService;
import org.thingsboard.server.dao.group.EntityGroupService;
import org.thingsboard.server.dao.role.RoleService;
import org.thingsboard.server.dao.scheduler.SchedulerEventService;
import org.thingsboard.server.dao.user.UserService;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private EntityViewService entityViewService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private SchedulerEventService schedulerEventService;

    @Autowired
    protected UserPermissionsService userPermissionsService;


    @Override
    public Set<EntityId> fetchOwners(TenantId tenantId, EntityId ownerId) {
        Set<EntityId> result = new HashSet<>();
        fetchOwners(tenantId, ownerId, result);
        return result;
    }

    @Override
    public Set<EntityId> getOwners(TenantId tenantId, EntityId entityId, HasOwnerId hasOwnerId) {
        return getOwners(tenantId, entityId, id -> hasOwnerId);
    }

    @Override
    public Set<EntityId> getOwners(TenantId tenantId, EntityGroupId entityGroupId) {
        return getOwners(tenantId, entityGroupId, id -> entityGroupService.findEntityGroupById(tenantId, new EntityGroupId(id.getId())));
    }

    @Override
    public EntityId getOwner(TenantId tenantId, EntityId entityId) {
        Cache cache = cacheManager.getCache(ENTITY_OWNERS_CACHE);
        String cacheKey = getOwnerCacheKey(entityId);
        byte[] data = cache.get(cacheKey, byte[].class);
        EntityId ownerId = null;
        if (data != null && data.length > 0) {
            try {
                ownerId = bytesToOwner(data);
            } catch (InvalidProtocolBufferException e) {
                log.warn("[{}][{}] Failed to decode owner id from cache: {}", tenantId, entityId, Arrays.toString(data));
            }
        }
        if (ownerId == null) {
            ownerId = fetchOwnerId(tenantId, entityId);
            cache.put(getOwnerCacheKey(entityId), toBytes(ownerId));
        }
        return ownerId;
    }

    @Override
    public void clearOwners(EntityId entityId) {
        Cache cache = cacheManager.getCache(ENTITY_OWNERS_CACHE);
        cache.evict(getOwnersCacheKey(entityId));
        cache.evict(getOwnerCacheKey(entityId));
    }

    @Override
    public Set<EntityId> getChildOwners(TenantId tenantId, EntityId parentOwnerId) {
        Set<EntityId> result = new HashSet<>();
        try {
            fetchChildOwners(tenantId, parentOwnerId, result);
        } catch (Exception e) {
            log.error("Failed to get child owners by parentOwnerId [{}]", parentOwnerId, e);
            throw new RuntimeException(e);
        }
        return result;
    }

    @Override
    public void changeDashboardOwner(TenantId tenantId, EntityId targetOwnerId,Dashboard dashboard) throws ThingsboardException {
        changeEntityOwner(tenantId, targetOwnerId, dashboard.getId(),
                dashboard,
                dashboardService::saveDashboard);
    }

    @Override
    public void changeUserOwner(TenantId tenantId, EntityId targetOwnerId, User user) throws ThingsboardException {
        userPermissionsService.onUserUpdatedOrRemoved(user);
        changeEntityOwner(tenantId, targetOwnerId, user.getId(), user, targetUser -> {
            if (EntityType.CUSTOMER.equals(targetOwnerId.getEntityType())) {
                targetUser.setAuthority(Authority.CUSTOMER_USER);
            }
            if (EntityType.TENANT.equals(targetOwnerId.getEntityType())) {
                targetUser.setAuthority(Authority.TENANT_ADMIN);
            }
            userService.saveUser(targetUser);
        });
    }

    @Override
    public void changeCustomerOwner(TenantId tenantId, EntityId targetOwnerId, Customer customer) throws ThingsboardException {
        Set<EntityId> ownerIds = getChildOwners(tenantId, customer.getId());
        if (!ownerIds.contains(targetOwnerId)) {
            changeEntityOwner(tenantId, targetOwnerId, customer.getId(),
                    customer,
                    customerService::saveCustomer);
        } else {
            // Making Sub-Customer as a Parent Customer - NOT OK.
            throw new ThingsboardException("Owner of the Customer can't be changed to its Sub-Customer!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
    }

    @Override
    public void changeEntityViewOwner(TenantId tenantId, EntityId targetOwnerId, EntityView entityView) throws ThingsboardException {
        changeEntityOwner(tenantId, targetOwnerId, entityView.getId(), entityView, entityViewService::saveEntityView);
    }

    @Override
    public void changeAssetOwner(TenantId tenantId, EntityId targetOwnerId, Asset asset) throws ThingsboardException {
        changeEntityOwner(tenantId, targetOwnerId, asset.getId(), asset, assetService::saveAsset);
    }

    @Override
    public void changeDeviceOwner(TenantId tenantId, EntityId targetOwnerId, Device device) throws ThingsboardException {
        changeEntityOwner(tenantId, targetOwnerId, device.getId(), device, deviceService::saveDevice);
    }

    @Override
    public void changeEntityOwner(TenantId tenantId, EntityId targetOwnerId, EntityId entityId, EntityType entityType) throws ThingsboardException {
        switch (entityType) {
            case DEVICE:
                changeDeviceOwner(tenantId, targetOwnerId, getDeviceById(tenantId, entityId));
                break;
            case ASSET:
                changeAssetOwner(tenantId, targetOwnerId, getAssetById(tenantId, entityId));
                break;
            case CUSTOMER:
                changeCustomerOwner(tenantId, targetOwnerId, getCustomerById(tenantId, entityId));
                break;
            case USER:
                changeUserOwner(tenantId, targetOwnerId, getUserById(tenantId, entityId));
                break;
            case DASHBOARD:
                changeDashboardOwner(tenantId, targetOwnerId, getDashboardById(tenantId, entityId));
                break;
            case ENTITY_VIEW:
                changeEntityViewOwner(tenantId, targetOwnerId, getEntityViewById(tenantId, entityId));
                break;
            default:
                throw new RuntimeException("EntityType does not support owner change: " + entityId.getEntityType());
        }
    }

    @Override
    public boolean isChildOwner(TenantId tenantId, CustomerId parentOwnerId, CustomerId childOwnerId) {
        return getChildOwners(tenantId, parentOwnerId).stream().anyMatch(childOwnerId::equals);
    }

    @Override
    public <E extends SearchTextBased<? extends UUIDBased>, I extends EntityId> TextPageData<E>
    getGroupEntitiesByPageLink(TenantId tenantId, SecurityUser securityUser, EntityType entityType, Operation operation,
                               Function<EntityId, I> toIdFunction, Function<List<I>, List<E>> toEntitiesFunction,
                               List<Predicate<E>> entityFilters, List<I> additionalEntityIds, TextPageLink pageLink)
            throws Exception {
        Resource resource = Resource.resourceFromEntityType(entityType);
        if (securityUser.getAuthority() == Authority.TENANT_ADMIN &&
                securityUser.getUserPermissions().hasGenericPermission(resource, operation)) {
            switch (entityType) {
                case DEVICE:
                    return (TextPageData<E>) deviceService.findDevicesByTenantId(tenantId, pageLink);
                case ASSET:
                    return (TextPageData<E>) assetService.findAssetsByTenantId(tenantId, pageLink);
                case CUSTOMER:
                    return (TextPageData<E>) customerService.findCustomersByTenantId(tenantId, pageLink);
                case USER:
                    return (TextPageData<E>) userService.findUsersByTenantId(tenantId, pageLink);
                case DASHBOARD:
                    return (TextPageData<E>) dashboardService.findDashboardsByTenantId(tenantId, pageLink);
                case ENTITY_VIEW:
                    return (TextPageData<E>) entityViewService.findEntityViewByTenantId(tenantId, pageLink);
                default:
                    throw new RuntimeException("EntityType does not supported: " + entityType);
            }
        } else {
            List<I> entityIds = getEntityIdsFromAllowedGroups(tenantId, securityUser, entityType, operation, toIdFunction);
            entityIds.addAll(additionalEntityIds);

            return loadAndFilterEntities(entityIds, toEntitiesFunction, entityFilters, pageLink);
        }
    }

    @Override
    public <E extends SearchTextBased<? extends UUIDBased>, I extends EntityId> TextPageData<E>
    loadAndFilterEntities(List<I> entityIds, Function<List<I>, List<E>> toEntitiesFunction, List<Predicate<E>> entityFilters,
                          TextPageLink pageLink) {
        List<E> entities;
        if (entityIds.isEmpty()) {
            entities = Collections.emptyList();
        } else {
            entities = toEntitiesFunction.apply(entityIds);
        }
        Stream<E> entitiesStream = entities.stream().sorted(entityComparator());
        for (Predicate<E> entityFilter : entityFilters) {
            entitiesStream = entitiesStream.filter(entityFilter);
        }
        entities = entitiesStream.filter(entityPageLinkFilter(pageLink)).collect(Collectors.toList());
        if (pageLink.getLimit() > 0 && entities.size() > pageLink.getLimit()) {
            int toRemove = entities.size() - pageLink.getLimit();
            entities.subList(entities.size() - toRemove, entities.size()).clear();
        }
        return new TextPageData<>(entities, pageLink);
    }

    @Override
    public Comparator<SearchTextBased<? extends UUIDBased>> entityComparator(){
        return entityComparator;
    }

    @Override
    public Predicate<SearchTextBased<? extends UUIDBased>> entityPageLinkFilter(TextPageLink pageLink) {
        return new EntityPageLinkFilter(pageLink);
    }

    private final Comparator<SearchTextBased<? extends UUIDBased>> entityComparator = (e1, e2) -> {
        int result = e1.getSearchText().compareToIgnoreCase(e2.getSearchText());
        if (result == 0) {
            result = (int)(e2.getCreatedTime() - e1.getCreatedTime());
        }
        return result;
    };

    private static class EntityPageLinkFilter implements Predicate<SearchTextBased<? extends UUIDBased>> {

        private final String textSearch;
        private final String textOffset;
        private final long createdTimeOffset;

        EntityPageLinkFilter(TextPageLink pageLink) {
            if (!StringUtils.isEmpty(pageLink.getTextSearch())) {
                this.textSearch = pageLink.getTextSearch().toLowerCase();
            } else {
                this.textSearch = "";
            }
            if (!StringUtils.isEmpty(pageLink.getTextOffset())) {
                this.textOffset = pageLink.getTextOffset();
            } else {
                this.textOffset = "";
            }
            if (pageLink.getIdOffset() != null) {
                createdTimeOffset = UUIDs.unixTimestamp(pageLink.getIdOffset());
            } else {
                createdTimeOffset = Long.MAX_VALUE;
            }
        }

        @Override
        public boolean test(SearchTextBased<? extends UUIDBased> searchTextBased) {
            if (textOffset.length() > 0) {
                int result = searchTextBased.getSearchText().compareToIgnoreCase(textOffset);
                if (result == 0 && searchTextBased.getCreatedTime() < createdTimeOffset) {
                    return true;
                } else if (result > 0 && searchTextBased.getSearchText().toLowerCase().startsWith(textSearch)) {
                    return true;
                }
            } else if (textSearch.length() > 0) {
                return searchTextBased.getSearchText().toLowerCase().startsWith(textSearch);
            } else {
                return true;
            }
            return false;
        }
    }

    private  <I extends EntityId> List<I> getEntityIdsFromAllowedGroups(TenantId tenantId,
                                                                        SecurityUser securityUser,
                                                                        EntityType entityType,
                                                                        Operation operation,
                                                                        Function<EntityId, I> toIdFunction) throws Exception {
        MergedGroupTypePermissionInfo groupTypePermissionInfo = null;
        if (operation == Operation.READ) {
            groupTypePermissionInfo = securityUser.getUserPermissions().getReadGroupPermissions().get(entityType);
        }
        Resource resource = Resource.resourceFromEntityType(entityType);
        if (securityUser.getUserPermissions().hasGenericPermission(resource, operation) ||
                (groupTypePermissionInfo != null && !groupTypePermissionInfo.getEntityGroupIds().isEmpty())) {

            Set<EntityId> entityIds = new HashSet<>();
            Set<EntityGroupId> groupIds = new HashSet<>();
            if (securityUser.getUserPermissions().hasGenericPermission(resource, operation)) {
                Set<EntityId> ownerIds = getChildOwners(tenantId, securityUser.getOwnerId());
                for (EntityId ownerId : ownerIds) {
                    Optional<EntityGroup> entityGroup = entityGroupService.findEntityGroupByTypeAndName(tenantId, ownerId,
                            entityType, EntityGroup.GROUP_ALL_NAME).get();
                    if (entityGroup.isPresent()) {
                        groupIds.add(entityGroup.get().getId());
                    }
                }
            }
            if (groupTypePermissionInfo != null && !groupTypePermissionInfo.getEntityGroupIds().isEmpty()) {
                groupIds.addAll(groupTypePermissionInfo.getEntityGroupIds());
            }
            for (EntityGroupId groupId : groupIds) {
                entityIds.addAll(entityGroupService.findAllEntityIds(tenantId, groupId, new TimePageLink(Integer.MAX_VALUE)).get());
            }
            if (!entityIds.isEmpty()) {
                List<I> entityIdsList = new ArrayList<>();
                entityIds.forEach((entityId) -> entityIdsList.add(toIdFunction.apply(entityId)));
                return entityIdsList;
            } else {
                return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }
    }

    private EntityId fetchOwnerId(TenantId tenantId, EntityId entityId) {
        switch (entityId.getEntityType()) {
            case DEVICE:
                return getOwnerId(getDeviceById(tenantId, entityId));
            case ASSET:
                return getOwnerId(getAssetById(tenantId, entityId));
            case CUSTOMER:
                return getOwnerId(getCustomerById(tenantId, entityId));
            case ENTITY_VIEW:
                return getOwnerId(getEntityViewById(tenantId, entityId));
            case DASHBOARD:
                return getOwnerId(getDashboardById(tenantId, entityId));
            case USER:
                return getOwnerId(getUserById(tenantId, entityId));
            case ENTITY_GROUP:
                return getOwnerId(entityGroupService.findEntityGroupById(tenantId, new EntityGroupId(entityId.getId())));
            case ROLE:
                return getOwnerId(roleService.findRoleById(tenantId, new RoleId(entityId.getId())));
            case SCHEDULER_EVENT:
                return getOwnerId(schedulerEventService.findSchedulerEventById(tenantId, new SchedulerEventId(entityId.getId())));
            default:
                // Maybe return tenantId here?
                throw new RuntimeException("EntityType does not support ownership: " + entityId.getEntityType());
        }
    }

    private Device getDeviceById(TenantId tenantId, EntityId entityId) {
        return deviceService.findDeviceById(tenantId, new DeviceId(entityId.getId()));
    }

    private Asset getAssetById(TenantId tenantId, EntityId entityId) {
        return assetService.findAssetById(tenantId, new AssetId(entityId.getId()));
    }

    private Customer getCustomerById(TenantId tenantId, EntityId entityId) {
        return customerService.findCustomerById(tenantId, new CustomerId(entityId.getId()));
    }

    private User getUserById(TenantId tenantId, EntityId entityId) {
        return userService.findUserById(tenantId, new UserId(entityId.getId()));
    }

    private Dashboard getDashboardById(TenantId tenantId, EntityId entityId) {
        return dashboardService.findDashboardById(tenantId, new DashboardId(entityId.getId()));
    }

    private EntityView getEntityViewById(TenantId tenantId, EntityId entityId) {
        return entityViewService.findEntityViewById(tenantId, new EntityViewId(entityId.getId()));
    }

    private EntityId getOwnerId(HasOwnerId entity) {
        return entity != null ? entity.getOwnerId() : null;
    }

    private Set<EntityId> getOwners(TenantId tenantId, EntityId entityId, Function<EntityId, HasOwnerId> fetchHasOwnerId) {
        Cache cache = cacheManager.getCache(ENTITY_OWNERS_CACHE);
        String cacheKey = getOwnersCacheKey(entityId);
        byte[] data = cache.get(cacheKey, byte[].class);
        Set<EntityId> result = null;
        if (data != null && data.length > 0) {
            try {
                result = bytesToOwners(data);
            } catch (InvalidProtocolBufferException e) {
                log.warn("[{}][{}] Failed to decode owners list from cache: {}", tenantId, entityId, Arrays.toString(data));
            }
        }
        if (result == null) {
            HasOwnerId hasOwnerId = fetchHasOwnerId.apply(entityId);
            result = new HashSet<>();
            fetchOwners(tenantId, hasOwnerId.getOwnerId(), result);
            cache.put(getOwnerCacheKey(entityId), toBytes(hasOwnerId.getOwnerId()));
            cache.put(cacheKey, toBytes(result));
        }
        return result;
    }

    private String getOwnersCacheKey(EntityId entityId) {
        return ENTITY_OWNERS_CACHE + "_" + entityId.getId().toString();
    }

    private String getOwnerCacheKey(EntityId entityId) {
        return ENTITY_OWNERS_CACHE + "_owner_" + entityId.getId().toString();
    }

    private void fetchOwners(TenantId tenantId, EntityId entityId, Set<EntityId> result) {
        result.add(entityId);
        if (entityId.getEntityType() == EntityType.CUSTOMER) {
            Customer customer = getCustomerById(tenantId, entityId);
            fetchOwners(tenantId, customer.getOwnerId(), result);
        }
    }

    private EntityId bytesToOwner(byte[] data) throws InvalidProtocolBufferException {
        TransportProtos.EntityIdProto proto = TransportProtos.EntityIdProto.parseFrom(data);
        return EntityIdFactory.getByTypeAndUuid(proto.getEntityType(), new UUID(proto.getEntityIdMSB(), proto.getEntityIdLSB()));
    }

    private Set<EntityId> bytesToOwners(byte[] data) throws InvalidProtocolBufferException {
        TransportProtos.OwnersListProto proto = TransportProtos.OwnersListProto.parseFrom(data);
        return proto.getEntityIdsList().stream().map(entityIdProto ->
                EntityIdFactory.getByTypeAndUuid(entityIdProto.getEntityType(),
                        new UUID(entityIdProto.getEntityIdMSB(), entityIdProto.getEntityIdLSB()))).collect(Collectors.toSet());
    }

    private byte[] toBytes(EntityId entityId) {
        return TransportProtos.EntityIdProto.newBuilder()
                .setEntityIdMSB(entityId.getId().getMostSignificantBits())
                .setEntityIdLSB(entityId.getId().getLeastSignificantBits())
                .setEntityType(entityId.getEntityType().name()).build().toByteArray();
    }

    private byte[] toBytes(Set<EntityId> result) {
        TransportProtos.OwnersListProto.Builder builder = TransportProtos.OwnersListProto.newBuilder();
        builder.addAllEntityIds(result.stream().map(entityId ->
                TransportProtos.EntityIdProto.newBuilder()
                        .setEntityIdMSB(entityId.getId().getMostSignificantBits())
                        .setEntityIdLSB(entityId.getId().getLeastSignificantBits())
                        .setEntityType(entityId.getEntityType().name()).build()).collect(Collectors.toList()));
        return builder.build().toByteArray();
    }

    private void fetchChildOwners(TenantId tenantId, EntityId entityId, Set<EntityId> result) throws Exception {
        result.add(entityId);
        Optional<EntityGroup> entityGroup = entityGroupService.findEntityGroupByTypeAndName(tenantId, entityId, EntityType.CUSTOMER, EntityGroup.GROUP_ALL_NAME).get();
        if (entityGroup.isPresent()) {
            List<EntityId> childOwnerIds = entityGroupService.findAllEntityIds(tenantId, entityGroup.get().getId(), new TimePageLink(Integer.MAX_VALUE)).get();
            for (EntityId ownerId : childOwnerIds) {
                fetchChildOwners(tenantId, ownerId, result);
            }
        }
    }

    private <T extends HasOwnerId> void changeEntityOwner(TenantId tenantId, EntityId targetOwnerId, EntityId entityId, T entity, Consumer<T> saveFunction)
            throws ThingsboardException {
        if (entity.getOwnerId().equals(targetOwnerId)) {
            throw new ThingsboardException("Entity already belongs to this owner!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }

        List<EntityGroupId> entityGroupList = null;
        try {
            entityGroupList = entityGroupService.findEntityGroupsForEntity(tenantId, entityId).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new ThingsboardException(e, ThingsboardErrorCode.GENERAL);
        }
        for (EntityGroupId entityGroupId : entityGroupList) {
            entityGroupService.removeEntityFromEntityGroup(tenantId, entityGroupId, entityId);
        }

        entityGroupService.addEntityToEntityGroupAll(tenantId, targetOwnerId, entityId);

        entity.setOwnerId(targetOwnerId);
        saveFunction.accept(entity);
        clearOwners(entityId);
    }
}
