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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.*;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.*;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.page.TimePageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.permission.MergedGroupTypePermissionInfo;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.permission.Resource;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.security.model.UserPrincipal;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class DashboardController extends BaseController {

    public static final String DASHBOARD_ID = "dashboardId";

    @Value("${dashboard.max_datapoints_limit}")
    private long maxDatapointsLimit;


    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/dashboard/serverTime", method = RequestMethod.GET)
    @ResponseBody
    public long getServerTime() throws ThingsboardException {
        return System.currentTimeMillis();
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/dashboard/maxDatapointsLimit", method = RequestMethod.GET)
    @ResponseBody
    public long getMaxDatapointsLimit() throws ThingsboardException {
        return maxDatapointsLimit;
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/dashboard/info/{dashboardId}", method = RequestMethod.GET)
    @ResponseBody
    public DashboardInfo getDashboardInfoById(@PathVariable(DASHBOARD_ID) String strDashboardId) throws ThingsboardException {
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            return checkDashboardInfoId(dashboardId, Operation.READ);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/dashboard/{dashboardId}", method = RequestMethod.GET)
    @ResponseBody
    public Dashboard getDashboardById(@PathVariable(DASHBOARD_ID) String strDashboardId) throws ThingsboardException {
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            return checkDashboardId(dashboardId, Operation.READ);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/dashboard", method = RequestMethod.POST)
    @ResponseBody 
    public Dashboard saveDashboard(@RequestBody Dashboard dashboard) throws ThingsboardException {
        try {
            dashboard.setTenantId(getCurrentUser().getTenantId());

            Operation operation = dashboard.getId() == null ? Operation.CREATE : Operation.WRITE;

            if (operation == Operation.CREATE
                    && getCurrentUser().getAuthority() == Authority.CUSTOMER_USER &&
                    (dashboard.getCustomerId() == null || dashboard.getCustomerId().isNullUid())) {
                dashboard.setCustomerId(getCurrentUser().getCustomerId());
            }

            accessControlService.checkPermission(getCurrentUser(), Resource.DASHBOARD, operation,
                    dashboard.getId(), dashboard);

            Dashboard savedDashboard = checkNotNull(dashboardService.saveDashboard(dashboard));

            logEntityAction(savedDashboard.getId(), savedDashboard,
                    null,
                    dashboard.getId() == null ? ActionType.ADDED : ActionType.UPDATED, null);

            return savedDashboard;
        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.DASHBOARD), dashboard,
                    null, dashboard.getId() == null ? ActionType.ADDED : ActionType.UPDATED, e);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/dashboard/{dashboardId}", method = RequestMethod.DELETE)
    @ResponseStatus(value = HttpStatus.OK)
    public void deleteDashboard(@PathVariable(DASHBOARD_ID) String strDashboardId) throws ThingsboardException {
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            Dashboard dashboard = checkDashboardId(dashboardId, Operation.DELETE);
            dashboardService.deleteDashboard(getCurrentUser().getTenantId(), dashboardId);

            logEntityAction(dashboardId, dashboard,
                    null,
                    ActionType.DELETED, null, strDashboardId);

        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.DASHBOARD),
                    null,
                    null,
                    ActionType.DELETED, e, strDashboardId);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/customer/{customerId}/dashboard/{dashboardId}", method = RequestMethod.POST)
    @ResponseBody 
    public Dashboard assignDashboardToCustomer(@PathVariable("customerId") String strCustomerId,
                                         @PathVariable(DASHBOARD_ID) String strDashboardId) throws ThingsboardException {
        checkParameter("customerId", strCustomerId);
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            CustomerId customerId = new CustomerId(toUUID(strCustomerId));
            Customer customer = checkCustomerId(customerId, Operation.READ);

            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            checkDashboardId(dashboardId, Operation.ASSIGN_TO_CUSTOMER);
            
            Dashboard savedDashboard = checkNotNull(dashboardService.assignDashboardToCustomer(getCurrentUser().getTenantId(), dashboardId, customerId));

            logEntityAction(dashboardId, savedDashboard,
                    customerId,
                    ActionType.ASSIGNED_TO_CUSTOMER, null, strDashboardId, strCustomerId, customer.getName());


            return savedDashboard;
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.DASHBOARD), null,
                    null,
                    ActionType.ASSIGNED_TO_CUSTOMER, e, strDashboardId, strCustomerId);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/customer/{customerId}/dashboard/{dashboardId}", method = RequestMethod.DELETE)
    @ResponseBody 
    public Dashboard unassignDashboardFromCustomer(@PathVariable("customerId") String strCustomerId,
                                                   @PathVariable(DASHBOARD_ID) String strDashboardId) throws ThingsboardException {
        checkParameter("customerId", strCustomerId);
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            CustomerId customerId = new CustomerId(toUUID(strCustomerId));
            Customer customer = checkCustomerId(customerId, Operation.READ);
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            Dashboard dashboard = checkDashboardId(dashboardId, Operation.UNASSIGN_FROM_CUSTOMER);

            Dashboard savedDashboard = checkNotNull(dashboardService.unassignDashboardFromCustomer(getCurrentUser().getTenantId(), dashboardId, customerId));

            logEntityAction(dashboardId, dashboard,
                    customerId,
                    ActionType.UNASSIGNED_FROM_CUSTOMER, null, strDashboardId, customer.getId().toString(), customer.getName());

            return savedDashboard;
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.DASHBOARD), null,
                    null,
                    ActionType.UNASSIGNED_FROM_CUSTOMER, e, strDashboardId);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/dashboard/{dashboardId}/customers", method = RequestMethod.POST)
    @ResponseBody
    public Dashboard updateDashboardCustomers(@PathVariable(DASHBOARD_ID) String strDashboardId,
                                              @RequestBody String[] strCustomerIds) throws ThingsboardException {
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            Dashboard dashboard = checkDashboardId(dashboardId, Operation.ASSIGN_TO_CUSTOMER);

            Set<CustomerId> customerIds = new HashSet<>();
            if (strCustomerIds != null) {
                for (String strCustomerId : strCustomerIds) {
                    customerIds.add(new CustomerId(toUUID(strCustomerId)));
                }
            }

            Set<CustomerId> addedCustomerIds = new HashSet<>();
            Set<CustomerId> removedCustomerIds = new HashSet<>();
            for (CustomerId customerId : customerIds) {
                if (!dashboard.isAssignedToCustomer(customerId)) {
                    addedCustomerIds.add(customerId);
                }
            }

            Set<ShortCustomerInfo> assignedCustomers = dashboard.getAssignedCustomers();
            if (assignedCustomers != null) {
                for (ShortCustomerInfo customerInfo : assignedCustomers) {
                    if (!customerIds.contains(customerInfo.getCustomerId())) {
                        removedCustomerIds.add(customerInfo.getCustomerId());
                    }
                }
            }

            if (addedCustomerIds.isEmpty() && removedCustomerIds.isEmpty()) {
                return dashboard;
            } else {
                Dashboard savedDashboard = null;
                for (CustomerId customerId : addedCustomerIds) {
                    savedDashboard = checkNotNull(dashboardService.assignDashboardToCustomer(getCurrentUser().getTenantId(), dashboardId, customerId));
                    ShortCustomerInfo customerInfo = savedDashboard.getAssignedCustomerInfo(customerId);
                    logEntityAction(dashboardId, savedDashboard,
                            customerId,
                            ActionType.ASSIGNED_TO_CUSTOMER, null, strDashboardId, customerId.toString(), customerInfo.getTitle());
                }
                for (CustomerId customerId : removedCustomerIds) {
                    ShortCustomerInfo customerInfo = dashboard.getAssignedCustomerInfo(customerId);
                    savedDashboard = checkNotNull(dashboardService.unassignDashboardFromCustomer(getCurrentUser().getTenantId(), dashboardId, customerId));
                    logEntityAction(dashboardId, dashboard,
                            customerId,
                            ActionType.UNASSIGNED_FROM_CUSTOMER, null, strDashboardId, customerId.toString(), customerInfo.getTitle());

                }
                return savedDashboard;
            }
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.DASHBOARD), null,
                    null,
                    ActionType.ASSIGNED_TO_CUSTOMER, e, strDashboardId);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/dashboard/{dashboardId}/customers/add", method = RequestMethod.POST)
    @ResponseBody
    public Dashboard addDashboardCustomers(@PathVariable(DASHBOARD_ID) String strDashboardId,
                                           @RequestBody String[] strCustomerIds) throws ThingsboardException {
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            Dashboard dashboard = checkDashboardId(dashboardId, Operation.ASSIGN_TO_CUSTOMER);

            Set<CustomerId> customerIds = new HashSet<>();
            if (strCustomerIds != null) {
                for (String strCustomerId : strCustomerIds) {
                    CustomerId customerId = new CustomerId(toUUID(strCustomerId));
                    if (!dashboard.isAssignedToCustomer(customerId)) {
                        customerIds.add(customerId);
                    }
                }
            }

            if (customerIds.isEmpty()) {
                return dashboard;
            } else {
                Dashboard savedDashboard = null;
                for (CustomerId customerId : customerIds) {
                    savedDashboard = checkNotNull(dashboardService.assignDashboardToCustomer(getCurrentUser().getTenantId(), dashboardId, customerId));
                    ShortCustomerInfo customerInfo = savedDashboard.getAssignedCustomerInfo(customerId);
                    logEntityAction(dashboardId, savedDashboard,
                            customerId,
                            ActionType.ASSIGNED_TO_CUSTOMER, null, strDashboardId, customerId.toString(), customerInfo.getTitle());
                }
                return savedDashboard;
            }
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.DASHBOARD), null,
                    null,
                    ActionType.ASSIGNED_TO_CUSTOMER, e, strDashboardId);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/dashboard/{dashboardId}/customers/remove", method = RequestMethod.POST)
    @ResponseBody
    public Dashboard removeDashboardCustomers(@PathVariable(DASHBOARD_ID) String strDashboardId,
                                              @RequestBody String[] strCustomerIds) throws ThingsboardException {
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            Dashboard dashboard = checkDashboardId(dashboardId, Operation.UNASSIGN_FROM_CUSTOMER);

            Set<CustomerId> customerIds = new HashSet<>();
            if (strCustomerIds != null) {
                for (String strCustomerId : strCustomerIds) {
                    CustomerId customerId = new CustomerId(toUUID(strCustomerId));
                    if (dashboard.isAssignedToCustomer(customerId)) {
                        customerIds.add(customerId);
                    }
                }
            }

            if (customerIds.isEmpty()) {
                return dashboard;
            } else {
                Dashboard savedDashboard = null;
                for (CustomerId customerId : customerIds) {
                    ShortCustomerInfo customerInfo = dashboard.getAssignedCustomerInfo(customerId);
                    savedDashboard = checkNotNull(dashboardService.unassignDashboardFromCustomer(getCurrentUser().getTenantId(), dashboardId, customerId));
                    logEntityAction(dashboardId, dashboard,
                            customerId,
                            ActionType.UNASSIGNED_FROM_CUSTOMER, null, strDashboardId, customerId.toString(), customerInfo.getTitle());

                }
                return savedDashboard;
            }
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.DASHBOARD), null,
                    null,
                    ActionType.UNASSIGNED_FROM_CUSTOMER, e, strDashboardId);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/customer/public/dashboard/{dashboardId}", method = RequestMethod.POST)
    @ResponseBody
    public Dashboard assignDashboardToPublicCustomer(@PathVariable(DASHBOARD_ID) String strDashboardId) throws ThingsboardException {
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            Dashboard dashboard = checkDashboardId(dashboardId, Operation.ASSIGN_TO_CUSTOMER);
            Customer publicCustomer = customerService.findOrCreatePublicCustomer(dashboard.getTenantId());
            Dashboard savedDashboard = checkNotNull(dashboardService.assignDashboardToCustomer(getCurrentUser().getTenantId(), dashboardId, publicCustomer.getId()));

            logEntityAction(dashboardId, savedDashboard,
                    publicCustomer.getId(),
                    ActionType.ASSIGNED_TO_CUSTOMER, null, strDashboardId, publicCustomer.getId().toString(), publicCustomer.getName());

            return savedDashboard;
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.DASHBOARD), null,
                    null,
                    ActionType.ASSIGNED_TO_CUSTOMER, e, strDashboardId);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/customer/public/dashboard/{dashboardId}", method = RequestMethod.DELETE)
    @ResponseBody
    public Dashboard unassignDashboardFromPublicCustomer(@PathVariable(DASHBOARD_ID) String strDashboardId) throws ThingsboardException {
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            Dashboard dashboard = checkDashboardId(dashboardId, Operation.UNASSIGN_FROM_CUSTOMER);
            Customer publicCustomer = customerService.findOrCreatePublicCustomer(dashboard.getTenantId());

            Dashboard savedDashboard = checkNotNull(dashboardService.unassignDashboardFromCustomer(getCurrentUser().getTenantId(), dashboardId, publicCustomer.getId()));

            logEntityAction(dashboardId, dashboard,
                    publicCustomer.getId(),
                    ActionType.UNASSIGNED_FROM_CUSTOMER, null, strDashboardId, publicCustomer.getId().toString(), publicCustomer.getName());

            return savedDashboard;
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.DASHBOARD), null,
                    null,
                    ActionType.UNASSIGNED_FROM_CUSTOMER, e, strDashboardId);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/tenant/{tenantId}/dashboards", params = { "limit" }, method = RequestMethod.GET)
    @ResponseBody
    public TextPageData<DashboardInfo> getTenantDashboards(
            @PathVariable("tenantId") String strTenantId,
            @RequestParam int limit,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String idOffset,
            @RequestParam(required = false) String textOffset) throws ThingsboardException {
        try {
            TenantId tenantId = new TenantId(toUUID(strTenantId));
            checkTenantId(tenantId, Operation.READ);
            TextPageLink pageLink = createPageLink(limit, textSearch, idOffset, textOffset);
            return checkNotNull(dashboardService.findDashboardsByTenantId(tenantId, pageLink));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/tenant/dashboards", params = { "limit" }, method = RequestMethod.GET)
    @ResponseBody
    public TextPageData<DashboardInfo> getTenantDashboards(
            @RequestParam int limit,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String idOffset,
            @RequestParam(required = false) String textOffset) throws ThingsboardException {
        try {
            accessControlService.checkPermission(getCurrentUser(), Resource.DASHBOARD, Operation.READ);
            TenantId tenantId = getCurrentUser().getTenantId();
            TextPageLink pageLink = createPageLink(limit, textSearch, idOffset, textOffset);
            return checkNotNull(dashboardService.findDashboardsByTenantId(tenantId, pageLink));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/customer/{customerId}/dashboards", params = { "limit" }, method = RequestMethod.GET)
    @ResponseBody
    public TimePageData<DashboardInfo> getCustomerDashboards(
            @PathVariable("customerId") String strCustomerId,
            @RequestParam int limit,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false, defaultValue = "false") boolean ascOrder,
            @RequestParam(required = false) String offset) throws ThingsboardException {
        checkParameter("customerId", strCustomerId);
        try {
            TenantId tenantId = getCurrentUser().getTenantId();
            CustomerId customerId = new CustomerId(toUUID(strCustomerId));
            checkCustomerId(customerId, Operation.READ);
            accessControlService.checkPermission(getCurrentUser(), Resource.DASHBOARD, Operation.READ);
            TimePageLink pageLink = createPageLink(limit, startTime, endTime, ascOrder, offset);
            return checkNotNull(dashboardService.findDashboardsByTenantIdAndCustomerId(tenantId, customerId, pageLink).get());
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("isAuthenticated()")
    @RequestMapping(value = "/userDashboards", params = { "limit" }, method = RequestMethod.GET)
    @ResponseBody
    public List<DashboardInfo> getUserDashboards(
            @RequestParam int limit,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String textSearch,
            @RequestParam(name = "userId", required = false) String strUserId) throws ThingsboardException {
        try {
            SecurityUser securityUser;
            if (!StringUtils.isEmpty(strUserId)) {
                UserId userId = new UserId(toUUID(strUserId));
                User user = checkUserId(userId, Operation.READ);
                UserPrincipal principal = new UserPrincipal(UserPrincipal.Type.USER_NAME, user.getEmail());
                securityUser = new SecurityUser(user, true, principal, getMergedUserPermissions(user));
            } else {
                securityUser = getCurrentUser();
            }
            Operation operationType = Operation.READ;
            if (!StringUtils.isEmpty(operation)) {
                try {
                    operationType = Operation.valueOf(operation);
                } catch (IllegalArgumentException e) {
                    throw new ThingsboardException("Unsupported operation type '" + operation + "'!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
                }
            }
            MergedGroupTypePermissionInfo groupTypePermissionInfo = null;
            if (operationType == Operation.READ) {
                groupTypePermissionInfo = securityUser.getUserPermissions().getReadGroupPermissions().get(EntityType.DASHBOARD);
            }
            if (securityUser.getUserPermissions().hasGenericPermission(Resource.DASHBOARD, operationType) ||
                    (groupTypePermissionInfo != null && !groupTypePermissionInfo.getEntityGroupIds().isEmpty())) {
                Set<EntityId> dashboardIds = new HashSet<>();
                Set<EntityGroupId> groupIds = new HashSet<>();
                if (securityUser.getUserPermissions().hasGenericPermission(Resource.DASHBOARD, operationType)) {
                    Optional<EntityGroup> entityGroup = entityGroupService.findEntityGroupByTypeAndName(getTenantId(), securityUser.getOwnerId(), EntityType.DASHBOARD, EntityGroup.GROUP_ALL_NAME).get();
                    if (entityGroup.isPresent()) {
                        groupIds.add(entityGroup.get().getId());
                    }
                }
                if (groupTypePermissionInfo != null && !groupTypePermissionInfo.getEntityGroupIds().isEmpty()) {
                    groupIds.addAll(groupTypePermissionInfo.getEntityGroupIds());
                }
                for (EntityGroupId groupId : groupIds) {
                    dashboardIds.addAll(entityGroupService.findAllEntityIds(getTenantId(), groupId, new TimePageLink(Integer.MAX_VALUE)).get());
                }
                return loadDashboards(dashboardIds, textSearch, limit);
            } else {
                return Collections.emptyList();
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}/dashboards", params = { "limit" }, method = RequestMethod.GET)
    @ResponseBody
    public List<DashboardInfo> getGroupDashboards(
            @PathVariable("entityGroupId") String strEntityGroupId,
            @RequestParam int limit,
            @RequestParam(required = false) String textSearch) throws ThingsboardException {
        try {
            checkParameter("entityGroupId", strEntityGroupId);
            EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
            EntityGroup entityGroup = checkEntityGroupId(entityGroupId, Operation.READ);
            if (entityGroup.getType() != EntityType.DASHBOARD) {
                throw new ThingsboardException("Invalid entity group type '" + entityGroup.getType() + "'! Should be 'DASHBOARD'.", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
            }
            List<EntityId> ids = entityGroupService.findAllEntityIds(getTenantId(), entityGroupId, new TimePageLink(Integer.MAX_VALUE)).get();
            return loadDashboards(ids, textSearch, limit);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private List<DashboardInfo> loadDashboards(Collection<EntityId> dashboardIds, String textSearch, int limit) throws Exception {
        if (!dashboardIds.isEmpty()) {
            List<DashboardId> dashboardIdsList = new ArrayList<>();
            dashboardIds.forEach((dashboardId) -> dashboardIdsList.add(new DashboardId(dashboardId.getId())));
            List<DashboardInfo> dashboards = dashboardService.findDashboardInfoByIdsAsync(getTenantId(), dashboardIdsList).get();
            if (!StringUtils.isEmpty(textSearch)) {
                String textSearchLower = textSearch.toLowerCase();
                dashboards = dashboards.stream().filter(dashboardInfo -> dashboardInfo.getTitle().toLowerCase().startsWith(textSearchLower)).collect(Collectors.toList());
            }
            dashboards = dashboards.stream().sorted(Comparator.comparing(DashboardInfo::getTitle)).collect(Collectors.toList());
            if (limit > 0 && dashboards.size() > limit) {
                int toRemove = dashboards.size() - limit;
                dashboards.subList(dashboards.size() - toRemove, dashboards.size()).clear();
            }
            return dashboards;
        } else {
            return Collections.emptyList();
        }

    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/dashboards", params = {"dashboardIds"}, method = RequestMethod.GET)
    @ResponseBody
    public List<DashboardInfo> getDashboardsByIds(
            @RequestParam("dashboardIds") String[] strDashboardIds) throws ThingsboardException {
        checkArrayParameter("dashboardIds", strDashboardIds);
        try {
            SecurityUser user = getCurrentUser();
            TenantId tenantId = user.getTenantId();
            List<DashboardId> dashboardIds = new ArrayList<>();
            for (String strDashboardId : strDashboardIds) {
                dashboardIds.add(new DashboardId(toUUID(strDashboardId)));
            }
            List<DashboardInfo> dashboards = checkNotNull(dashboardService.findDashboardInfoByIdsAsync(tenantId, dashboardIds).get());
            return filterDashboardsByReadPermission(dashboards);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private List<DashboardInfo> filterDashboardsByReadPermission(List<DashboardInfo> dashboards) {
        return dashboards.stream().filter(dashboard -> {
            try {
                return accessControlService.hasPermission(getCurrentUser(), Resource.DASHBOARD, Operation.READ, dashboard.getId(), dashboard);
            } catch (ThingsboardException e) {
                return false;
            }
        }).collect(Collectors.toList());
    }

}
