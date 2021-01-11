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
package org.thingsboard.server.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.permission.OwnersCacheService;

import java.util.Set;
import java.util.UUID;

@RestController
@TbCoreComponent
@RequestMapping("/api")
public class OwnerController extends BaseController {

    public static final String OWNER_ID = "ownerId";
    public static final String ENTITY_TYPE = "entityType";
    public static final String ENTITY_ID = "entityId";

    @Autowired
    private OwnersCacheService ownersCacheService;

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/owner/TENANT/{ownerId}/{entityType}/{entityId}", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public void changeOwnerToTenant(
            @PathVariable(OWNER_ID) String ownerIdStr,
            @PathVariable(ENTITY_TYPE) String entityType,
            @PathVariable(ENTITY_ID) String entityIdStr) throws ThingsboardException {
        checkParameter(OWNER_ID, ownerIdStr);
        checkParameter(ENTITY_TYPE, entityType);
        checkParameter(ENTITY_ID, entityIdStr);
        TenantId targetOwnerId = new TenantId(UUID.fromString(ownerIdStr));
        EntityId entityId = EntityIdFactory.getByTypeAndId(entityType, entityIdStr);
        if (!getCurrentUser().getTenantId().equals(targetOwnerId)) {
            throw new ThingsboardException("You aren't authorized to perform this operation!", ThingsboardErrorCode.PERMISSION_DENIED);
        }
        try {
            checkEntityId(entityId, Operation.CHANGE_OWNER);
            changeOwner(getCurrentUser().getTenantId(), targetOwnerId, entityId);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/owner/CUSTOMER/{ownerId}/{entityType}/{entityId}", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public void changeOwnerToCustomer(
            @PathVariable(OWNER_ID) String ownerIdStr,
            @PathVariable(ENTITY_TYPE) String entityType,
            @PathVariable(ENTITY_ID) String entityIdStr) throws ThingsboardException {
        checkParameter(OWNER_ID, ownerIdStr);
        checkParameter(ENTITY_TYPE, entityType);
        checkParameter(ENTITY_ID, entityIdStr);
        EntityId currentUserOwnerId = getCurrentUser().getOwnerId();
        CustomerId targetOwnerId = new CustomerId(UUID.fromString(ownerIdStr));
        EntityId entityId = EntityIdFactory.getByTypeAndId(entityType, entityIdStr);

        Customer targetOwner = customerService.findCustomerById(getCurrentUser().getTenantId(), targetOwnerId);
        Set<EntityId> targetOwnerOwners = ownersCacheService.getOwners(getCurrentUser().getTenantId(), targetOwnerId, targetOwner);
        if (!targetOwnerOwners.contains(currentUserOwnerId)) {
            // Customer/Tenant Changes Owner from Customer to Sub-Customer - OK.
            // Sub-Customer Changes Owner from Sub-Customer to Customer - NOT OK.
            throw new ThingsboardException("You aren't authorized to perform this operation!", ThingsboardErrorCode.PERMISSION_DENIED);
        }
        try {
            changeOwner(getCurrentUser().getTenantId(), targetOwnerId, entityId);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private void changeOwner(TenantId tenantId, EntityId targetOwnerId, EntityId entityId) throws ThingsboardException {
        try {
            switch (entityId.getEntityType()) {
                case DEVICE:
                    ownersCacheService.changeDeviceOwner(tenantId, targetOwnerId, checkDeviceId(new DeviceId(entityId.getId()), Operation.CHANGE_OWNER));
                    break;
                case ASSET:
                    ownersCacheService.changeAssetOwner(tenantId, targetOwnerId, checkAssetId(new AssetId(entityId.getId()), Operation.CHANGE_OWNER));
                    break;
                case ENTITY_VIEW:
                    ownersCacheService.changeEntityViewOwner(tenantId, targetOwnerId, checkEntityViewId(new EntityViewId(entityId.getId()), Operation.CHANGE_OWNER));
                    break;
                case CUSTOMER:
                    ownersCacheService.changeCustomerOwner(tenantId, targetOwnerId, checkCustomerId(new CustomerId(entityId.getId()), Operation.CHANGE_OWNER));
                    break;
                case USER:
                    User user = checkUserId(new UserId(entityId.getId()), Operation.CHANGE_OWNER);
                    ownersCacheService.changeUserOwner(tenantId, targetOwnerId, user);
                    break;
                case DASHBOARD:
                    ownersCacheService.changeDashboardOwner(tenantId, targetOwnerId, checkDashboardId(new DashboardId(entityId.getId()), Operation.CHANGE_OWNER));
                    break;
            }
        } catch (ThingsboardException e) {
            logEntityAction(entityId, null,
                    null, ActionType.ASSIGNED_TO_CUSTOMER, e);
            throw handleException(e);
        }
    }

}
