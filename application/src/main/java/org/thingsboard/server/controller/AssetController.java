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
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.EntitySubtype;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.asset.AssetSearchQuery;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.permission.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class AssetController extends BaseController {

    public static final String ASSET_ID = "assetId";

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/asset/{assetId}", method = RequestMethod.GET)
    @ResponseBody
    public Asset getAssetById(@PathVariable(ASSET_ID) String strAssetId) throws ThingsboardException {
        checkParameter(ASSET_ID, strAssetId);
        try {
            AssetId assetId = new AssetId(toUUID(strAssetId));
            return checkAssetId(assetId, Operation.READ);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/asset", method = RequestMethod.POST)
    @ResponseBody
    public Asset saveAsset(@RequestBody Asset asset) throws ThingsboardException {
        try {
            asset.setTenantId(getCurrentUser().getTenantId());

            Operation operation = asset.getId() == null ? Operation.CREATE : Operation.WRITE;

            if (operation == Operation.CREATE
                    && getCurrentUser().getAuthority() == Authority.CUSTOMER_USER &&
                    (asset.getCustomerId() == null || asset.getCustomerId().isNullUid())) {
                asset.setCustomerId(getCurrentUser().getCustomerId());
            }

            accessControlService.checkPermission(getCurrentUser(), Resource.ASSET, operation,
                    asset.getId(), asset);

            Asset savedAsset  = checkNotNull(assetService.saveAsset(asset));

            logEntityAction(savedAsset.getId(), savedAsset,
                    savedAsset.getCustomerId(),
                    asset.getId() == null ? ActionType.ADDED : ActionType.UPDATED, null);

            return  savedAsset;
        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.ASSET), asset,
                    null, asset.getId() == null ? ActionType.ADDED : ActionType.UPDATED, e);
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/asset/{assetId}", method = RequestMethod.DELETE)
    @ResponseStatus(value = HttpStatus.OK)
    public void deleteAsset(@PathVariable(ASSET_ID) String strAssetId) throws ThingsboardException {
        checkParameter(ASSET_ID, strAssetId);
        try {
            AssetId assetId = new AssetId(toUUID(strAssetId));
            Asset asset = checkAssetId(assetId, Operation.DELETE);
            assetService.deleteAsset(getTenantId(), assetId);

            logEntityAction(assetId, asset,
                    asset.getCustomerId(),
                    ActionType.DELETED, null, strAssetId);

        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.ASSET),
                    null,
                    null,
                    ActionType.DELETED, e, strAssetId);
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/customer/{customerId}/asset/{assetId}", method = RequestMethod.POST)
    @ResponseBody
    public Asset assignAssetToCustomer(@PathVariable("customerId") String strCustomerId,
                                       @PathVariable(ASSET_ID) String strAssetId) throws ThingsboardException {
        checkParameter("customerId", strCustomerId);
        checkParameter(ASSET_ID, strAssetId);
        try {
            CustomerId customerId = new CustomerId(toUUID(strCustomerId));
            Customer customer = checkCustomerId(customerId, Operation.READ);

            AssetId assetId = new AssetId(toUUID(strAssetId));
            checkAssetId(assetId, Operation.ASSIGN_TO_CUSTOMER);

            Asset savedAsset = checkNotNull(assetService.assignAssetToCustomer(getTenantId(), assetId, customerId));

            logEntityAction(assetId, savedAsset,
                    savedAsset.getCustomerId(),
                    ActionType.ASSIGNED_TO_CUSTOMER, null, strAssetId, strCustomerId, customer.getName());

            return  savedAsset;
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.ASSET), null,
                    null,
                    ActionType.ASSIGNED_TO_CUSTOMER, e, strAssetId, strCustomerId);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/customer/asset/{assetId}", method = RequestMethod.DELETE)
    @ResponseBody
    public Asset unassignAssetFromCustomer(@PathVariable(ASSET_ID) String strAssetId) throws ThingsboardException {
        checkParameter(ASSET_ID, strAssetId);
        try {
            AssetId assetId = new AssetId(toUUID(strAssetId));
            Asset asset = checkAssetId(assetId, Operation.UNASSIGN_FROM_CUSTOMER);
            String customerId = "";
            String customerName = "";
            if (asset.getCustomerId() != null && !asset.getCustomerId().isNullUid()) {
                Customer customer = checkCustomerId(asset.getCustomerId(), Operation.READ);
                customerId = customer.getId().toString();
                customerName = customer.getName();
            }

            Asset savedAsset = checkNotNull(assetService.unassignAssetFromCustomer(getTenantId(), assetId));

            logEntityAction(assetId, asset,
                    asset.getCustomerId(),
                    ActionType.UNASSIGNED_FROM_CUSTOMER, null, strAssetId, customerId, customerName);

            return savedAsset;
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.ASSET), null,
                    null,
                    ActionType.UNASSIGNED_FROM_CUSTOMER, e, strAssetId);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/customer/public/asset/{assetId}", method = RequestMethod.POST)
    @ResponseBody
    public Asset assignAssetToPublicCustomer(@PathVariable(ASSET_ID) String strAssetId) throws ThingsboardException {
        checkParameter(ASSET_ID, strAssetId);
        try {
            AssetId assetId = new AssetId(toUUID(strAssetId));
            Asset asset = checkAssetId(assetId, Operation.ASSIGN_TO_CUSTOMER);
            Customer publicCustomer = customerService.findOrCreatePublicCustomer(asset.getTenantId());
            Asset savedAsset = checkNotNull(assetService.assignAssetToCustomer(getTenantId(), assetId, publicCustomer.getId()));

            logEntityAction(assetId, savedAsset,
                    savedAsset.getCustomerId(),
                    ActionType.ASSIGNED_TO_CUSTOMER, null, strAssetId, publicCustomer.getId().toString(), publicCustomer.getName());

            return savedAsset;
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.ASSET), null,
                    null,
                    ActionType.ASSIGNED_TO_CUSTOMER, e, strAssetId);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/tenant/assets", params = {"limit"}, method = RequestMethod.GET)
    @ResponseBody
    public TextPageData<Asset> getTenantAssets(
            @RequestParam int limit,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String idOffset,
            @RequestParam(required = false) String textOffset) throws ThingsboardException {
        try {
            accessControlService.checkPermission(getCurrentUser(), Resource.ASSET, Operation.READ);
            TenantId tenantId = getCurrentUser().getTenantId();
            TextPageLink pageLink = createPageLink(limit, textSearch, idOffset, textOffset);
            if (type != null && type.trim().length()>0) {
                return checkNotNull(assetService.findAssetsByTenantIdAndType(tenantId, type, pageLink));
            } else {
                return checkNotNull(assetService.findAssetsByTenantId(tenantId, pageLink));
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/tenant/assets", params = {"assetName"}, method = RequestMethod.GET)
    @ResponseBody
    public Asset getTenantAsset(
            @RequestParam String assetName) throws ThingsboardException {
        try {
            accessControlService.checkPermission(getCurrentUser(), Resource.ASSET, Operation.READ);
            TenantId tenantId = getCurrentUser().getTenantId();
            return checkNotNull(assetService.findAssetByTenantIdAndName(tenantId, assetName));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/customer/{customerId}/assets", params = {"limit"}, method = RequestMethod.GET)
    @ResponseBody
    public TextPageData<Asset> getCustomerAssets(
            @PathVariable("customerId") String strCustomerId,
            @RequestParam int limit,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String idOffset,
            @RequestParam(required = false) String textOffset) throws ThingsboardException {
        checkParameter("customerId", strCustomerId);
        try {
            TenantId tenantId = getCurrentUser().getTenantId();
            CustomerId customerId = new CustomerId(toUUID(strCustomerId));
            checkCustomerId(customerId, Operation.READ);
            accessControlService.checkPermission(getCurrentUser(), Resource.ASSET, Operation.READ);
            TextPageLink pageLink = createPageLink(limit, textSearch, idOffset, textOffset);
            if (type != null && type.trim().length()>0) {
                return checkNotNull(assetService.findAssetsByTenantIdAndCustomerIdAndType(tenantId, customerId, type, pageLink));
            } else {
                return checkNotNull(assetService.findAssetsByTenantIdAndCustomerId(tenantId, customerId, pageLink));
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/assets", params = {"assetIds"}, method = RequestMethod.GET)
    @ResponseBody
    public List<Asset> getAssetsByIds(
            @RequestParam("assetIds") String[] strAssetIds) throws ThingsboardException {
        checkArrayParameter("assetIds", strAssetIds);
        try {
            accessControlService.checkPermission(getCurrentUser(), Resource.ASSET, Operation.READ);
            SecurityUser user = getCurrentUser();
            TenantId tenantId = user.getTenantId();
            CustomerId customerId = user.getCustomerId();
            List<AssetId> assetIds = new ArrayList<>();
            for (String strAssetId : strAssetIds) {
                assetIds.add(new AssetId(toUUID(strAssetId)));
            }
            ListenableFuture<List<Asset>> assets;
            if (customerId == null || customerId.isNullUid()) {
                assets = assetService.findAssetsByTenantIdAndIdsAsync(tenantId, assetIds);
            } else {
                assets = assetService.findAssetsByTenantIdCustomerIdAndIdsAsync(tenantId, customerId, assetIds);
            }
            return checkNotNull(assets.get());
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/assets", method = RequestMethod.POST)
    @ResponseBody
    public List<Asset> findByQuery(@RequestBody AssetSearchQuery query) throws ThingsboardException {
        checkNotNull(query);
        checkNotNull(query.getParameters());
        checkNotNull(query.getAssetTypes());
        checkEntityId(query.getParameters().getEntityId(), Operation.READ);
        try {
            List<Asset> assets = checkNotNull(assetService.findAssetsByQuery(getTenantId(), query).get());
            assets = assets.stream().filter(asset -> {
                try {
                    accessControlService.checkPermission(getCurrentUser(), Resource.ASSET, Operation.READ, asset.getId(), asset);
                    return true;
                } catch (ThingsboardException e) {
                    return false;
                }
            }).collect(Collectors.toList());
            return assets;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/asset/types", method = RequestMethod.GET)
    @ResponseBody
    public List<EntitySubtype> getAssetTypes() throws ThingsboardException {
        try {
            SecurityUser user = getCurrentUser();
            TenantId tenantId = user.getTenantId();
            ListenableFuture<List<EntitySubtype>> assetTypes = assetService.findAssetTypesByTenantId(tenantId);
            return checkNotNull(assetTypes.get());
        } catch (Exception e) {
            throw handleException(e);
        }
    }
}
