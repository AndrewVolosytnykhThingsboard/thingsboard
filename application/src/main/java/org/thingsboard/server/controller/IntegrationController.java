/**
 * Copyright © 2016-2021 ThingsBoard, Inc.
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.IntegrationId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.permission.Resource;
import org.thingsboard.server.common.data.plugin.ComponentLifecycleEvent;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.integration.PlatformIntegrationService;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@TbCoreComponent
@RequestMapping("/api")
public class IntegrationController extends BaseController {

    @Autowired
    private PlatformIntegrationService platformIntegrationService;

    private static final String INTEGRATION_ID = "integrationId";

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/integration/{integrationId}", method = RequestMethod.GET)
    @ResponseBody
    public Integration getIntegrationById(@PathVariable(INTEGRATION_ID) String strIntegrationId) throws ThingsboardException {
        checkParameter(INTEGRATION_ID, strIntegrationId);
        try {
            IntegrationId integrationId = new IntegrationId(toUUID(strIntegrationId));
            return checkIntegrationId(integrationId, Operation.READ);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/integration/routingKey/{routingKey}", method = RequestMethod.GET)
    @ResponseBody
    public Integration getIntegrationByRoutingKey(
            @PathVariable("routingKey") String routingKey) throws ThingsboardException {
        try {
            Integration integration = checkNotNull(integrationService.findIntegrationByRoutingKey(getTenantId(), routingKey));
            checkNotNull(integration);
            accessControlService.checkPermission(getCurrentUser(), Resource.INTEGRATION, Operation.READ, integration.getId(), integration);
            return integration;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/integration", method = RequestMethod.POST)
    @ResponseBody
    public Integration saveIntegration(@RequestBody Integration integration) throws ThingsboardException {
        try {
            integration.setTenantId(getCurrentUser().getTenantId());
            boolean created = integration.getId() == null;

            checkEntity(integration.getId(), integration, Resource.INTEGRATION, null);

            platformIntegrationService.validateIntegrationConfiguration(integration);

            Integration result = checkNotNull(integrationService.saveIntegration(integration));
            tbClusterService.onEntityStateChange(result.getTenantId(), result.getId(),
                    created ? ComponentLifecycleEvent.CREATED : ComponentLifecycleEvent.UPDATED);
            logEntityAction(result.getId(), result, null, created ? ActionType.ADDED : ActionType.UPDATED, null);
            return result;
        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.INTEGRATION), integration,
                    null, integration.getId() == null ? ActionType.ADDED : ActionType.UPDATED, e);
            throw handleException(e);
        }
    }


    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/integrations", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<Integration> getIntegrations(
            @RequestParam int pageSize,
            @RequestParam int page,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String sortProperty,
            @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        try {
            accessControlService.checkPermission(getCurrentUser(), Resource.INTEGRATION, Operation.READ);
            TenantId tenantId = getCurrentUser().getTenantId();
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            return checkNotNull(integrationService.findTenantIntegrations(tenantId, pageLink));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/integration/check", method = RequestMethod.POST)
    @ResponseBody
    public void checkIntegrationConnection(@RequestBody Integration integration) throws ThingsboardException {
        try {
            checkNotNull(integration);
            integration.setTenantId(getCurrentUser().getTenantId());
            platformIntegrationService.checkIntegrationConnection(integration);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/integration/{integrationId}", method = RequestMethod.DELETE)
    @ResponseStatus(value = HttpStatus.OK)
    public void deleteIntegration(@PathVariable(INTEGRATION_ID) String strIntegrationId) throws ThingsboardException {
        checkParameter(INTEGRATION_ID, strIntegrationId);
        try {
            IntegrationId integrationId = new IntegrationId(toUUID(strIntegrationId));
            Integration integration = checkIntegrationId(integrationId, Operation.DELETE);
            integrationService.deleteIntegration(getTenantId(), integrationId);

            tbClusterService.onEntityStateChange(integration.getTenantId(), integration.getId(), ComponentLifecycleEvent.DELETED);

            logEntityAction(integrationId, integration,
                    null,
                    ActionType.DELETED, null, strIntegrationId);

        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.INTEGRATION),
                    null,
                    null,
                    ActionType.DELETED, e, strIntegrationId);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/integrations", params = {"integrationIds"}, method = RequestMethod.GET)
    @ResponseBody
    public List<Integration> getIntegrationsByIds(
            @RequestParam("integrationIds") String[] strIntegrationIds) throws ThingsboardException {
        checkArrayParameter("integrationIds", strIntegrationIds);
        try {
            if (!accessControlService.hasPermission(getCurrentUser(), Resource.INTEGRATION, Operation.READ)) {
                return Collections.emptyList();
            }
            SecurityUser user = getCurrentUser();
            TenantId tenantId = user.getTenantId();
            List<IntegrationId> integrationIds = new ArrayList<>();
            for (String strIntegrationId : strIntegrationIds) {
                integrationIds.add(new IntegrationId(toUUID(strIntegrationId)));
            }
            List<Integration> integrations = checkNotNull(integrationService.findIntegrationsByIdsAsync(tenantId, integrationIds).get());
            return filterIntegrationsByReadPermission(integrations);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private List<Integration> filterIntegrationsByReadPermission(List<Integration> integrations) {
        return integrations.stream().filter(integration -> {
            try {
                return accessControlService.hasPermission(getCurrentUser(), Resource.INTEGRATION, Operation.READ, integration.getId(), integration);
            } catch (ThingsboardException e) {
                return false;
            }
        }).collect(Collectors.toList());
    }

}
