/**
 * Thingsboard OÜ ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2017 Thingsboard OÜ. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of Thingsboard OÜ and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Thingsboard OÜ
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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.thingsboard.server.actors.service.ActorService;
import org.thingsboard.server.common.data.*;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmId;
import org.thingsboard.server.common.data.alarm.AlarmInfo;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.converter.Converter;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.*;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.plugin.ComponentDescriptor;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.data.plugin.PluginMetaData;
import org.thingsboard.server.common.data.rule.RuleMetaData;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.data.widget.WidgetType;
import org.thingsboard.server.common.data.widget.WidgetsBundle;
import org.thingsboard.server.dao.alarm.AlarmService;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.converter.ConverterService;
import org.thingsboard.server.dao.customer.CustomerService;
import org.thingsboard.server.dao.dashboard.DashboardService;
import org.thingsboard.server.dao.device.DeviceCredentialsService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.exception.IncorrectParameterException;
import org.thingsboard.server.dao.group.EntityGroupService;
import org.thingsboard.server.dao.integration.IntegrationService;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.dao.plugin.PluginService;
import org.thingsboard.server.dao.relation.RelationService;
import org.thingsboard.server.dao.rule.RuleService;
import org.thingsboard.server.dao.user.UserService;
import org.thingsboard.server.dao.widget.WidgetTypeService;
import org.thingsboard.server.dao.widget.WidgetsBundleService;
import org.thingsboard.server.exception.ThingsboardErrorCode;
import org.thingsboard.server.exception.ThingsboardErrorResponseHandler;
import org.thingsboard.server.exception.ThingsboardException;
import org.thingsboard.server.service.component.ComponentDiscoveryService;
import org.thingsboard.server.service.security.model.SecurityUser;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.thingsboard.server.dao.service.Validator.validateId;

@Slf4j
public abstract class BaseController {

    public static final String INCORRECT_TENANT_ID = "Incorrect tenantId ";
    public static final String YOU_DON_T_HAVE_PERMISSION_TO_PERFORM_THIS_OPERATION = "You don't have permission to perform this operation!";
    @Autowired
    private ThingsboardErrorResponseHandler errorResponseHandler;

    @Autowired
    protected CustomerService customerService;

    @Autowired
    protected UserService userService;

    @Autowired
    protected DeviceService deviceService;

    @Autowired
    protected AssetService assetService;

    @Autowired
    protected IntegrationService integrationService;

    @Autowired
    protected ConverterService converterService;

    @Autowired
    protected AlarmService alarmService;

    @Autowired
    protected DeviceCredentialsService deviceCredentialsService;

    @Autowired
    protected WidgetsBundleService widgetsBundleService;

    @Autowired
    protected WidgetTypeService widgetTypeService;

    @Autowired
    protected DashboardService dashboardService;

    @Autowired
    protected ComponentDiscoveryService componentDescriptorService;

    @Autowired
    protected RuleService ruleService;

    @Autowired
    protected PluginService pluginService;

    @Autowired
    protected ActorService actorService;

    @Autowired
    protected RelationService relationService;

    @Autowired
    protected EntityGroupService entityGroupService;

    @ExceptionHandler(ThingsboardException.class)
    public void handleThingsboardException(ThingsboardException ex, HttpServletResponse response) {
        errorResponseHandler.handle(ex, response);
    }

    ThingsboardException handleException(Exception exception) {
        return handleException(exception, true);
    }

    private ThingsboardException handleException(Exception exception, boolean logException) {
        if (logException) {
            log.error("Error [{}]", exception.getMessage());
        }

        String cause = "";
        if (exception.getCause() != null) {
            cause = exception.getCause().getClass().getCanonicalName();
        }

        if (exception instanceof ThingsboardException) {
            return (ThingsboardException) exception;
        } else if (exception instanceof IllegalArgumentException || exception instanceof IncorrectParameterException
                || exception instanceof DataValidationException || cause.contains("IncorrectParameterException")) {
            return new ThingsboardException(exception.getMessage(), ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        } else if (exception instanceof MessagingException) {
            return new ThingsboardException("Unable to send mail: " + exception.getMessage(), ThingsboardErrorCode.GENERAL);
        } else {
            return new ThingsboardException(exception.getMessage(), ThingsboardErrorCode.GENERAL);
        }
    }

    <T> T checkNotNull(T reference) throws ThingsboardException {
        if (reference == null) {
            throw new ThingsboardException("Requested item wasn't found!", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
        return reference;
    }

    <T> T checkNotNull(Optional<T> reference) throws ThingsboardException {
        if (reference.isPresent()) {
            return reference.get();
        } else {
            throw new ThingsboardException("Requested item wasn't found!", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
    }

    void checkParameter(String name, String param) throws ThingsboardException {
        if (StringUtils.isEmpty(param)) {
            throw new ThingsboardException("Parameter '" + name + "' can't be empty!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
    }

    void checkArrayParameter(String name, String[] params) throws ThingsboardException {
        if (params == null || params.length == 0) {
            throw new ThingsboardException("Parameter '" + name + "' can't be empty!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        } else {
            for (String param : params) {
                checkParameter(name, param);
            }
        }
    }

    EntityType checkStrEntityGroupType(String name, String strGroupType) throws ThingsboardException {
        checkParameter(name, strGroupType);
        EntityType groupType;
        try {
            groupType = EntityType.valueOf(strGroupType);
        } catch (IllegalArgumentException e) {
            throw new ThingsboardException("Unsupported entityGroup type '" + strGroupType + "'! Only 'CUSTOMER', 'ASSET' or 'DEVICE' types are allowed.", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        return checkEntityGroupType(groupType);
    }

    EntityType checkEntityGroupType(EntityType groupType) throws ThingsboardException {
        if (groupType == null) {
            throw new ThingsboardException("EntityGroup type is required!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        if (groupType != EntityType.CUSTOMER && groupType != EntityType.ASSET && groupType != EntityType.DEVICE) {
            throw new ThingsboardException("Unsupported entityGroup type '" + groupType + "'! Only 'CUSTOMER', 'ASSET' or 'DEVICE' types are allowed.", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        return groupType;
    }

    UUID toUUID(String id) {
        return UUID.fromString(id);
    }

    TimePageLink createPageLink(int limit, Long startTime, Long endTime, boolean ascOrder, String idOffset) {
        UUID idOffsetUuid = null;
        if (StringUtils.isNotEmpty(idOffset)) {
            idOffsetUuid = toUUID(idOffset);
        }
        return new TimePageLink(limit, startTime, endTime, ascOrder, idOffsetUuid);
    }


    TextPageLink createPageLink(int limit, String textSearch, String idOffset, String textOffset) {
        UUID idOffsetUuid = null;
        if (StringUtils.isNotEmpty(idOffset)) {
            idOffsetUuid = toUUID(idOffset);
        }
        return new TextPageLink(limit, textSearch, idOffsetUuid, textOffset);
    }

    protected SecurityUser getCurrentUser() throws ThingsboardException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser) {
            return (SecurityUser) authentication.getPrincipal();
        } else {
            throw new ThingsboardException("You aren't authorized to perform this operation!", ThingsboardErrorCode.AUTHENTICATION);
        }
    }

    void checkTenantId(TenantId tenantId) throws ThingsboardException {
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        SecurityUser authUser = getCurrentUser();
        if (authUser.getAuthority() != Authority.SYS_ADMIN &&
                (authUser.getTenantId() == null || !authUser.getTenantId().equals(tenantId))) {
            throw new ThingsboardException(YOU_DON_T_HAVE_PERMISSION_TO_PERFORM_THIS_OPERATION,
                    ThingsboardErrorCode.PERMISSION_DENIED);
        }
    }

    protected TenantId getTenantId() throws ThingsboardException {
        return getCurrentUser().getTenantId();
    }

    Customer checkCustomerId(CustomerId customerId) throws ThingsboardException {
        try {
            validateId(customerId, "Incorrect customerId " + customerId);
            SecurityUser authUser = getCurrentUser();
            if (authUser.getAuthority() == Authority.SYS_ADMIN ||
                    (authUser.getAuthority() != Authority.TENANT_ADMIN &&
                            (authUser.getCustomerId() == null || !authUser.getCustomerId().equals(customerId)))) {
                throw new ThingsboardException(YOU_DON_T_HAVE_PERMISSION_TO_PERFORM_THIS_OPERATION,
                        ThingsboardErrorCode.PERMISSION_DENIED);
            }
            Customer customer = customerService.findCustomerById(customerId);
            checkCustomer(customer);
            return customer;
        } catch (Exception e) {
            throw handleException(e, false);
        }
    }

    private void checkCustomer(Customer customer) throws ThingsboardException {
        checkNotNull(customer);
        checkTenantId(customer.getTenantId());
    }

    User checkUserId(UserId userId) throws ThingsboardException {
        try {
            validateId(userId, "Incorrect userId " + userId);
            User user = userService.findUserById(userId);
            checkUser(user);
            return user;
        } catch (Exception e) {
            throw handleException(e, false);
        }
    }

    private void checkUser(User user) throws ThingsboardException {
        checkNotNull(user);
        checkTenantId(user.getTenantId());
        if (user.getAuthority() == Authority.CUSTOMER_USER) {
            checkCustomerId(user.getCustomerId());
        }
    }

    protected void checkEntityId(EntityId entityId) throws ThingsboardException {
        try {
            checkNotNull(entityId);
            validateId(entityId.getId(), "Incorrect entityId " + entityId);
            switch (entityId.getEntityType()) {
                case DEVICE:
                    checkDevice(deviceService.findDeviceById(new DeviceId(entityId.getId())));
                    return;
                case CUSTOMER:
                    checkCustomerId(new CustomerId(entityId.getId()));
                    return;
                case TENANT:
                    checkTenantId(new TenantId(entityId.getId()));
                    return;
                case PLUGIN:
                    checkPlugin(new PluginId(entityId.getId()));
                    return;
                case RULE:
                    checkRule(new RuleId(entityId.getId()));
                    return;
                case ASSET:
                    checkAsset(assetService.findAssetById(new AssetId(entityId.getId())));
                    return;
                case INTEGRATION:
                    checkIntegration(integrationService.findIntegrationById(new IntegrationId(entityId.getId())));
                    return;
                case CONVERTER:
                    checkConverter(converterService.findConverterById(new ConverterId(entityId.getId())));
                    return;
                case DASHBOARD:
                    checkDashboardId(new DashboardId(entityId.getId()));
                    return;
                case USER:
                    checkUserId(new UserId(entityId.getId()));
                    return;
                case ENTITY_GROUP:
                    checkEntityGroupId(new EntityGroupId(entityId.getId()));
                    return;
                default:
                    throw new IllegalArgumentException("Unsupported entity type: " + entityId.getEntityType());
            }
        } catch (Exception e) {
            throw handleException(e, false);
        }
    }

    Device checkDeviceId(DeviceId deviceId) throws ThingsboardException {
        try {
            validateId(deviceId, "Incorrect deviceId " + deviceId);
            Device device = deviceService.findDeviceById(deviceId);
            checkDevice(device);
            return device;
        } catch (Exception e) {
            throw handleException(e, false);
        }
    }

    protected void checkDevice(Device device) throws ThingsboardException {
        checkNotNull(device);
        checkTenantId(device.getTenantId());
        if (device.getCustomerId() != null && !device.getCustomerId().getId().equals(ModelConstants.NULL_UUID)) {
            checkCustomerId(device.getCustomerId());
        }
    }

    Asset checkAssetId(AssetId assetId) throws ThingsboardException {
        try {
            validateId(assetId, "Incorrect assetId " + assetId);
            Asset asset = assetService.findAssetById(assetId);
            checkAsset(asset);
            return asset;
        } catch (Exception e) {
            throw handleException(e, false);
        }
    }

    protected void checkAsset(Asset asset) throws ThingsboardException {
        checkNotNull(asset);
        checkTenantId(asset.getTenantId());
        if (asset.getCustomerId() != null && !asset.getCustomerId().getId().equals(ModelConstants.NULL_UUID)) {
            checkCustomerId(asset.getCustomerId());
        }
    }

    Integration checkIntegrationId(IntegrationId integrationId) throws ThingsboardException {
        try {
            validateId(integrationId, "Incorrect integrationId " + integrationId);
            Integration integration = integrationService.findIntegrationById(integrationId);
            checkIntegration(integration);
            return integration;
        } catch (Exception e) {
            throw handleException(e, false);
        }
    }

    protected void checkIntegration(Integration integration) throws ThingsboardException {
        checkNotNull(integration);
        checkTenantId(integration.getTenantId());
        if (integration.getDefaultConverterId() != null && !integration.getDefaultConverterId().getId().equals(ModelConstants.NULL_UUID)) {
            checkConverterId(integration.getDefaultConverterId());
        }
    }

    Converter checkConverterId(ConverterId converterId) throws ThingsboardException {
        try {
            validateId(converterId, "Incorrect converterId " + converterId);
            Converter converter = converterService.findConverterById(converterId);
            checkConverter(converter);
            return converter;
        } catch (Exception e) {
            throw handleException(e, false);
        }
    }

    protected void checkConverter(Converter converter) throws ThingsboardException {
        checkNotNull(converter);
        checkTenantId(converter.getTenantId());

    }

    Alarm checkAlarmId(AlarmId alarmId) throws ThingsboardException {
        try {
            validateId(alarmId, "Incorrect alarmId " + alarmId);
            Alarm alarm = alarmService.findAlarmByIdAsync(alarmId).get();
            checkAlarm(alarm);
            return alarm;
        } catch (Exception e) {
            throw handleException(e, false);
        }
    }

    AlarmInfo checkAlarmInfoId(AlarmId alarmId) throws ThingsboardException {
        try {
            validateId(alarmId, "Incorrect alarmId " + alarmId);
            AlarmInfo alarmInfo = alarmService.findAlarmInfoByIdAsync(alarmId).get();
            checkAlarm(alarmInfo);
            return alarmInfo;
        } catch (Exception e) {
            throw handleException(e, false);
        }
    }

    protected void checkAlarm(Alarm alarm) throws ThingsboardException {
        checkNotNull(alarm);
        checkTenantId(alarm.getTenantId());
    }

    WidgetsBundle checkWidgetsBundleId(WidgetsBundleId widgetsBundleId, boolean modify) throws ThingsboardException {
        try {
            validateId(widgetsBundleId, "Incorrect widgetsBundleId " + widgetsBundleId);
            WidgetsBundle widgetsBundle = widgetsBundleService.findWidgetsBundleById(widgetsBundleId);
            checkWidgetsBundle(widgetsBundle, modify);
            return widgetsBundle;
        } catch (Exception e) {
            throw handleException(e, false);
        }
    }

    private void checkWidgetsBundle(WidgetsBundle widgetsBundle, boolean modify) throws ThingsboardException {
        checkNotNull(widgetsBundle);
        if (widgetsBundle.getTenantId() != null && !widgetsBundle.getTenantId().getId().equals(ModelConstants.NULL_UUID)) {
            checkTenantId(widgetsBundle.getTenantId());
        } else if (modify && getCurrentUser().getAuthority() != Authority.SYS_ADMIN) {
            throw new ThingsboardException(YOU_DON_T_HAVE_PERMISSION_TO_PERFORM_THIS_OPERATION,
                    ThingsboardErrorCode.PERMISSION_DENIED);
        }
    }

    WidgetType checkWidgetTypeId(WidgetTypeId widgetTypeId, boolean modify) throws ThingsboardException {
        try {
            validateId(widgetTypeId, "Incorrect widgetTypeId " + widgetTypeId);
            WidgetType widgetType = widgetTypeService.findWidgetTypeById(widgetTypeId);
            checkWidgetType(widgetType, modify);
            return widgetType;
        } catch (Exception e) {
            throw handleException(e, false);
        }
    }

    void checkWidgetType(WidgetType widgetType, boolean modify) throws ThingsboardException {
        checkNotNull(widgetType);
        if (widgetType.getTenantId() != null && !widgetType.getTenantId().getId().equals(ModelConstants.NULL_UUID)) {
            checkTenantId(widgetType.getTenantId());
        } else if (modify && getCurrentUser().getAuthority() != Authority.SYS_ADMIN) {
            throw new ThingsboardException(YOU_DON_T_HAVE_PERMISSION_TO_PERFORM_THIS_OPERATION,
                    ThingsboardErrorCode.PERMISSION_DENIED);
        }
    }

    Dashboard checkDashboardId(DashboardId dashboardId) throws ThingsboardException {
        try {
            validateId(dashboardId, "Incorrect dashboardId " + dashboardId);
            Dashboard dashboard = dashboardService.findDashboardById(dashboardId);
            checkDashboard(dashboard, true);
            return dashboard;
        } catch (Exception e) {
            throw handleException(e, false);
        }
    }

    DashboardInfo checkDashboardInfoId(DashboardId dashboardId) throws ThingsboardException {
        try {
            validateId(dashboardId, "Incorrect dashboardId " + dashboardId);
            DashboardInfo dashboardInfo = dashboardService.findDashboardInfoById(dashboardId);
            SecurityUser authUser = getCurrentUser();
            checkDashboard(dashboardInfo, authUser.getAuthority() != Authority.SYS_ADMIN);
            return dashboardInfo;
        } catch (Exception e) {
            throw handleException(e, false);
        }
    }

    private void checkDashboard(DashboardInfo dashboard, boolean checkCustomerId) throws ThingsboardException {
        checkNotNull(dashboard);
        checkTenantId(dashboard.getTenantId());
        SecurityUser authUser = getCurrentUser();
        if (authUser.getAuthority() == Authority.CUSTOMER_USER) {
            if (dashboard.getCustomerId() == null || dashboard.getCustomerId().getId().equals(ModelConstants.NULL_UUID)) {
                throw new ThingsboardException(YOU_DON_T_HAVE_PERMISSION_TO_PERFORM_THIS_OPERATION,
                        ThingsboardErrorCode.PERMISSION_DENIED);
            }
        }
        if (checkCustomerId &&
                dashboard.getCustomerId() != null && !dashboard.getCustomerId().getId().equals(ModelConstants.NULL_UUID)) {
            checkCustomerId(dashboard.getCustomerId());
        }
    }

    ComponentDescriptor checkComponentDescriptorByClazz(String clazz) throws ThingsboardException {
        try {
            log.debug("[{}] Lookup component descriptor", clazz);
            ComponentDescriptor componentDescriptor = checkNotNull(componentDescriptorService.getComponent(clazz));
            return componentDescriptor;
        } catch (Exception e) {
            throw handleException(e, false);
        }
    }

    List<ComponentDescriptor> checkComponentDescriptorsByType(ComponentType type) throws ThingsboardException {
        try {
            log.debug("[{}] Lookup component descriptors", type);
            return componentDescriptorService.getComponents(type);
        } catch (Exception e) {
            throw handleException(e, false);
        }
    }

    List<ComponentDescriptor> checkPluginActionsByPluginClazz(String pluginClazz) throws ThingsboardException {
        try {
            checkComponentDescriptorByClazz(pluginClazz);
            log.debug("[{}] Lookup plugin actions", pluginClazz);
            return componentDescriptorService.getPluginActions(pluginClazz);
        } catch (Exception e) {
            throw handleException(e, false);
        }
    }

    protected PluginMetaData checkPlugin(PluginMetaData plugin) throws ThingsboardException {
        checkNotNull(plugin);
        SecurityUser authUser = getCurrentUser();
        TenantId tenantId = plugin.getTenantId();
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        if (authUser.getAuthority() != Authority.SYS_ADMIN) {
            if (authUser.getTenantId() == null ||
                    !tenantId.getId().equals(ModelConstants.NULL_UUID) && !authUser.getTenantId().equals(tenantId)) {
                throw new ThingsboardException(YOU_DON_T_HAVE_PERMISSION_TO_PERFORM_THIS_OPERATION,
                        ThingsboardErrorCode.PERMISSION_DENIED);

            } else if (tenantId.getId().equals(ModelConstants.NULL_UUID)) {
                plugin.setConfiguration(null);
            }
        }
        return plugin;
    }

    protected PluginMetaData checkPlugin(PluginId pluginId) throws ThingsboardException {
        checkNotNull(pluginId);
        return checkPlugin(pluginService.findPluginById(pluginId));
    }

    protected RuleMetaData checkRule(RuleId ruleId) throws ThingsboardException {
        checkNotNull(ruleId);
        return checkRule(ruleService.findRuleById(ruleId));
    }

    protected RuleMetaData checkRule(RuleMetaData rule) throws ThingsboardException {
        checkNotNull(rule);
        SecurityUser authUser = getCurrentUser();
        TenantId tenantId = rule.getTenantId();
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        if (authUser.getAuthority() != Authority.SYS_ADMIN) {
            if (authUser.getTenantId() == null ||
                    !tenantId.getId().equals(ModelConstants.NULL_UUID) && !authUser.getTenantId().equals(tenantId)) {
                throw new ThingsboardException(YOU_DON_T_HAVE_PERMISSION_TO_PERFORM_THIS_OPERATION,
                        ThingsboardErrorCode.PERMISSION_DENIED);

            }
        }
        return rule;
    }

    protected EntityGroup checkEntityGroupId(EntityGroupId entityGroupId) throws ThingsboardException {
        try {
            validateId(entityGroupId, "Incorrect entityGroupId " + entityGroupId);
            EntityGroup entityGroup = entityGroupService.findEntityGroupById(entityGroupId);
            checkEntityGroup(entityGroup);
            return entityGroup;
        } catch (Exception e) {
            throw handleException(e, false);
        }
    }

    protected void checkEntityGroup(EntityGroup entityGroup) throws ThingsboardException {
        checkNotNull(entityGroup);
        try {
            if (!entityGroupService.checkEntityGroup(getTenantId(), entityGroup).get()) {
                throw new ThingsboardException("You don't have permission to perform this operation!",
                        ThingsboardErrorCode.PERMISSION_DENIED);
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    protected String constructBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        if (request.getHeader("x-forwarded-proto") != null) {
            scheme = request.getHeader("x-forwarded-proto");
        }
        int serverPort = request.getServerPort();
        if (request.getHeader("x-forwarded-port") != null) {
            try {
                serverPort = request.getIntHeader("x-forwarded-port");
            } catch (NumberFormatException e) {
            }
        }

        String baseUrl = String.format("%s://%s:%d",
                scheme,
                request.getServerName(),
                serverPort);
        return baseUrl;
    }
}
