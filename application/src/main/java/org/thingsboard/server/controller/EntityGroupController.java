/**
 * Copyright © 2016-2017 The Thingsboard Authors
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

package org.thingsboard.server.controller;

import com.google.common.util.concurrent.ListenableFuture;
import io.swagger.annotations.ApiParam;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TimePageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.exception.ThingsboardErrorCode;
import org.thingsboard.server.exception.ThingsboardException;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
public class EntityGroupController extends BaseController {

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}", method = RequestMethod.GET)
    @ResponseBody
    public EntityGroup getEntityGroupById(@PathVariable("entityGroupId") String strEntityGroupId) throws ThingsboardException {
        checkParameter("entityGroupId", strEntityGroupId);
        try {
            EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
            return checkEntityGroupId(entityGroupId);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/entityGroup", method = RequestMethod.POST)
    @ResponseBody
    public EntityGroup saveEntityGroup(@RequestBody EntityGroup entityGroup) throws ThingsboardException {
        try {
            checkEntityGroupType(entityGroup.getType());
            return checkNotNull(entityGroupService.saveEntityGroup(getCurrentUser().getTenantId(), entityGroup));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}", method = RequestMethod.DELETE)
    @ResponseStatus(value = HttpStatus.OK)
    public void deleteEntityGroup(@PathVariable("entityGroupId") String strEntityGroupId) throws ThingsboardException {
        checkParameter("entityGroupId", strEntityGroupId);
        try {
            EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
            EntityGroup entityGroup = checkEntityGroupId(entityGroupId);
            if (entityGroup.isGroupAll()) {
                throw new ThingsboardException("Unable to remove entity group: " +
                        "Removal of entity group 'All' is forbidden!", ThingsboardErrorCode.PERMISSION_DENIED);
            }
            entityGroupService.deleteEntityGroup(entityGroupId);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/tenant/entityGroups/{groupType}", method = RequestMethod.GET)
    @ResponseBody
    public List<EntityGroup> getTenantEntityGroups(
            @ApiParam(value = "EntityGroup type", required = true, allowableValues = "CUSTOMER,ASSET,DEVICE") @PathVariable("groupType") String strGroupType) throws ThingsboardException {
        try {
            EntityType groupType = checkStrEntityGroupType("groupType", strGroupType);
            TenantId tenantId = getCurrentUser().getTenantId();
            return checkNotNull(entityGroupService.findEntityGroupsByType(tenantId, groupType).get());
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}/{entityIds}", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public void addEntitiesToEntityGroup(@PathVariable("entityGroupId") String strEntityGroupId,
                                         @PathVariable("entityIds") String[] strEntityIds) throws ThingsboardException {
        checkParameter("entityGroupId", strEntityGroupId);
        checkArrayParameter("entityIds", strEntityIds);
        try {
            EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
            EntityGroup entityGroup = checkEntityGroupId(entityGroupId);
            if (entityGroup.isGroupAll()) {
                throw new ThingsboardException("Unable to add entities to entity group: " +
                        "Addition to entity group 'All' is forbidden!", ThingsboardErrorCode.PERMISSION_DENIED);
            }
            List<EntityId> entityIds = new ArrayList<>();
            for (String strEntityId : strEntityIds) {
                EntityId entityId = EntityIdFactory.getByTypeAndId(entityGroup.getType(), strEntityId);
                checkEntityId(entityId);
                entityIds.add(entityId);
            }
            entityGroupService.addEntitiesToEntityGroup(entityGroupId, entityIds);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}/{entityIds}", method = RequestMethod.DELETE)
    @ResponseStatus(value = HttpStatus.OK)
    public void removeEntitiesFromEntityGroup(@PathVariable("entityGroupId") String strEntityGroupId,
                                              @PathVariable("entityIds") String[] strEntityIds) throws ThingsboardException {
        checkParameter("entityGroupId", strEntityGroupId);
        checkArrayParameter("entityIds", strEntityIds);
        try {
            EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
            EntityGroup entityGroup = checkEntityGroupId(entityGroupId);
            if (entityGroup.isGroupAll()) {
                throw new ThingsboardException("Unable to remove entities from entity group: " +
                        "Removal from entity group 'All' is forbidden!", ThingsboardErrorCode.PERMISSION_DENIED);
            }
            List<EntityId> entityIds = new ArrayList<>();
            for (String strEntityId : strEntityIds) {
                EntityId entityId = EntityIdFactory.getByTypeAndId(entityGroup.getType(), strEntityId);
                checkEntityId(entityId);
                entityIds.add(entityId);
            }
            entityGroupService.removeEntitiesFromEntityGroup(entityGroupId, entityIds);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}/{entityId}", method = RequestMethod.GET)
    @ResponseBody
    public EntityView getGroupEntity(
            @PathVariable("entityGroupId") String strEntityGroupId,
            @PathVariable("entityId") String strEntityId) throws ThingsboardException {
        checkParameter("entityGroupId", strEntityGroupId);
        checkParameter("entityId", strEntityId);

        try {
            EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
            EntityGroup entityGroup = checkEntityGroupId(entityGroupId);
            EntityType entityType = entityGroup.getType();
            checkEntityGroupType(entityType);
            EntityId entityId = EntityIdFactory.getByTypeAndId(entityType, strEntityId);
            checkEntityId(entityId);
            EntityView result = null;
            if (entityType == EntityType.CUSTOMER) {
                result = customerService.findGroupCustomer(entityGroupId, entityId);
            } else if (entityType == EntityType.ASSET) {
                result = assetService.findGroupAsset(entityGroupId, entityId);
            } else if (entityType == EntityType.DEVICE) {
                result = deviceService.findGroupDevice(entityGroupId, entityId);
            }
            return checkNotNull(result);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}/entities", method = RequestMethod.GET)
    @ResponseBody
    public TimePageData<EntityView> getEntities(
            @PathVariable("entityGroupId") String strEntityGroupId,
            @ApiParam(value = "Page link limit", required = true, allowableValues = "range[1, infinity]") @RequestParam int limit,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false, defaultValue = "false") boolean ascOrder,
            @RequestParam(required = false) String offset
    ) throws ThingsboardException {
        checkParameter("entityGroupId", strEntityGroupId);
        EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
        EntityGroup entityGroup = checkEntityGroupId(entityGroupId);
        EntityType entityType = entityGroup.getType();
        checkEntityGroupType(entityType);
        try {
            TimePageLink pageLink = createPageLink(limit, startTime, endTime, ascOrder, offset);
            ListenableFuture<TimePageData<EntityView>> asyncResult = null;
            if (entityType == EntityType.CUSTOMER) {
                asyncResult = customerService.findCustomersByEntityGroupId(entityGroupId, pageLink);
            } else if (entityType == EntityType.ASSET) {
                asyncResult = assetService.findAssetsByEntityGroupId(entityGroupId, pageLink);
            } else if (entityType == EntityType.DEVICE) {
                asyncResult = deviceService.findDevicesByEntityGroupId(entityGroupId, pageLink);
            }
            return checkNotNull(asyncResult.get());
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/entityGroups/{entityType}/{entityId}", method = RequestMethod.GET)
    @ResponseBody
    public List<EntityGroupId> getEntityGroupsForEntity(
            @ApiParam(value = "Entity type", required = true, allowableValues = "CUSTOMER,ASSET,DEVICE") @PathVariable("entityType") String strEntityType,
            @PathVariable("entityId") String strEntityId) throws ThingsboardException {
        checkParameter("entityType", strEntityType);
        checkParameter("entityId", strEntityId);
        try {
            EntityType entityType = checkStrEntityGroupType("entityType", strEntityType);
            EntityId entityId = EntityIdFactory.getByTypeAndId(entityType, strEntityId);
            checkEntityId(entityId);
            return checkNotNull(entityGroupService.findEntityGroupsForEntity(entityId).get());
        } catch (Exception e) {
            throw handleException(e);
        }
    }
}
