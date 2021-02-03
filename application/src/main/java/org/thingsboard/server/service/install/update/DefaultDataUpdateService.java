/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.service.install.update;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.AdminSettings;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DashboardInfo;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.rule.engine.profile.TbDeviceProfileNode;
import org.thingsboard.rule.engine.profile.TbDeviceProfileNodeConfiguration;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.SearchTextBased;
import org.thingsboard.server.common.data.ShortCustomerInfo;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UUIDBased;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.BaseAttributeKvEntry;
import org.thingsboard.server.common.data.kv.BaseReadTsKvQuery;
import org.thingsboard.server.common.data.kv.ReadTsKvQuery;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.data.wl.Favicon;
import org.thingsboard.server.common.data.wl.PaletteSettings;
import org.thingsboard.server.common.data.wl.WhiteLabelingParams;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.customer.CustomerService;
import org.thingsboard.server.dao.dashboard.DashboardService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.rule.RuleNode;
import org.thingsboard.server.dao.entityview.EntityViewService;
import org.thingsboard.server.dao.exception.IncorrectParameterException;
import org.thingsboard.server.dao.group.EntityGroupService;
import org.thingsboard.server.dao.integration.IntegrationService;
import org.thingsboard.server.dao.relation.RelationService;
import org.thingsboard.server.dao.rule.RuleChainService;
import org.thingsboard.server.dao.settings.AdminSettingsService;
import org.thingsboard.server.dao.tenant.TenantService;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.server.dao.user.UserService;
import org.thingsboard.server.dao.wl.WhiteLabelingService;
import org.thingsboard.server.dao.util.mapping.JacksonUtil;
import org.thingsboard.server.service.install.InstallScripts;
import org.thingsboard.server.service.install.SystemDataLoaderService;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.apache.commons.lang.StringUtils.isBlank;
import static org.thingsboard.server.service.install.DatabaseHelper.objectMapper;

@Service
@Profile("install")
@Slf4j
public class DefaultDataUpdateService implements DataUpdateService {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String WHITE_LABEL_PARAMS = "whiteLabelParams";
    private static final String LOGO_IMAGE = "logoImage";
    private static final String LOGO_IMAGE_CHECKSUM = "logoImageChecksum";
    private static final String MAIL_TEMPLATES = "mailTemplates";
    private static final int DEFAULT_LIMIT = 100;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private RuleChainService ruleChainService;

    @Autowired
    private InstallScripts installScripts;

    @Autowired
    private IntegrationService integrationService;

    @Autowired
    private EntityGroupService entityGroupService;

    @Autowired
    private UserService userService;

    @Autowired
    private AdminSettingsService adminSettingsService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private EntityViewService entityViewService;

    @Autowired
    private SystemDataLoaderService systemDataLoaderService;

    @Autowired
    private WhiteLabelingService whiteLabelingService;

    @Autowired
    private AttributesService attributesService;

    @Autowired
    private RelationService relationService;

    @Autowired
    private TimeseriesService tsService;

    @Override
    public void updateData(String fromVersion) throws Exception {

        switch (fromVersion) {
            case "1.4.0":
                log.info("Updating data from version 1.4.0 to 2.0.0 ...");
                tenantsDefaultRuleChainUpdater.updateEntities(null);
                break;
            case "3.0.1":
                log.info("Updating data from version 3.0.1 to 3.1.0 ...");
                tenantsEntityViewsUpdater.updateEntities(null);
                break;
            case "3.1.1":
                log.info("Updating data from version 3.1.1 to 3.2.0 ...");
                tenantsRootRuleChainUpdater.updateEntities(null);
                break;
            case "3.3.0":
                log.info("Updating data from version 3.3.0 to 3.3.0PE ...");
                tenantsCustomersGroupAllUpdater.updateEntities(null);
                tenantEntitiesGroupAllUpdater.updateEntities(null);
                tenantIntegrationUpdater.updateEntities(null);
                //for 2.4.0
                AdminSettings mailTemplateSettings = adminSettingsService.findAdminSettingsByKey(TenantId.SYS_TENANT_ID, "mailTemplates");
                if (mailTemplateSettings == null) {
                    systemDataLoaderService.loadMailTemplates();
                } else {
                    systemDataLoaderService.updateMailTemplates(mailTemplateSettings.getId(), mailTemplateSettings.getJsonValue());
                }

                //White Labeling updates
                updateSystemWhiteLabelingParameters();
                List<ListenableFuture<WhiteLabelingParams>> futures = tenantsWhiteLabelingUpdater.updateEntities(null);
                for (ListenableFuture<WhiteLabelingParams> future : futures) {
                    future.get();
                }
                break;
            default:
                throw new RuntimeException("Unable to update data, unsupported fromVersion: " + fromVersion);
        }
    }

    private PaginatedUpdater<String, Tenant> tenantsDefaultRuleChainUpdater =
            new PaginatedUpdater<String, Tenant>() {

                @Override
                protected String getName() {
                    return "Tenants default rule chain updater";
                }

                @Override
                protected PageData<Tenant> findEntities(String region, PageLink pageLink) {
                    return tenantService.findTenants(pageLink);
                }

                @Override
                protected void updateEntity(Tenant tenant) {
                    try {
                        RuleChain ruleChain = ruleChainService.getRootTenantRuleChain(tenant.getId());
                        if (ruleChain == null) {
                            installScripts.createDefaultRuleChains(tenant.getId());
                        }
                    } catch (Exception e) {
                        log.error("Unable to update Tenant", e);
                    }
                }
            };

    private PaginatedUpdater<String, Tenant> tenantsRootRuleChainUpdater =
            new PaginatedUpdater<String, Tenant>() {

                @Override
                protected String getName() {
                    return "Tenants root rule chain updater";
                }

                @Override
                protected PageData<Tenant> findEntities(String region, PageLink pageLink) {
                    return tenantService.findTenants(pageLink);
                }

                @Override
                protected void updateEntity(Tenant tenant) {
                    try {
                        RuleChain ruleChain = ruleChainService.getRootTenantRuleChain(tenant.getId());
                        if (ruleChain == null) {
                            installScripts.createDefaultRuleChains(tenant.getId());
                        } else {
                            RuleChainMetaData md = ruleChainService.loadRuleChainMetaData(tenant.getId(), ruleChain.getId());
                            int oldIdx = md.getFirstNodeIndex();
                            int newIdx = md.getNodes().size();

                            if (md.getNodes().size() < oldIdx) {
                                // Skip invalid rule chains
                                return;
                            }

                            RuleNode oldFirstNode = md.getNodes().get(oldIdx);
                            if (oldFirstNode.getType().equals(TbDeviceProfileNode.class.getName())) {
                                // No need to update the rule node twice.
                                return;
                            }

                            RuleNode ruleNode = new RuleNode();
                            ruleNode.setRuleChainId(ruleChain.getId());
                            ruleNode.setName("Device Profile Node");
                            ruleNode.setType(TbDeviceProfileNode.class.getName());
                            ruleNode.setDebugMode(false);
                            TbDeviceProfileNodeConfiguration ruleNodeConfiguration = new TbDeviceProfileNodeConfiguration().defaultConfiguration();
                            ruleNode.setConfiguration(JacksonUtil.valueToTree(ruleNodeConfiguration));
                            ObjectNode additionalInfo = JacksonUtil.newObjectNode();
                            additionalInfo.put("description", "Process incoming messages from devices with the alarm rules defined in the device profile. Dispatch all incoming messages with \"Success\" relation type.");
                            additionalInfo.put("layoutX", 204);
                            additionalInfo.put("layoutY", 240);
                            ruleNode.setAdditionalInfo(additionalInfo);

                            md.getNodes().add(ruleNode);
                            md.setFirstNodeIndex(newIdx);
                            md.addConnectionInfo(newIdx, oldIdx, "Success");
                            ruleChainService.saveRuleChainMetaData(tenant.getId(), md);
                        }
                    } catch (Exception e) {
                        log.error("[{}] Unable to update Tenant: {}", tenant.getId(), tenant.getName(), e);
                    }
                }
            };

    private PaginatedUpdater<String, Tenant> tenantsEntityViewsUpdater =
            new PaginatedUpdater<String, Tenant>() {

                @Override
                protected String getName() {
                    return "Tenants entity views updater";
                }

                @Override
                protected PageData<Tenant> findEntities(String region, PageLink pageLink) {
                    return tenantService.findTenants(pageLink);
                }

                @Override
                protected void updateEntity(Tenant tenant) {
                    updateTenantEntityViews(tenant.getId());
                }
            };

    private PaginatedUpdater<String, Tenant> tenantsCustomersGroupAllUpdater =
            new PaginatedUpdater<String, Tenant>() {

                @Override
                protected String getName() {
                    return "Tenants customers group all updater";
                }

                @Override
                protected PageData<Tenant> findEntities(String region, PageLink pageLink) {
                    return tenantService.findTenants(pageLink);
                }

                @Override
                protected void updateEntity(Tenant tenant) {
                    try {
                        List<EntityGroup> entityGroups = entityGroupService.findAllEntityGroups(TenantId.SYS_TENANT_ID, tenant.getId()).get();
                        for (EntityGroup entityGroup : entityGroups) {
                            entityGroup.setOwnerId(tenant.getId());
                            entityGroupService.saveEntityGroup(TenantId.SYS_TENANT_ID, tenant.getId(), entityGroup);
                        }
                        EntityGroup entityGroup;
                        Optional<EntityGroup> customerGroupOptional =
                                entityGroupService.findEntityGroupByTypeAndName(TenantId.SYS_TENANT_ID, tenant.getId(), EntityType.CUSTOMER, EntityGroup.GROUP_ALL_NAME).get();
                        if (!customerGroupOptional.isPresent()) {
                            entityGroup = entityGroupService.createEntityGroupAll(TenantId.SYS_TENANT_ID, tenant.getId(), EntityType.CUSTOMER);
                        } else {
                            entityGroup = customerGroupOptional.get();
                        }
                        new CustomersGroupAllUpdater(entityGroup).updateEntities(tenant.getId());
                    } catch (InterruptedException | ExecutionException e) {
                        log.error("Unable to update Tenant", e);
                    }
                }
            };

    private PaginatedUpdater<String, Tenant> tenantEntitiesGroupAllUpdater =
            new PaginatedUpdater<String, Tenant>() {

                @Override
                protected String getName() {
                    return "Tenant entities group all updater";
                }

                @Override
                protected PageData<Tenant> findEntities(String region, PageLink pageLink) {
                    return tenantService.findTenants(pageLink);
                }

                @Override
                protected void updateEntity(Tenant tenant) {
                    try {
                        EntityType[] entityGroupTypes = new EntityType[]{EntityType.USER, EntityType.ASSET, EntityType.DEVICE, EntityType.DASHBOARD, EntityType.ENTITY_VIEW};
                        for (EntityType groupType : entityGroupTypes) {
                            EntityGroup entityGroup;
                            Optional<EntityGroup> entityGroupOptional =
                                    entityGroupService.findEntityGroupByTypeAndName(TenantId.SYS_TENANT_ID, tenant.getId(), groupType, EntityGroup.GROUP_ALL_NAME).get();
                            boolean fetchAllTenantEntities;
                            if (!entityGroupOptional.isPresent()) {
                                entityGroup = entityGroupService.createEntityGroupAll(TenantId.SYS_TENANT_ID, tenant.getId(), groupType);
                                fetchAllTenantEntities = true;
                            } else {
                                entityGroup = entityGroupOptional.get();
                                fetchAllTenantEntities = false;
                            }
                            switch (groupType) {
                                case USER:
                                    new CustomerUsersTenantGroupAllRemover(entityGroup).updateEntities(tenant.getId());
                                    entityGroupService.findOrCreateTenantUsersGroup(tenant.getId());
                                    Optional<EntityGroup> tenantAdminsOptional =
                                            entityGroupService.findEntityGroupByTypeAndName(tenant.getId(), tenant.getId(), EntityType.USER, EntityGroup.GROUP_TENANT_ADMINS_NAME).get();
                                    if (!tenantAdminsOptional.isPresent()) {
                                        EntityGroup tenantAdmins = entityGroupService.findOrCreateTenantAdminsGroup(tenant.getId());
                                        new TenantAdminsGroupAllUpdater(entityGroup, tenantAdmins).updateEntities(tenant.getId());
                                    }
                                    break;
                                case ASSET:
                                    new AssetsGroupAllUpdater(assetService, customerService, entityGroupService, entityGroup, fetchAllTenantEntities).updateEntities(tenant.getId());
                                    break;
                                case DEVICE:
                                    new DevicesGroupAllUpdater(deviceService, customerService, entityGroupService, entityGroup, fetchAllTenantEntities).updateEntities(tenant.getId());
                                    break;
                                case ENTITY_VIEW:
                                    new EntityViewGroupAllUpdater(entityViewService, customerService, entityGroupService, entityGroup, fetchAllTenantEntities).updateEntities(tenant.getId());
                                    break;
                                case DASHBOARD:
                                    new DashboardsGroupAllUpdater(entityGroup, fetchAllTenantEntities).updateEntities(tenant.getId());
                                    break;
                            }
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        log.error("Unable to update Tenant", e);
                    }
                }
            };

    private class TenantAdminsGroupAllUpdater extends GroupAllPaginatedUpdater<TenantId, User> {

        private final EntityGroup tenantAdmins;

        public TenantAdminsGroupAllUpdater(EntityGroup groupAll, EntityGroup tenantAdmins) {
            super(groupAll);
            this.tenantAdmins = tenantAdmins;
        }

        @Override
        protected String getName() {
            return "Tenant admins group all updater";
        }

        @Override
        protected PageData<User> findEntities(TenantId id, PageLink pageLink) {
            return userService.findTenantAdmins(id, pageLink);
        }

        @Override
        protected void updateGroupEntity(User entity, EntityGroup groupAll) {
            entityGroupService.addEntityToEntityGroup(TenantId.SYS_TENANT_ID, groupAll.getId(), entity.getId());
            entityGroupService.addEntityToEntityGroup(TenantId.SYS_TENANT_ID, tenantAdmins.getId(), entity.getId());
        }
    }

    private class CustomerUsersTenantGroupAllRemover extends PaginatedUpdater<TenantId, User> {

        private final EntityGroup groupAll;

        public CustomerUsersTenantGroupAllRemover(EntityGroup groupAll) {
            this.groupAll = groupAll;
        }

        @Override
        protected String getName() {
            return "Customer users tenant group all remover";
        }

        @Override
        protected PageData<User> findEntities(TenantId id, PageLink pageLink) {
            try {
                List<EntityId> entityIds = entityGroupService.findAllEntityIds(TenantId.SYS_TENANT_ID, groupAll.getId(), new PageLink(Integer.MAX_VALUE)).get();
                List<UserId> userIds = entityIds.stream().map(entityId -> new UserId(entityId.getId())).collect(Collectors.toList());
                List<User> users;
                if (!userIds.isEmpty()) {
                    users = userService.findUsersByTenantIdAndIdsAsync(id, userIds).get();
                } else {
                    users = Collections.emptyList();
                }
                return new PageData<>(users, 1, users.size(), false);
            } catch (Exception e) {
                log.error("Failed to get users from group all!", e);
                throw new RuntimeException("Failed to get users from group all!", e);
            }
        }

        @Override
        protected void updateEntity(User entity) {
            if (Authority.CUSTOMER_USER.equals(entity.getAuthority())) {
                entityGroupService.removeEntityFromEntityGroup(TenantId.SYS_TENANT_ID, groupAll.getId(), entity.getId());
            }
        }
    }

    private class CustomerUsersGroupAllUpdater extends GroupAllPaginatedUpdater<CustomerId, User> {

        private final TenantId tenantId;
        private final EntityGroup customerUsers;

        public CustomerUsersGroupAllUpdater(TenantId tenantId, EntityGroup groupAll, EntityGroup customerUsers) {
            super(groupAll);
            this.tenantId = tenantId;
            this.customerUsers = customerUsers;
        }

        @Override
        protected String getName() {
            return "Customer users group all updater";
        }

        @Override
        protected PageData<User> findEntities(CustomerId id, PageLink pageLink) {
            return userService.findCustomerUsers(this.tenantId, id, pageLink);
        }

        @Override
        protected void updateGroupEntity(User entity, EntityGroup groupAll) {
            entityGroupService.addEntityToEntityGroup(TenantId.SYS_TENANT_ID, groupAll.getId(), entity.getId());
            entityGroupService.addEntityToEntityGroup(TenantId.SYS_TENANT_ID, customerUsers.getId(), entity.getId());
        }
    }

    private class CustomersGroupAllUpdater extends GroupAllPaginatedUpdater<TenantId, Customer> {

        public CustomersGroupAllUpdater(EntityGroup groupAll) {
            super(groupAll);
        }

        @Override
        protected String getName() {
            return "Customers group all updater";
        }

        @Override
        protected PageData<Customer> findEntities(TenantId id, PageLink pageLink) {
            return customerService.findCustomersByTenantId(id, pageLink);
        }

        @Override
        protected void updateGroupEntity(Customer customer, EntityGroup groupAll) {
            if (customer.getId() == null || customer.getId().isNullUid()) {
                log.warn("Customer has invalid id [{}]", customer.getId());
                log.warn("[{}]", customer);
                return;
            }
            if (customer.isSubCustomer()) {
                return;
            }
            entityGroupService.addEntityToEntityGroup(TenantId.SYS_TENANT_ID, groupAll.getId(), customer.getId());
            try {
                List<EntityGroup> entityGroups = entityGroupService.findAllEntityGroups(TenantId.SYS_TENANT_ID, customer.getId()).get();
                for (EntityGroup entityGroup : entityGroups) {
                    entityGroup.setOwnerId(customer.getId());
                    entityGroupService.saveEntityGroup(TenantId.SYS_TENANT_ID, customer.getId(), entityGroup);
                }
                EntityType[] entityGroupTypes = new EntityType[]{EntityType.USER, EntityType.CUSTOMER, EntityType.ASSET, EntityType.DEVICE, EntityType.DASHBOARD, EntityType.ENTITY_VIEW};
                for (EntityType groupType : entityGroupTypes) {
                    Optional<EntityGroup> entityGroupOptional =
                            entityGroupService.findEntityGroupByTypeAndName(TenantId.SYS_TENANT_ID, customer.getId(), groupType, EntityGroup.GROUP_ALL_NAME).get();
                    if (!entityGroupOptional.isPresent()) {
                        EntityGroup entityGroup = entityGroupService.createEntityGroupAll(TenantId.SYS_TENANT_ID, customer.getId(), groupType);
                        if (groupType == EntityType.USER) {
                            if (!customer.isPublic()) {
                                entityGroupService.findOrCreateCustomerAdminsGroup(customer.getTenantId(), customer.getId(), null);
                                Optional<EntityGroup> customerUsersOptional =
                                        entityGroupService.findEntityGroupByTypeAndName(customer.getTenantId(), customer.getId(), EntityType.USER, EntityGroup.GROUP_CUSTOMER_USERS_NAME).get();
                                if (!customerUsersOptional.isPresent()) {
                                    EntityGroup customerUsers = entityGroupService.findOrCreateCustomerUsersGroup(customer.getTenantId(), customer.getId(), null);
                                    new CustomerUsersGroupAllUpdater(customer.getTenantId(), entityGroup, customerUsers).updateEntities(customer.getId());
                                }
                            } else {
                                entityGroupService.findOrCreatePublicUsersGroup(customer.getTenantId(), customer.getId());
                            }
                        }
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("Unable to update Customer", e);
            }
        }
    }


    private class DashboardsGroupAllUpdater extends PaginatedUpdater<TenantId, DashboardInfo> {

        private final EntityGroup groupAll;
        private final boolean fetchAllTenantEntities;

        private Map<CustomerId, EntityGroupId> customersGroupMap = new HashMap<>();
        private Map<CustomerId, Customer> customersMap = new HashMap<>();

        public DashboardsGroupAllUpdater(EntityGroup groupAll,
                                         boolean fetchAllTenantEntities) {
            this.groupAll = groupAll;
            this.fetchAllTenantEntities = fetchAllTenantEntities;
        }

        @Override
        protected String getName() {
            return "Dashboards group all updater";
        }

        @Override
        protected PageData<DashboardInfo> findEntities(TenantId id, PageLink pageLink) {
            if (fetchAllTenantEntities) {
                return dashboardService.findDashboardsByTenantId(id, pageLink);
            } else {
                try {
                    List<EntityId> entityIds = entityGroupService.findAllEntityIds(TenantId.SYS_TENANT_ID, groupAll.getId(), new PageLink(Integer.MAX_VALUE)).get();
                    List<DashboardId> dashboardIds = entityIds.stream().map(entityId -> new DashboardId(entityId.getId())).collect(Collectors.toList());
                    List<DashboardInfo> dashboards;
                    if (!dashboardIds.isEmpty()) {
                        dashboards = dashboardService.findDashboardInfoByIdsAsync(TenantId.SYS_TENANT_ID, dashboardIds).get();
                    } else {
                        dashboards = Collections.emptyList();
                    }
                    return new PageData<>(dashboards, 1, dashboards.size(), false);
                } catch (Exception e) {
                    log.error("Failed to get dashboards from group all!", e);
                    throw new RuntimeException("Failed to get dashboards from group all!", e);
                }
            }
        }

        @Override
        protected void updateEntity(DashboardInfo entity) {
            entityGroupService.addEntityToEntityGroupAll(TenantId.SYS_TENANT_ID, entity.getTenantId(), entity.getId());
            if (entity.getAssignedCustomers() != null) {
                for (ShortCustomerInfo customer : entity.getAssignedCustomers()) {
                    Customer customer1 = customersMap.computeIfAbsent(customer.getCustomerId(), customerId ->
                            customerService.findCustomerById(entity.getTenantId(), customer.getCustomerId()));
                    if (customer1 != null) {
                        EntityGroupId customerEntityGroupId = customersGroupMap.computeIfAbsent(
                                customer.getCustomerId(), customerId ->
                                        entityGroupService.findOrCreateReadOnlyEntityGroupForCustomer(entity.getTenantId(),
                                                customerId, entity.getEntityType()).getId()
                        );
                        entityGroupService.addEntityToEntityGroup(TenantId.SYS_TENANT_ID, customerEntityGroupId, entity.getId());
                        dashboardService.unassignDashboardFromCustomer(TenantId.SYS_TENANT_ID, entity.getId(), customer.getCustomerId());
                    } else {
                        Dashboard dashboard = dashboardService.findDashboardById(TenantId.SYS_TENANT_ID, entity.getId());
                        if (dashboard.removeAssignedCustomerInfo(customer)) {
                            EntityRelation relationToDelete =
                                    new EntityRelation(customer.getCustomerId(), entity.getId(), EntityRelation.CONTAINS_TYPE, RelationTypeGroup.DASHBOARD);
                            relationService.deleteRelation(TenantId.SYS_TENANT_ID, relationToDelete);
                            dashboardService.saveDashboard(dashboard);
                        }
                    }
                }
            }
        }
    }

    private WhiteLabelingPaginatedUpdater<String, Tenant> tenantsWhiteLabelingUpdater = new WhiteLabelingPaginatedUpdater<String, Tenant>() {

        @Override
        protected String getName() {
            return "Tenants white-labeling updater";
        }

        @Override
        protected PageData<Tenant> findEntities(String id, PageLink pageLink) {
            return tenantService.findTenants(pageLink);
        }

        @Override
        protected ListenableFuture<WhiteLabelingParams> updateEntity(Tenant tenant) throws Exception {
            List<ListenableFuture<WhiteLabelingParams>> futures = customersWhiteLabelingUpdater.updateEntities(tenant.getId());
            for (ListenableFuture<WhiteLabelingParams> future : futures) {
                future.get();
            }
            ListenableFuture<List<Void>> future = updateTenantMailTemplates(tenant.getId());
            return Futures.transformAsync(future, l -> updateEntityWhiteLabelingParameters(tenant.getId()),
                    MoreExecutors.directExecutor());
        }
    };

    private WhiteLabelingPaginatedUpdater<TenantId, Customer> customersWhiteLabelingUpdater = new WhiteLabelingPaginatedUpdater<TenantId, Customer>() {

        @Override
        protected String getName() {
            return "Customers white-labeling updater";
        }

        @Override
        protected PageData<Customer> findEntities(TenantId id, PageLink pageLink) {
            return customerService.findCustomersByTenantId(id, pageLink);
        }

        @Override
        protected ListenableFuture<WhiteLabelingParams> updateEntity(Customer customer) {
            return updateEntityWhiteLabelingParameters(customer.getId());
        }
    };

    private PaginatedUpdater<String, Tenant> tenantIntegrationUpdater = new PaginatedUpdater<String, Tenant>() {
        @Override
        protected PageData<Tenant> findEntities(String id, PageLink pageLink) {
            return tenantService.findTenants(pageLink);
        }

        @Override
        protected String getName() {
            return "Tenant integration updater";
        }

        @Override
        protected void updateEntity(Tenant tenant) {
            updateTenantIntegrations(tenant.getId());
        }
    };

    private void updateTenantEntityViews(TenantId tenantId) {
        PageLink pageLink = new PageLink(100);
        PageData<EntityView> pageData = entityViewService.findEntityViewByTenantId(tenantId, pageLink);
        boolean hasNext = true;
        while (hasNext) {
            List<ListenableFuture<List<Void>>> updateFutures = new ArrayList<>();
            for (EntityView entityView : pageData.getData()) {
                updateFutures.add(updateEntityViewLatestTelemetry(entityView));
            }

            try {
                Futures.allAsList(updateFutures).get();
            } catch (InterruptedException | ExecutionException e) {
                log.error("Failed to copy latest telemetry to entity view", e);
            }

            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
                pageData = entityViewService.findEntityViewByTenantId(tenantId, pageLink);
            } else {
                hasNext = false;
            }
        }
    }

    private ListenableFuture<List<Void>> updateEntityViewLatestTelemetry(EntityView entityView) {
        EntityViewId entityId = entityView.getId();
        List<String> keys = entityView.getKeys() != null && entityView.getKeys().getTimeseries() != null ?
                entityView.getKeys().getTimeseries() : Collections.emptyList();
        long startTs = entityView.getStartTimeMs();
        long endTs = entityView.getEndTimeMs() == 0 ? Long.MAX_VALUE : entityView.getEndTimeMs();
        ListenableFuture<List<String>> keysFuture;
        if (keys.isEmpty()) {
            keysFuture = Futures.transform(tsService.findAllLatest(TenantId.SYS_TENANT_ID,
                    entityView.getEntityId()), latest -> latest.stream().map(TsKvEntry::getKey).collect(Collectors.toList()), MoreExecutors.directExecutor());
        } else {
            keysFuture = Futures.immediateFuture(keys);
        }
        ListenableFuture<List<TsKvEntry>> latestFuture = Futures.transformAsync(keysFuture, fetchKeys -> {
            List<ReadTsKvQuery> queries = fetchKeys.stream().filter(key -> !isBlank(key)).map(key -> new BaseReadTsKvQuery(key, startTs, endTs, 1, "DESC")).collect(Collectors.toList());
            if (!queries.isEmpty()) {
                return tsService.findAll(TenantId.SYS_TENANT_ID, entityView.getEntityId(), queries);
            } else {
                return Futures.immediateFuture(null);
            }
        }, MoreExecutors.directExecutor());
        return Futures.transformAsync(latestFuture, latestValues -> {
            if (latestValues != null && !latestValues.isEmpty()) {
                ListenableFuture<List<Void>> saveFuture = tsService.saveLatest(TenantId.SYS_TENANT_ID, entityId, latestValues);
                return saveFuture;
            }
            return Futures.immediateFuture(null);
        }, MoreExecutors.directExecutor());
    }

    private void updateSystemWhiteLabelingParameters() {
        AdminSettings whiteLabelParamsSettings = adminSettingsService.findAdminSettingsByKey(TenantId.SYS_TENANT_ID, WHITE_LABEL_PARAMS);
        JsonNode storedWl = null;
        String logoImageUrl = null;
        if (whiteLabelParamsSettings != null) {
            String json = whiteLabelParamsSettings.getJsonValue().get("value").asText();
            if (!StringUtils.isEmpty(json)) {
                try {
                    storedWl = objectMapper.readTree(json);
                } catch (IOException e) {
                    log.error("Unable to read System White Labeling Params!", e);
                }
            }
        }
        AdminSettings logoImageSettings = adminSettingsService.findAdminSettingsByKey(TenantId.SYS_TENANT_ID, LOGO_IMAGE);
        if (logoImageSettings != null) {
            logoImageUrl = logoImageSettings.getJsonValue().get("value").asText();
        }
        WhiteLabelingParams preparedWhiteLabelingParams = createWhiteLabelingParams(storedWl, logoImageUrl, true);
        whiteLabelingService.saveSystemWhiteLabelingParams(preparedWhiteLabelingParams);
        adminSettingsService.deleteAdminSettingsByKey(TenantId.SYS_TENANT_ID, LOGO_IMAGE);
        adminSettingsService.deleteAdminSettingsByKey(TenantId.SYS_TENANT_ID, LOGO_IMAGE_CHECKSUM);
    }

    private ListenableFuture<WhiteLabelingParams> updateEntityWhiteLabelingParameters(EntityId entityId) {
        JsonNode storedWl = getEntityWhiteLabelParams(entityId);
        String logoImageUrl = getEntityAttributeValue(entityId, LOGO_IMAGE);
        WhiteLabelingParams preparedWhiteLabelingParams = createWhiteLabelingParams(storedWl, logoImageUrl, false);
        ListenableFuture<WhiteLabelingParams> result = Futures.immediateFuture(null);
        if (entityId.getEntityType() == EntityType.TENANT) {
            result = whiteLabelingService.saveTenantWhiteLabelingParams(new TenantId(entityId.getId()), preparedWhiteLabelingParams);
        }
        if (entityId.getEntityType() == EntityType.CUSTOMER) {
            result = whiteLabelingService.saveCustomerWhiteLabelingParams(TenantId.SYS_TENANT_ID, new CustomerId(entityId.getId()), preparedWhiteLabelingParams);
        }
        deleteEntityAttribute(entityId, LOGO_IMAGE);
        deleteEntityAttribute(entityId, LOGO_IMAGE_CHECKSUM);
        return result;
    }

    private ListenableFuture<List<Void>> updateTenantMailTemplates(TenantId tenantId) {
        String mailTemplatesJsonString = getEntityAttributeValue(tenantId, MAIL_TEMPLATES);
        if (!StringUtils.isEmpty(mailTemplatesJsonString)) {
            Optional<String> updated = this.installScripts.updateMailTemplatesFromVelocityToFreeMarker(mailTemplatesJsonString);
            if (updated.isPresent()) {
                return this.saveEntityAttribute(tenantId, MAIL_TEMPLATES, updated.get());
            }
        }
        return Futures.immediateFuture(Collections.emptyList());
    }

    private void updateTenantIntegrations(TenantId tenantId) {
        PageLink pageLink = new PageLink(DEFAULT_LIMIT);
        PageData<Integration> pageData = integrationService.findTenantIntegrations(tenantId, pageLink);
        boolean hasNext = true;
        while (hasNext) {
            for (Integration integration : pageData.getData()) {
                try {
                    Field enabledField = integration.getClass().getDeclaredField("enabled");
                    enabledField.setAccessible(true);
                    Boolean booleanVal = (Boolean) enabledField.get(integration);
                    if (booleanVal == null) {
                        integration.setEnabled(true);
                        integrationService.saveIntegration(integration);
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    log.error(e.getMessage(), e);
                }
            }
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
                pageData = integrationService.findTenantIntegrations(tenantId, pageLink);
            } else {
                hasNext = false;
            }
        }
    }

    private WhiteLabelingParams createWhiteLabelingParams(JsonNode storedWl, String logoImageUrl, boolean isSystem) {
        WhiteLabelingParams whiteLabelingParams = new WhiteLabelingParams();
        whiteLabelingParams.setLogoImageUrl(logoImageUrl);
        if (storedWl != null) {
            if (storedWl.has("logoImageUrl")) {
                logoImageUrl = storedWl.get("logoImageUrl").asText();
                if (!StringUtils.isEmpty(logoImageUrl) && !"null".equals(logoImageUrl)) {
                    whiteLabelingParams.setLogoImageUrl(logoImageUrl);
                }
            }
            if (storedWl.has("logoImageHeight")) {
                int logoImageHeight = storedWl.get("logoImageHeight").asInt();
                if (logoImageHeight > 0) {
                    whiteLabelingParams.setLogoImageHeight(logoImageHeight);
                }
            }
            if (storedWl.has("appTitle")) {
                String appTitle = storedWl.get("appTitle").asText();
                if (!StringUtils.isEmpty(appTitle) && !"null".equals(appTitle)) {
                    whiteLabelingParams.setAppTitle(appTitle);
                }
            }
            if (storedWl.has("faviconUrl")) {
                String faviconUrl = storedWl.get("faviconUrl").asText();
                if (!StringUtils.isEmpty(faviconUrl) && !"null".equals(faviconUrl)) {
                    String faviconType = "";
                    if (storedWl.has("faviconType")) {
                        faviconType = storedWl.get("faviconType").asText();
                    }
                    Favicon favicon;
                    if (StringUtils.isEmpty(faviconType)) {
                        favicon = new Favicon(faviconUrl);
                    } else {
                        favicon = new Favicon(faviconUrl, faviconType);
                    }
                    whiteLabelingParams.setFavicon(favicon);
                }
            }
            if (storedWl.has("favicon")) {
                JsonNode faviconJson = storedWl.get("favicon");
                Favicon favicon = null;
                try {
                    favicon = objectMapper.treeToValue(faviconJson, Favicon.class);
                } catch (JsonProcessingException e) {
                    log.error("Unable to read Favicon from previous White Labeling Params!", e);
                }
                whiteLabelingParams.setFavicon(favicon);
            }
            if (storedWl.has("paletteSettings")) {
                JsonNode paletteSettingsJson = storedWl.get("paletteSettings");
                PaletteSettings paletteSettings = null;
                try {
                    paletteSettings = objectMapper.treeToValue(paletteSettingsJson, PaletteSettings.class);
                } catch (JsonProcessingException e) {
                    log.error("Unable to read Palette Settings from previous White Labeling Params!", e);
                }
                whiteLabelingParams.setPaletteSettings(paletteSettings);
            }
            if (storedWl.has("customCss")) {
                String customCss = storedWl.get("customCss").asText();
                if (!StringUtils.isEmpty(customCss) && !"null".equals(customCss)) {
                    whiteLabelingParams.setCustomCss(customCss);
                }
            }
        }
        if (isSystem) {
            String helpLinkBaseUrl = "https://thingsboard.io";
            if (storedWl != null && storedWl.has("helpLinkBaseUrl")) {
                JsonNode helpLinkBaseUrlJson = storedWl.get("helpLinkBaseUrl");
                if (helpLinkBaseUrlJson.isTextual()) {
                    if (!StringUtils.isEmpty(helpLinkBaseUrlJson.asText())) {
                        helpLinkBaseUrl = helpLinkBaseUrlJson.asText();
                    }
                }
            }
            whiteLabelingParams.setHelpLinkBaseUrl(helpLinkBaseUrl);
            if (storedWl != null && storedWl.has("enableHelpLinks")) {
                whiteLabelingParams.setEnableHelpLinks(storedWl.get("enableHelpLinks").asBoolean());
            } else {
                whiteLabelingParams.setEnableHelpLinks(true);
            }
        }
        return whiteLabelingParams;
    }

    private JsonNode getEntityWhiteLabelParams(EntityId entityId) {
        String value = getEntityAttributeValue(entityId, WHITE_LABEL_PARAMS);
        if (!StringUtils.isEmpty(value)) {
            try {
                return objectMapper.readTree(value);
            } catch (IOException e) {
                log.error("Unable to read White Labeling Params from JSON!", e);
                return null;
            }
        } else {
            return null;
        }
    }

    private String getEntityAttributeValue(EntityId entityId, String key) {
        List<AttributeKvEntry> attributeKvEntries = null;
        try {
            attributeKvEntries = attributesService.find(TenantId.SYS_TENANT_ID, entityId, DataConstants.SERVER_SCOPE, Arrays.asList(key)).get();
        } catch (Exception e) {
            log.error("Unable to find attribute for " + key + "!", e);
        }
        if (attributeKvEntries != null && !attributeKvEntries.isEmpty()) {
            AttributeKvEntry kvEntry = attributeKvEntries.get(0);
            return kvEntry.getValueAsString();
        } else {
            return "";
        }
    }

    private ListenableFuture<List<Void>> saveEntityAttribute(EntityId entityId, String key, String value) {
        List<AttributeKvEntry> attributes = new ArrayList<>();
        long ts = System.currentTimeMillis();
        attributes.add(new BaseAttributeKvEntry(new StringDataEntry(key, value), ts));
        try {
            return attributesService.save(TenantId.SYS_TENANT_ID, entityId, DataConstants.SERVER_SCOPE, attributes);
        } catch (Exception e) {
            log.error("Unable to save White Labeling Params to attributes!", e);
            throw new IncorrectParameterException("Unable to save White Labeling Params to attributes!");
        }
    }

    private void deleteEntityAttribute(EntityId entityId, String key) {
        try {
            attributesService.removeAll(TenantId.SYS_TENANT_ID, entityId, DataConstants.SERVER_SCOPE, Arrays.asList(key)).get();
        } catch (Exception e) {
            log.error("Unable to delete attribute for " + key + "!", e);
        }
    }

    private abstract static class WhiteLabelingPaginatedUpdater<I, D extends SearchTextBased<? extends UUIDBased>> {

        private static final int DEFAULT_LIMIT = 100;
        private int updated = 0;

        public List<ListenableFuture<WhiteLabelingParams>> updateEntities(I id) throws Exception {
            updated = 0;
            PageLink pageLink = new PageLink(DEFAULT_LIMIT);
            boolean hasNext = true;
            List<ListenableFuture<WhiteLabelingParams>> result = new ArrayList<>();
            while (hasNext) {
                PageData<D> entities = findEntities(id, pageLink);
                for (D entity : entities.getData()) {
                    result.add(updateEntity(entity));
                }
                updated += entities.getData().size();
                hasNext = entities.hasNext();
                if (hasNext) {
                    log.info("{}: {} entities updated so far...", getName(), updated);
                    pageLink = pageLink.nextPageLink();
                } else {
                    if (updated > DEFAULT_LIMIT) {
                        log.info("{}: {} total entities updated.", getName(), updated);
                    }
                }
            }
            return result;
        }

        protected abstract String getName();

        protected abstract PageData<D> findEntities(I id, PageLink pageLink);

        protected abstract ListenableFuture<WhiteLabelingParams> updateEntity(D entity) throws Exception;

    }
}
