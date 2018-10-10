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
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.entityview.EntityViewSearchQuery;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.dao.exception.IncorrectParameterException;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.util.List;
import java.util.stream.Collectors;

import static org.thingsboard.server.controller.CustomerController.CUSTOMER_ID;

/**
 * Created by Victor Basanets on 8/28/2017.
 */
@RestController
@RequestMapping("/api")
public class EntityViewController extends BaseController {

    public static final String ENTITY_VIEW_ID = "entityViewId";

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityView/{entityViewId}", method = RequestMethod.GET)
    @ResponseBody
    public EntityView getEntityViewById(@PathVariable(ENTITY_VIEW_ID) String strEntityViewId) throws ThingsboardException {
        checkParameter(ENTITY_VIEW_ID, strEntityViewId);
        try {
            return checkEntityViewId(new EntityViewId(toUUID(strEntityViewId)));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityView", method = RequestMethod.POST)
    @ResponseBody
    public EntityView saveEntityView(@RequestBody EntityView entityView) throws ThingsboardException {
        try {
            entityView.setTenantId(getCurrentUser().getTenantId());
            EntityView savedEntityView = checkNotNull(entityViewService.saveEntityView(entityView));
            logEntityAction(savedEntityView.getId(), savedEntityView, null,
                    entityView.getId() == null ? ActionType.ADDED : ActionType.UPDATED, null);
            return savedEntityView;
        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.ENTITY_VIEW), entityView, null,
                    entityView.getId() == null ? ActionType.ADDED : ActionType.UPDATED, e);
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/entityView/{entityViewId}", method = RequestMethod.DELETE)
    @ResponseStatus(value = HttpStatus.OK)
    public void deleteEntityView(@PathVariable(ENTITY_VIEW_ID) String strEntityViewId) throws ThingsboardException {
        checkParameter(ENTITY_VIEW_ID, strEntityViewId);
        try {
            EntityViewId entityViewId = new EntityViewId(toUUID(strEntityViewId));
            EntityView entityView = checkEntityViewId(entityViewId);
            entityViewService.deleteEntityView(entityViewId);
            logEntityAction(entityViewId, entityView, entityView.getCustomerId(),
                    ActionType.DELETED,null, strEntityViewId);
        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.ENTITY_VIEW),
                    null,
                    null,
                    ActionType.DELETED, e, strEntityViewId);
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/customer/{customerId}/entityView/{entityViewId}", method = RequestMethod.POST)
    @ResponseBody
    public EntityView assignEntityViewToCustomer(@PathVariable(CUSTOMER_ID) String strCustomerId,
                                             @PathVariable(ENTITY_VIEW_ID) String strEntityViewId) throws ThingsboardException {
        checkParameter(CUSTOMER_ID, strCustomerId);
        checkParameter(ENTITY_VIEW_ID, strEntityViewId);
        try {
            CustomerId customerId = new CustomerId(toUUID(strCustomerId));
            Customer customer = checkCustomerId(customerId);

            EntityViewId entityViewId = new EntityViewId(toUUID(strEntityViewId));
            checkEntityViewId(entityViewId);

            EntityView savedEntityView = checkNotNull(entityViewService.assignEntityViewToCustomer(entityViewId, customerId));
            logEntityAction(entityViewId, savedEntityView,
                    savedEntityView.getCustomerId(),
                    ActionType.ASSIGNED_TO_CUSTOMER, null, strEntityViewId, strCustomerId, customer.getName());
            return savedEntityView;
        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.ENTITY_VIEW), null,
                    null,
                    ActionType.ASSIGNED_TO_CUSTOMER, e, strEntityViewId, strCustomerId);
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/customer/entityView/{entityViewId}", method = RequestMethod.DELETE)
    @ResponseBody
    public EntityView unassignEntityViewFromCustomer(@PathVariable(ENTITY_VIEW_ID) String strEntityViewId) throws ThingsboardException {
        checkParameter(ENTITY_VIEW_ID, strEntityViewId);
        try {
            EntityViewId entityViewId = new EntityViewId(toUUID(strEntityViewId));
            EntityView entityView = checkEntityViewId(entityViewId);
            if (entityView.getCustomerId() == null || entityView.getCustomerId().getId().equals(ModelConstants.NULL_UUID)) {
                throw new IncorrectParameterException("Entity View isn't assigned to any customer!");
            }
            Customer customer = checkCustomerId(entityView.getCustomerId());
            EntityView savedEntityView = checkNotNull(entityViewService.unassignEntityViewFromCustomer(entityViewId));
            logEntityAction(entityViewId, entityView,
                    entityView.getCustomerId(),
                    ActionType.UNASSIGNED_FROM_CUSTOMER, null, strEntityViewId, customer.getId().toString(), customer.getName());

            return savedEntityView;
        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.ENTITY_VIEW), null,
                    null,
                    ActionType.UNASSIGNED_FROM_CUSTOMER, e, strEntityViewId);
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/customer/{customerId}/entityViews", params = {"limit"}, method = RequestMethod.GET)
    @ResponseBody
    public TextPageData<EntityView> getCustomerEntityViews(
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
            checkCustomerId(customerId);
            TextPageLink pageLink = createPageLink(limit, textSearch, idOffset, textOffset);
            if (type != null && type.trim().length() > 0) {
                return checkNotNull(entityViewService.findEntityViewsByTenantIdAndCustomerIdAndType(tenantId, customerId, pageLink, type));
            } else {
                return checkNotNull(entityViewService.findEntityViewsByTenantIdAndCustomerId(tenantId, customerId, pageLink));
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/tenant/entityViews", params = {"limit"}, method = RequestMethod.GET)
    @ResponseBody
    public TextPageData<EntityView> getTenantEntityViews(
            @RequestParam int limit,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String idOffset,
            @RequestParam(required = false) String textOffset) throws ThingsboardException {
        try {
            TenantId tenantId = getCurrentUser().getTenantId();
            TextPageLink pageLink = createPageLink(limit, textSearch, idOffset, textOffset);

            if (type != null && type.trim().length() > 0) {
                return checkNotNull(entityViewService.findEntityViewByTenantIdAndType(tenantId, pageLink, type));
            } else {
                return checkNotNull(entityViewService.findEntityViewByTenantId(tenantId, pageLink));
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityViews", method = RequestMethod.POST)
    @ResponseBody
    public List<EntityView> findByQuery(@RequestBody EntityViewSearchQuery query) throws ThingsboardException {
        checkNotNull(query);
        checkNotNull(query.getParameters());
        checkNotNull(query.getEntityViewTypes());
        checkEntityId(query.getParameters().getEntityId());
        try {
            List<EntityView> entityViews = checkNotNull(entityViewService.findEntityViewsByQuery(query).get());
            entityViews = entityViews.stream().filter(entityView -> {
                try {
                    checkEntityView(entityView);
                    return true;
                } catch (ThingsboardException e) {
                    return false;
                }
            }).collect(Collectors.toList());
            return entityViews;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityView/types", method = RequestMethod.GET)
    @ResponseBody
    public List<EntitySubtype> getEntityViewTypes() throws ThingsboardException {
        try {
            SecurityUser user = getCurrentUser();
            TenantId tenantId = user.getTenantId();
            ListenableFuture<List<EntitySubtype>> entityViewTypes = entityViewService.findEntityViewTypesByTenantId(tenantId);
            return checkNotNull(entityViewTypes.get());
        } catch (Exception e) {
            throw handleException(e);
        }
    }
}
