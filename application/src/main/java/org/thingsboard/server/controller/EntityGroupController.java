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
package org.thingsboard.server.controller;

import com.google.common.util.concurrent.ListenableFuture;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.ContactBased;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.ShortEntityView;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.group.EntityGroupInfo;
import org.thingsboard.server.common.data.id.*;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.page.TimePageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.permission.MergedGroupTypePermissionInfo;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.permission.Resource;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.security.permission.OwnersCacheService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.thingsboard.server.dao.service.Validator.validateEntityId;
import static org.thingsboard.server.dao.service.Validator.validateId;

@RestController
@RequestMapping("/api")
public class EntityGroupController extends BaseController {

    public static final String ENTITY_GROUP_ID = "entityGroupId";

    @Autowired
    private OwnersCacheService ownersCacheService;

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}", method = RequestMethod.GET)
    @ResponseBody
    public EntityGroupInfo getEntityGroupById(@PathVariable(ENTITY_GROUP_ID) String strEntityGroupId) throws ThingsboardException {
        checkParameter(ENTITY_GROUP_ID, strEntityGroupId);
        try {
            EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
            return toEntityGroupInfo(checkEntityGroupId(entityGroupId, Operation.READ));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup", method = RequestMethod.POST)
    @ResponseBody
    public EntityGroupInfo saveEntityGroup(@RequestBody EntityGroup entityGroup) throws ThingsboardException {
        try {
            checkEntityGroupType(entityGroup.getType());

            Operation operation = entityGroup.getId() == null ? Operation.CREATE : Operation.WRITE;

            EntityId parentEntityId = entityGroup.getOwnerId();
            if (operation == Operation.CREATE) {
                if (parentEntityId == null || parentEntityId.isNullUid()) {
                    parentEntityId = getCurrentUser().getOwnerId();
                } else {
                    if (!ownersCacheService.fetchOwners(getTenantId(), parentEntityId).contains(getCurrentUser().getOwnerId())) {
                        throw new ThingsboardException("Unable to create entity group: " +
                                "Invalid entity group ownerId!", ThingsboardErrorCode.PERMISSION_DENIED);
                    }
                }
            } else {
                validateEntityId(parentEntityId, "Incorrect entity group ownerId " + parentEntityId);
            }

            accessControlService.checkEntityGroupPermission(getCurrentUser(), operation, entityGroup);

            EntityGroup savedEntityGroup = checkNotNull(entityGroupService.saveEntityGroup(getTenantId(), parentEntityId, entityGroup));

            logEntityAction(savedEntityGroup.getId(), savedEntityGroup,
                    null,
                    entityGroup.getId() == null ? ActionType.ADDED : ActionType.UPDATED, null);

            return toEntityGroupInfo(savedEntityGroup);
        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.ENTITY_GROUP), entityGroup,
                    null, entityGroup.getId() == null ? ActionType.ADDED : ActionType.UPDATED, e);
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}", method = RequestMethod.DELETE)
    @ResponseStatus(value = HttpStatus.OK)
    public void deleteEntityGroup(@PathVariable(ENTITY_GROUP_ID) String strEntityGroupId) throws ThingsboardException {
        checkParameter(ENTITY_GROUP_ID, strEntityGroupId);
        try {
            EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
            EntityGroup entityGroup = checkEntityGroupId(entityGroupId, Operation.DELETE);
            if (entityGroup.isGroupAll()) {
                throw new ThingsboardException("Unable to remove entity group: " +
                        "Removal of entity group 'All' is forbidden!", ThingsboardErrorCode.PERMISSION_DENIED);
            }
            entityGroupService.deleteEntityGroup(getTenantId(), entityGroupId);

            logEntityAction(entityGroupId, entityGroup,
                    null,
                    ActionType.DELETED, null, strEntityGroupId);

        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.ENTITY_GROUP),
                    null,
                    null,
                    ActionType.DELETED, e, strEntityGroupId);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroups/{groupType}", method = RequestMethod.GET)
    @ResponseBody
    public List<EntityGroupInfo> getEntityGroupsByType(
            @ApiParam(value = "EntityGroup type", required = true, allowableValues = "CUSTOMER,ASSET,DEVICE,USER,ENTITY_VIEW,DASHBOARD") @PathVariable("groupType") String strGroupType) throws ThingsboardException {
        try {
            EntityType groupType = checkStrEntityGroupType("groupType", strGroupType);
            MergedGroupTypePermissionInfo groupTypePermissionInfo = getCurrentUser().getUserPermissions().getReadGroupPermissions().get(groupType);
            if (groupTypePermissionInfo.isHasGenericRead() || !groupTypePermissionInfo.getEntityGroupIds().isEmpty()) {
                List<EntityGroup> groups = new ArrayList<>();
                if (groupTypePermissionInfo.isHasGenericRead()) {
                    EntityId parentEntityId = getCurrentUser().isTenantAdmin() ? getCurrentUser().getTenantId() : getCurrentUser().getCustomerId();
                    groups.addAll(entityGroupService.findEntityGroupsByType(getTenantId(), parentEntityId, groupType).get());
                }
                if (!groupTypePermissionInfo.getEntityGroupIds().isEmpty()) {
                    List<EntityGroupId> existingIds = groups.stream().map(EntityGroup::getId).collect(Collectors.toList());
                    List<EntityGroupId> groupIds = groupTypePermissionInfo.getEntityGroupIds().stream().filter(entityGroupId ->
                        !existingIds.contains(entityGroupId)
                    ).collect(Collectors.toList());
                    if (!groupIds.isEmpty()) {
                        groups.addAll(entityGroupService.findEntityGroupByIdsAsync(getTenantId(), groupIds).get());
                    }
                }
                return toEntityGroupsInfo(groups);
            } else {
                throw permissionDenied();
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroups/{ownerType}/{ownerId}/{groupType}", method = RequestMethod.GET)
    @ResponseBody
    public List<EntityGroupInfo> getEntityGroupsByOwnerAndType(
            @PathVariable("ownerType") String strOwnerType,
            @PathVariable("ownerId") String strOwnerId,
            @ApiParam(value = "EntityGroup type", required = true, allowableValues = "CUSTOMER,ASSET,DEVICE,USER,ENTITY_VIEW,DASHBOARD") @PathVariable("groupType") String strGroupType) throws ThingsboardException {
        checkParameter("ownerId", strOwnerId);
        checkParameter("ownerType", strOwnerType);
        try {
            EntityId ownerId = EntityIdFactory.getByTypeAndId(strOwnerType, strOwnerId);
            EntityType groupType = checkStrEntityGroupType("groupType", strGroupType);
            checkEntityId(ownerId, Operation.READ);
            MergedGroupTypePermissionInfo groupTypePermissionInfo = getCurrentUser().getUserPermissions().getReadGroupPermissions().get(groupType);
            if (groupTypePermissionInfo.isHasGenericRead()) {
                return toEntityGroupsInfo(entityGroupService.findEntityGroupsByType(getTenantId(), ownerId, groupType).get());
            } else {
                throw permissionDenied();
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private List<EntityGroupInfo> toEntityGroupsInfo(List<EntityGroup> entityGroups) throws ThingsboardException {
        List<EntityGroupInfo> entityGroupsInfo = new ArrayList<>(entityGroups.size());
        for (EntityGroup entityGroup : entityGroups) {
            entityGroupsInfo.add(toEntityGroupInfo(entityGroup));
        }
        return entityGroupsInfo;
    }

    private EntityGroupInfo toEntityGroupInfo(EntityGroup entityGroup) throws ThingsboardException {
        EntityGroupInfo entityGroupInfo = new EntityGroupInfo(entityGroup);
        entityGroupInfo.setOwnerIds(ownersCacheService.getOwners(getTenantId(), entityGroup.getId(), entityGroup));
        return entityGroupInfo;
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}/addEntities", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public void addEntitiesToEntityGroup(@PathVariable(ENTITY_GROUP_ID) String strEntityGroupId,
                                         @RequestBody String[] strEntityIds) throws ThingsboardException {
        checkParameter(ENTITY_GROUP_ID, strEntityGroupId);
        checkArrayParameter("entityIds", strEntityIds);
        EntityGroup entityGroup = null;
        try {
            EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
            entityGroup = checkEntityGroupId(entityGroupId, Operation.ADD_TO_GROUP);
            if (entityGroup.isGroupAll()) {
                throw new ThingsboardException("Unable to add entities to entity group: " +
                        "Addition to entity group 'All' is forbidden!", ThingsboardErrorCode.PERMISSION_DENIED);
            }
            List<EntityId> entityIds = new ArrayList<>();
            for (String strEntityId : strEntityIds) {
                EntityId entityId = EntityIdFactory.getByTypeAndId(entityGroup.getType(), strEntityId);
                checkEntityId(entityId, Operation.READ);
                entityIds.add(entityId);
            }
            entityGroupService.addEntitiesToEntityGroup(getTenantId(), entityGroupId, entityIds);
            for (EntityId entityId : entityIds) {
                logEntityAction((UUIDBased & EntityId)entityId, null,
                        null,
                        ActionType.ADDED_TO_ENTITY_GROUP, null, entityId.toString(), strEntityGroupId, entityGroup.getName());
            }
        } catch (Exception e) {
            if (entityGroup != null) {
                EntityType entityType = entityGroup.getType();
                String groupName = entityGroup.getName();
                for (String strEntityId : strEntityIds) {
                    logEntityAction(emptyId(entityType), null,
                            null,
                            ActionType.ADDED_TO_ENTITY_GROUP, e, strEntityId, strEntityGroupId, groupName);
                }
            }
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}/deleteEntities", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public void removeEntitiesFromEntityGroup(@PathVariable(ENTITY_GROUP_ID) String strEntityGroupId,
                                              @RequestBody String[] strEntityIds) throws ThingsboardException {
        checkParameter(ENTITY_GROUP_ID, strEntityGroupId);
        checkArrayParameter("entityIds", strEntityIds);
        EntityGroup entityGroup = null;
        try {
            EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
            entityGroup = checkEntityGroupId(entityGroupId, Operation.REMOVE_FROM_GROUP);
            if (entityGroup.isGroupAll()) {
                throw new ThingsboardException("Unable to remove entities from entity group: " +
                        "Removal from entity group 'All' is forbidden!", ThingsboardErrorCode.PERMISSION_DENIED);
            }
            List<EntityId> entityIds = new ArrayList<>();
            for (String strEntityId : strEntityIds) {
                EntityId entityId = EntityIdFactory.getByTypeAndId(entityGroup.getType(), strEntityId);
                checkEntityId(entityId, Operation.READ);
                entityIds.add(entityId);
            }
            entityGroupService.removeEntitiesFromEntityGroup(getTenantId(), entityGroupId, entityIds);
            for (EntityId entityId : entityIds) {
                logEntityAction((UUIDBased & EntityId)entityId, null,
                        null,
                        ActionType.REMOVED_FROM_ENTITY_GROUP, null, entityId.toString(), strEntityGroupId, entityGroup.getName());
            }
        } catch (Exception e) {
            if (entityGroup != null) {
                EntityType entityType = entityGroup.getType();
                String groupName = entityGroup.getName();
                for (String strEntityId : strEntityIds) {
                    logEntityAction(emptyId(entityType), null,
                            null,
                            ActionType.REMOVED_FROM_ENTITY_GROUP, e, strEntityId, strEntityGroupId, groupName);
                }
            }
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}/{entityId}", method = RequestMethod.GET)
    @ResponseBody
    public ShortEntityView getGroupEntity(
            @PathVariable(ENTITY_GROUP_ID) String strEntityGroupId,
            @PathVariable("entityId") String strEntityId) throws ThingsboardException {
        checkParameter(ENTITY_GROUP_ID, strEntityGroupId);
        checkParameter("entityId", strEntityId);

        try {
            EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
            EntityGroup entityGroup = checkEntityGroupId(entityGroupId, Operation.READ);
            EntityType entityType = entityGroup.getType();
            checkEntityGroupType(entityType);
            EntityId entityId = EntityIdFactory.getByTypeAndId(entityType, strEntityId);
            checkEntityId(entityId, Operation.READ);
            ShortEntityView result = null;
            if (entityType == EntityType.CUSTOMER) {
                result = customerService.findGroupCustomer(getTenantId(), entityGroupId, entityId);
            } else if (entityType == EntityType.ASSET) {
                result = assetService.findGroupAsset(getTenantId(), entityGroupId, entityId);
            } else if (entityType == EntityType.DEVICE) {
                result = deviceService.findGroupDevice(getTenantId(), entityGroupId, entityId);
            } else if (entityType == EntityType.USER) {
                result = userService.findGroupUser(getTenantId(), entityGroupId, entityId);
            } else if (entityType == EntityType.ENTITY_VIEW) {
                result = entityViewService.findGroupEntityView(getTenantId(), entityGroupId, entityId);
            } else if (entityType == EntityType.DASHBOARD) {
                result = dashboardService.findGroupDashboard(getTenantId(), entityGroupId, entityId);
            }
            return checkNotNull(result);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}/entities", method = RequestMethod.GET)
    @ResponseBody
    public TimePageData<ShortEntityView> getEntities(
            @PathVariable(ENTITY_GROUP_ID) String strEntityGroupId,
            @ApiParam(value = "Page link limit", required = true, allowableValues = "range[1, infinity]") @RequestParam int limit,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false, defaultValue = "false") boolean ascOrder,
            @RequestParam(required = false) String offset
    ) throws ThingsboardException {
        checkParameter(ENTITY_GROUP_ID, strEntityGroupId);
        EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
        EntityGroup entityGroup = checkEntityGroupId(entityGroupId, Operation.READ);
        EntityType entityType = entityGroup.getType();
        checkEntityGroupType(entityType);
        try {
            TimePageLink pageLink = createPageLink(limit, startTime, endTime, ascOrder, offset);
            ListenableFuture<TimePageData<ShortEntityView>> asyncResult = null;
            if (entityType == EntityType.CUSTOMER) {
                asyncResult = customerService.findCustomersByEntityGroupId(getTenantId(), entityGroupId, pageLink);
            } else if (entityType == EntityType.ASSET) {
                asyncResult = assetService.findAssetsByEntityGroupId(getTenantId(), entityGroupId, pageLink);
            } else if (entityType == EntityType.DEVICE) {
                asyncResult = deviceService.findDevicesByEntityGroupId(getTenantId(), entityGroupId, pageLink);
            } else if (entityType == EntityType.USER) {
                asyncResult = userService.findUsersByEntityGroupId(getTenantId(), entityGroupId, pageLink);
            } else if (entityType == EntityType.ENTITY_VIEW) {
                asyncResult = entityViewService.findEntityViewsByEntityGroupId(getTenantId(), entityGroupId, pageLink);
            } else if (entityType == EntityType.DASHBOARD) {
                asyncResult = dashboardService.findDashboardsByEntityGroupId(getTenantId(), entityGroupId, pageLink);
            }
            checkNotNull(asyncResult);
            if (asyncResult != null) {
                return checkNotNull(asyncResult.get());
            } else {
                throw new ThingsboardException("Requested item wasn't found!", ThingsboardErrorCode.ITEM_NOT_FOUND);
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroups/{entityType}/{entityId}", method = RequestMethod.GET)
    @ResponseBody
    public List<EntityGroupId> getEntityGroupsForEntity(
            @ApiParam(value = "Entity type", required = true, allowableValues = "CUSTOMER,ASSET,DEVICE,USER,ENTITY_VIEW,DASHBOARD") @PathVariable("entityType") String strEntityType,
            @PathVariable("entityId") String strEntityId) throws ThingsboardException {
        checkParameter("entityType", strEntityType);
        checkParameter("entityId", strEntityId);
        try {
            EntityType entityType = checkStrEntityGroupType("entityType", strEntityType);
            EntityId entityId = EntityIdFactory.getByTypeAndId(entityType, strEntityId);
            checkEntityId(entityId, Operation.READ);
            return checkNotNull(entityGroupService.findEntityGroupsForEntity(getTenantId(), entityId).get());
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroups", params = {"entityGroupIds"}, method = RequestMethod.GET)
    @ResponseBody
    public List<EntityGroup> getEntityGroupsByIds(
            @RequestParam("entityGroupIds") String[] strEntityGroupIds) throws ThingsboardException {
        checkArrayParameter("entityGroupIds", strEntityGroupIds);
        try {
            SecurityUser user = getCurrentUser();
            TenantId tenantId = user.getTenantId();
            List<EntityGroupId> entityGroupIds = new ArrayList<>();
            for (String strEntityGroupId : strEntityGroupIds) {
                entityGroupIds.add(new EntityGroupId(toUUID(strEntityGroupId)));
            }
            List<EntityGroup> entityGroups = checkNotNull(entityGroupService.findEntityGroupByIdsAsync(tenantId, entityGroupIds).get());
            return filterEntityGroupsByReadPermission(entityGroups);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/owners", params = {"limit"}, method = RequestMethod.GET)
    @ResponseBody
    public TextPageData<ContactBased<?>> getOwners(
            @RequestParam int limit,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String idOffset,
            @RequestParam(required = false) String textOffset) throws ThingsboardException {
        try {
            TextPageLink pageLink = createPageLink(limit, textSearch, idOffset, textOffset);
            List<ContactBased<?>> owners = new ArrayList<>();
            if (getCurrentUser().getAuthority() == Authority.TENANT_ADMIN) {
                accessControlService.checkPermission(getCurrentUser(), Resource.TENANT, Operation.READ);
                owners.add(tenantService.findTenantById(getCurrentUser().getTenantId()));
            }
            accessControlService.checkPermission(getCurrentUser(), Resource.CUSTOMER, Operation.READ);
            Set<EntityId> ownerIds = ownersCacheService.getChildOwners(getTenantId(), getCurrentUser().getOwnerId());
            if (!ownerIds.isEmpty()) {
                List<CustomerId> customerIds = new ArrayList<>();
                for (EntityId ownerId : ownerIds) {
                    customerIds.add(new CustomerId(ownerId.getId()));
                }
                owners.addAll(customerService.findCustomersByTenantIdAndIdsAsync(getTenantId(), customerIds).get());
            }
            owners = owners.stream().sorted(entityComparator).filter(new EntityPageLinkFilter(pageLink)).collect(Collectors.toList());
            if (pageLink.getLimit() > 0 && owners.size() > pageLink.getLimit()) {
                int toRemove = owners.size() - pageLink.getLimit();
                owners.subList(owners.size() - toRemove, owners.size()).clear();
            }
            return new TextPageData<>(owners, pageLink);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private List<EntityGroup> filterEntityGroupsByReadPermission(List<EntityGroup> entityGroups) {
        return entityGroups.stream().filter(entityGroup -> {
            try {
                return accessControlService.hasEntityGroupPermission(getCurrentUser(), Operation.READ, entityGroup);
            } catch (ThingsboardException e) {
                return false;
            }
        }).collect(Collectors.toList());
    }
}
