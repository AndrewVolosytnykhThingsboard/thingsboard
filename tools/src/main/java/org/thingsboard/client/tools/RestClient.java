/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2019 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.client.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.async.DeferredResult;
import org.thingsboard.server.common.data.AdminSettings;
import org.thingsboard.server.common.data.ClaimRequest;
import org.thingsboard.server.common.data.ContactBased;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DashboardInfo;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntitySubtype;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.Event;
import org.thingsboard.server.common.data.ShortEntityView;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.UpdateMessage;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmInfo;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.asset.AssetSearchQuery;
import org.thingsboard.server.common.data.audit.AuditLog;
import org.thingsboard.server.common.data.blob.BlobEntityInfo;
import org.thingsboard.server.common.data.converter.Converter;
import org.thingsboard.server.common.data.device.DeviceSearchQuery;
import org.thingsboard.server.common.data.entityview.EntityViewSearchQuery;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.group.EntityGroupInfo;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.menu.CustomMenu;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.page.TimePageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.permission.AllowedPermissionsInfo;
import org.thingsboard.server.common.data.permission.GroupPermission;
import org.thingsboard.server.common.data.permission.GroupPermissionInfo;
import org.thingsboard.server.common.data.permission.Resource;
import org.thingsboard.server.common.data.plugin.ComponentDescriptor;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.EntityRelationInfo;
import org.thingsboard.server.common.data.relation.EntityRelationsQuery;
import org.thingsboard.server.common.data.report.ReportConfig;
import org.thingsboard.server.common.data.role.Role;
import org.thingsboard.server.common.data.role.RoleType;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.scheduler.SchedulerEvent;
import org.thingsboard.server.common.data.scheduler.SchedulerEventInfo;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.data.security.DeviceCredentialsType;
import org.thingsboard.server.common.data.security.model.SecuritySettings;
import org.thingsboard.server.common.data.security.model.UserPasswordPolicy;
import org.thingsboard.server.common.data.selfregistration.SelfRegistrationParams;
import org.thingsboard.server.common.data.selfregistration.SignUpSelfRegistrationParams;
import org.thingsboard.server.common.data.signup.SignUpRequest;
import org.thingsboard.server.common.data.signup.SignUpResult;
import org.thingsboard.server.common.data.translation.CustomTranslation;
import org.thingsboard.server.common.data.widget.WidgetType;
import org.thingsboard.server.common.data.widget.WidgetsBundle;
import org.thingsboard.server.common.data.wl.LoginWhiteLabelingParams;
import org.thingsboard.server.common.data.wl.WhiteLabelingParams;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.util.StringUtils.isEmpty;

/**
 * @author Andrew Shvayka
 */
public class RestClient implements ClientHttpRequestInterceptor {
    private static final String JWT_TOKEN_HEADER_PARAM = "X-Authorization";
    protected final RestTemplate restTemplate;
    protected final String baseURL;
    private String token;
    private String refreshToken;
    private final ObjectMapper objectMapper = new ObjectMapper();


    protected static final String ACTIVATE_TOKEN_REGEX = "/api/noauth/activate?activateToken=";

    public RestClient(String baseURL) {
        this.restTemplate = new RestTemplate();
        this.baseURL = baseURL;
    }

    public RestClient(RestTemplate restTemplate, String baseURL) {
        this.restTemplate = restTemplate;
        this.baseURL = baseURL;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] bytes, ClientHttpRequestExecution execution) throws IOException {
        HttpRequest wrapper = new HttpRequestWrapper(request);
        wrapper.getHeaders().set(JWT_TOKEN_HEADER_PARAM, "Bearer " + token);
        ClientHttpResponse response = execution.execute(wrapper, bytes);
        if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            synchronized (this) {
                restTemplate.getInterceptors().remove(this);
                refreshToken();
                wrapper.getHeaders().set(JWT_TOKEN_HEADER_PARAM, "Bearer " + token);
                return execution.execute(wrapper, bytes);
            }
        }
        return response;
    }

    public String getToken() {
        return token;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void refreshToken() {
        Map<String, String> refreshTokenRequest = new HashMap<>();
        refreshTokenRequest.put("refreshToken", refreshToken);
        ResponseEntity<JsonNode> tokenInfo = restTemplate.postForEntity(baseURL + "/api/auth/token", refreshTokenRequest, JsonNode.class);
        setTokenInfo(tokenInfo.getBody());
    }

    public void login(String username, String password) {
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", username);
        loginRequest.put("password", password);
        ResponseEntity<JsonNode> tokenInfo = restTemplate.postForEntity(baseURL + "/api/auth/login", loginRequest, JsonNode.class);
        setTokenInfo(tokenInfo.getBody());
    }

    private void setTokenInfo(JsonNode tokenInfo) {
        this.token = tokenInfo.get("token").asText();
        this.refreshToken = tokenInfo.get("refreshToken").asText();
        restTemplate.getInterceptors().add(this);
    }

    public Optional<Device> findDevice(String name) {
        Map<String, String> params = new HashMap<String, String>();
        params.put("deviceName", name);
        try {
            ResponseEntity<Device> deviceEntity = restTemplate.getForEntity(baseURL + "/api/tenant/devices?deviceName={deviceName}", Device.class, params);
            return Optional.of(deviceEntity.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<Customer> findCustomer(String title) {
        Map<String, String> params = new HashMap<String, String>();
        params.put("customerTitle", title);
        try {
            ResponseEntity<Customer> customerEntity = restTemplate.getForEntity(baseURL + "/api/tenant/customers?customerTitle={customerTitle}", Customer.class, params);
            return Optional.of(customerEntity.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<Asset> findAsset(String name) {
        Map<String, String> params = new HashMap<String, String>();
        params.put("assetName", name);
        try {
            ResponseEntity<Asset> assetEntity = restTemplate.getForEntity(baseURL + "/api/tenant/assets?assetName={assetName}", Asset.class, params);
            return Optional.of(assetEntity.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<JsonNode> getAttributes(String accessToken, String clientKeys, String sharedKeys) {
        Map<String, String> params = new HashMap<>();
        params.put("accessToken", accessToken);
        params.put("clientKeys", clientKeys);
        params.put("sharedKeys", sharedKeys);
        try {
            ResponseEntity<JsonNode> telemetryEntity = restTemplate.getForEntity(baseURL + "/api/v1/{accessToken}/attributes?clientKeys={clientKeys}&sharedKeys={sharedKeys}", JsonNode.class, params);
            return Optional.of(telemetryEntity.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Customer createCustomer(Customer customer) {
        return restTemplate.postForEntity(baseURL + "/api/customer", customer, Customer.class).getBody();
    }

    public Customer createCustomer(String title) {
        Customer customer = new Customer();
        customer.setTitle(title);
        return restTemplate.postForEntity(baseURL + "/api/customer", customer, Customer.class).getBody();
    }

    public Device createDevice(String name, String type) {
        Device device = new Device();
        device.setName(name);
        device.setType(type);
        return restTemplate.postForEntity(baseURL + "/api/device", device, Device.class).getBody();
    }

    public DeviceCredentials updateDeviceCredentials(DeviceId deviceId, String token) {
        DeviceCredentials deviceCredentials = getCredentials(deviceId);
        deviceCredentials.setCredentialsType(DeviceCredentialsType.ACCESS_TOKEN);
        deviceCredentials.setCredentialsId(token);
        return saveDeviceCredentials(deviceCredentials);
    }

    public Device createDevice(Device device) {
        return restTemplate.postForEntity(baseURL + "/api/device", device, Device.class).getBody();
    }

    public Asset createAsset(Asset asset) {
        return restTemplate.postForEntity(baseURL + "/api/asset", asset, Asset.class).getBody();
    }

    public Asset createAsset(String name, String type) {
        Asset asset = new Asset();
        asset.setName(name);
        asset.setType(type);
        return restTemplate.postForEntity(baseURL + "/api/asset", asset, Asset.class).getBody();
    }

    public Alarm createAlarm(Alarm alarm) {
        return restTemplate.postForEntity(baseURL + "/api/alarm", alarm, Alarm.class).getBody();
    }

    public void deleteCustomer(CustomerId customerId) {
        restTemplate.delete(baseURL + "/api/customer/{customerId}", customerId);
    }

    public void deleteDevice(DeviceId deviceId) {
        restTemplate.delete(baseURL + "/api/device/{deviceId}", deviceId);
    }

    public void deleteAsset(AssetId assetId) {
        restTemplate.delete(baseURL + "/api/asset/{assetId}", assetId);
    }

    public EntityRelation makeRelation(String relationType, EntityId idFrom, EntityId idTo) {
        EntityRelation relation = new EntityRelation();
        relation.setFrom(idFrom);
        relation.setTo(idTo);
        relation.setType(relationType);
        return restTemplate.postForEntity(baseURL + "/api/relation", relation, EntityRelation.class).getBody();
    }

    public Dashboard createDashboard(Dashboard dashboard) {
        return restTemplate.postForEntity(baseURL + "/api/dashboard", dashboard, Dashboard.class).getBody();
    }

    public void deleteDashboard(DashboardId dashboardId) {
        restTemplate.delete(baseURL + "/api/dashboard/{dashboardId}", dashboardId);
    }

    public DeviceCredentials getCredentials(DeviceId id) {
        return restTemplate.getForEntity(baseURL + "/api/device/" + id.getId().toString() + "/credentials", DeviceCredentials.class).getBody();
    }

    public RestTemplate getRestTemplate() {
        return restTemplate;
    }

    public String getActivateToken(String userId) {
        String activationLink = getActivationLink(userId);
        return StringUtils.delete(activationLink, baseURL + ACTIVATE_TOKEN_REGEX);
    }

    //Admin

    public Optional<AdminSettings> getAdminSettings(String key) {
        try {
            ResponseEntity<AdminSettings> adminSettings = restTemplate.getForEntity(baseURL + "/api/admin/settings/{key}", AdminSettings.class, key);
            return Optional.ofNullable(adminSettings.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public AdminSettings saveAdminSettings(AdminSettings adminSettings) {
        return restTemplate.postForEntity(baseURL + "/api/admin/settings", adminSettings, AdminSettings.class).getBody();
    }

    public void sendTestMail(AdminSettings adminSettings) {
        restTemplate.postForEntity(baseURL + "/api/admin/settings/testMail", adminSettings, AdminSettings.class);
    }

    public Optional<SecuritySettings> getSecuritySettings() {
        try {
            ResponseEntity<SecuritySettings> securitySettings = restTemplate.getForEntity(baseURL + "/api/admin/securitySettings", SecuritySettings.class);
            return Optional.ofNullable(securitySettings.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public SecuritySettings saveSecuritySettings(SecuritySettings securitySettings) {
        return restTemplate.postForEntity(baseURL + "/api/admin/securitySettings", securitySettings, SecuritySettings.class).getBody();
    }

    public Optional<UpdateMessage> checkUpdates() {
        try {
            ResponseEntity<UpdateMessage> updateMsg = restTemplate.getForEntity(baseURL + "/api/admin/updates", UpdateMessage.class);
            return Optional.ofNullable(updateMsg.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    //Alarm

    public Optional<Alarm> getAlarmById(String alarmId) {
        try {
            ResponseEntity<Alarm> alarm = restTemplate.getForEntity(baseURL + "/api/alarm/{alarmId}", Alarm.class, alarmId);
            return Optional.ofNullable(alarm.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<AlarmInfo> getAlarmInfoById(String alarmId) {
        try {
            ResponseEntity<AlarmInfo> alarmInfo = restTemplate.getForEntity(baseURL + "/api/alarm/info/{alarmId}", AlarmInfo.class, alarmId);
            return Optional.ofNullable(alarmInfo.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Alarm saveAlarm(Alarm alarm) {
        return restTemplate.postForEntity(baseURL + "/api/alarm", alarm, Alarm.class).getBody();
    }

    public void deleteAlarm(String alarmId) {
        restTemplate.delete(baseURL + "/api/alarm/{alarmId}", alarmId);
    }

    public void ackAlarm(String alarmId) {
        restTemplate.postForObject(baseURL + "/api/alarm/{alarmId}/ack", new Object(), Object.class, alarmId);
    }

    public void clearAlarm(String alarmId) {
        restTemplate.postForObject(baseURL + "/api/alarm/{alarmId}/clear", new Object(), Object.class, alarmId);
    }

    public TimePageData<AlarmInfo> getAlarms(String entityType, String entityId, String searchStatus, String status, TimePageLink pageLink, Boolean fetchOriginator) {
        Map<String, String> params = new HashMap<>();
        params.put("entityType", entityType);
        params.put("entityId", entityId);
        params.put("searchStatus", searchStatus);
        params.put("status", status);
        params.put("fetchOriginator", String.valueOf(fetchOriginator));
        addPageLinkToParam(params, pageLink);

        String urlParams = getUrlParams(pageLink);
        return restTemplate.exchange(
                baseURL + "/api/alarm/{entityType}/{entityId}?searchStatus={searchStatus}&status={status}&fetchOriginator={fetchOriginator}&" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TimePageData<AlarmInfo>>() {
                }, params).getBody();
    }

    private String getUrlParams(TimePageLink pageLink) {
        String urlParams = "limit={limit}&ascOrder={ascOrder}";
        if (pageLink.getStartTime() != null) {
            urlParams += "&startTime={startTime}";
        }
        if (pageLink.getEndTime() != null) {
            urlParams += "&endTime={endTime}";
        }
        if (pageLink.getIdOffset() != null) {
            urlParams += "&offset={offset}";
        }
        return urlParams;
    }

    private String getUrlParams(TextPageLink pageLink) {
        String urlParams = "limit={limit}&ascOrder={ascOrder}";
        if (!isEmpty(pageLink.getTextSearch())) {
            urlParams += "&textSearch={textSearch}";
        }
        if (!isEmpty(pageLink.getIdOffset())) {
            urlParams += "&idOffset={idOffset}";
        }
        if (!isEmpty(pageLink.getTextOffset())) {
            urlParams += "&textOffset={textOffset}";
        }
        return urlParams;
    }

    public Optional<AlarmSeverity> getHighestAlarmSeverity(String entityType, String entityId, String searchStatus, String status) {
        Map<String, String> params = new HashMap<>();
        params.put("entityType", entityType);
        params.put("entityId", entityId);
        params.put("searchStatus", searchStatus);
        params.put("status", status);
        try {
            ResponseEntity<AlarmSeverity> alarmSeverity = restTemplate.getForEntity(baseURL + "/api/alarm/highestSeverity/{entityType}/{entityId}?searchStatus={searchStatus}&status={status}", AlarmSeverity.class, params);
            return Optional.ofNullable(alarmSeverity.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    //Asset

    public Optional<Asset> getAssetById(String assetId) {
        try {
            ResponseEntity<Asset> asset = restTemplate.getForEntity(baseURL + "/api/asset/{assetId}", Asset.class, assetId);
            return Optional.ofNullable(asset.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Asset saveAsset(Asset asset) {
        return restTemplate.postForEntity(baseURL + "/api/asset", asset, Asset.class).getBody();
    }

    public void deleteAsset(String assetId) {
        restTemplate.delete(baseURL + "/api/asset/{assetId}", assetId);
    }

    public TextPageData<Asset> getTenantAssets(TextPageLink pageLink, String type) {
        Map<String, String> params = new HashMap<>();
        params.put("type", type);
        addPageLinkToParam(params, pageLink);

        ResponseEntity<TextPageData<Asset>> assets = restTemplate.exchange(
                baseURL + "/tenant/assets?type={type}&" + getUrlParams(pageLink),
                HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<Asset>>() {
                },
                params);
        return assets.getBody();
    }

    public Optional<Asset> getTenantAsset(String assetName) {
        try {
            ResponseEntity<Asset> asset = restTemplate.getForEntity(baseURL + "/api/tenant/assets?assetName={assetName}", Asset.class, assetName);
            return Optional.ofNullable(asset.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public TextPageData<Asset> getCustomerAssets(String customerId, TextPageLink pageLink, String type) {
        Map<String, String> params = new HashMap<>();
        params.put("customerId", customerId);
        params.put("type", type);
        addPageLinkToParam(params, pageLink);

        ResponseEntity<TextPageData<Asset>> assets = restTemplate.exchange(
                baseURL + "/api/customer/{customerId}/assets?type={type}&" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<Asset>>() {
                },
                params);
        return assets.getBody();
    }

    public TextPageData<Asset> getUserAssets(String type, TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        params.put("type", type);
        addPageLinkToParam(params, pageLink);
        return restTemplate.exchange(
                baseURL + "/api/user/assets?type={type}&" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<Asset>>() {
                },
                params).getBody();
    }

    public List<Asset> getAssetsByIds(String[] assetIds) {
        return restTemplate.exchange(
                baseURL + "/api/assets?assetIds={assetIds}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<Asset>>() {
                },
                String.join(",", assetIds)).getBody();
    }

    public List<Asset> findByQuery(AssetSearchQuery query) {
        return restTemplate.exchange(
                URI.create(baseURL + "/api/assets"),
                HttpMethod.POST,
                new HttpEntity<>(query),
                new ParameterizedTypeReference<List<Asset>>() {
                }).getBody();
    }

    public List<EntitySubtype> getAssetTypes() {
        return restTemplate.exchange(URI.create(
                baseURL + "/api/asset/types"),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<EntitySubtype>>() {
                }).getBody();
    }

    //AuditLog

    public TimePageData<AuditLog> getAuditLogsByCustomerId(String customerId, TimePageLink pageLink, String actionTypes) {
        Map<String, String> params = new HashMap<>();
        params.put("customerId", customerId);
        params.put("actionTypes", actionTypes);
        addPageLinkToParam(params, pageLink);

        ResponseEntity<TimePageData<AuditLog>> auditLog = restTemplate.exchange(
                baseURL + "/api/audit/logs/customer/{customerId}?actionTypes={actionTypes}&" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TimePageData<AuditLog>>() {
                },
                params);
        return auditLog.getBody();
    }

    public TimePageData<AuditLog> getAuditLogsByUserId(String userId, TimePageLink pageLink, String actionTypes) {
        Map<String, String> params = new HashMap<>();
        params.put("userId", userId);
        params.put("actionTypes", actionTypes);
        addPageLinkToParam(params, pageLink);

        ResponseEntity<TimePageData<AuditLog>> auditLog = restTemplate.exchange(
                baseURL + "/api/audit/logs/user/{userId}?actionTypes={actionTypes}&" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TimePageData<AuditLog>>() {
                },
                params);
        return auditLog.getBody();
    }

    public TimePageData<AuditLog> getAuditLogsByEntityId(String entityType, String entityId, String actionTypes, TimePageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        params.put("entityType", entityType);
        params.put("entityId", entityId);
        params.put("actionTypes", actionTypes);
        addPageLinkToParam(params, pageLink);

        ResponseEntity<TimePageData<AuditLog>> auditLog = restTemplate.exchange(
                baseURL + "/api/audit/logs/entity/{entityType}/{entityId}?actionTypes={actionTypes}&" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TimePageData<AuditLog>>() {
                },
                params);
        return auditLog.getBody();
    }

    public TimePageData<AuditLog> getAuditLogs(TimePageLink pageLink, String actionTypes) {
        Map<String, String> params = new HashMap<>();
        params.put("actionTypes", actionTypes);
        addPageLinkToParam(params, pageLink);

        ResponseEntity<TimePageData<AuditLog>> auditLog = restTemplate.exchange(
                baseURL + "/api/audit/logs?actionTypes={actionTypes}&" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TimePageData<AuditLog>>() {
                },
                params);
        return auditLog.getBody();
    }

    //Auth

    public Optional<User> getUser() {
        ResponseEntity<User> user = restTemplate.getForEntity(baseURL + "/api/auth/user", User.class);
        return Optional.ofNullable(user.getBody());
    }

    public void logout() {
        restTemplate.exchange(URI.create(baseURL + "/api/auth/logout"), HttpMethod.POST, HttpEntity.EMPTY, Object.class);
    }

    public void changePassword(String currentPassword, String newPassword) {
        ObjectNode changePasswordRequest = objectMapper.createObjectNode();
        changePasswordRequest.put("currentPassword", currentPassword);
        changePasswordRequest.put("newPassword", newPassword);
        restTemplate.exchange(URI.create(baseURL + "/api/auth/changePassword"), HttpMethod.POST, new HttpEntity<>(changePasswordRequest), Object.class);
    }

    public Optional<UserPasswordPolicy> getUserPasswordPolicy() {
        try {
            ResponseEntity<UserPasswordPolicy> userPasswordPolicy = restTemplate.getForEntity(baseURL + "/api/noauth/userPasswordPolicy", UserPasswordPolicy.class);
            return Optional.ofNullable(userPasswordPolicy.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public ResponseEntity<String> checkActivateToken(String userId) {
        String activateToken = getActivateToken(userId);
        return restTemplate.getForEntity(baseURL + "/api/noauth/activate?activateToken={activateToken}", String.class, activateToken);
    }

    public void requestResetPasswordByEmail(String email) {
        ObjectNode resetPasswordByEmailRequest = objectMapper.createObjectNode();
        resetPasswordByEmailRequest.put("email", email);
        restTemplate.exchange(URI.create(baseURL + "/api/noauth/resetPasswordByEmail"), HttpMethod.POST, new HttpEntity<>(resetPasswordByEmailRequest), Object.class);
    }

    public Optional<JsonNode> activateUser(String userId, String password) {
        ObjectNode activateRequest = objectMapper.createObjectNode();
        activateRequest.put("activateToken", getActivateToken(userId));
        activateRequest.put("password", password);
        try {
            ResponseEntity<JsonNode> jsonNode = restTemplate.postForEntity(baseURL + "/api/noauth/activate", activateRequest, JsonNode.class);
            return Optional.ofNullable(jsonNode.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    //BlobEntity

    public Optional<BlobEntityInfo> getBlobEntityInfoById(String blobEntityId) {
        try {
            ResponseEntity<BlobEntityInfo> blobEntityInfo = restTemplate.getForEntity(baseURL + "/api/blobEntity/info/{blobEntityId}", BlobEntityInfo.class, blobEntityId);
            return Optional.ofNullable(blobEntityInfo.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public ResponseEntity<Resource> downloadBlobEntity(String blobEntityId) {
        return restTemplate.exchange(
                baseURL + "/api/blobEntity/{blobEntityId}/download",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<ResponseEntity<Resource>>() {
                },
                blobEntityId).getBody();
    }

    public void deleteBlobEntity(String blobEntityId) {
        restTemplate.delete(baseURL + "/api/blobEntity/{blobEntityId}", blobEntityId);
    }

    public TimePageData<BlobEntityInfo> getBlobEntities(String type, TimePageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        params.put("type", type);
        addPageLinkToParam(params, pageLink);

        return restTemplate.exchange(
                baseURL + "/api/blobEntities?type={type}&" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TimePageData<BlobEntityInfo>>() {
                },
                params).getBody();
    }

    public List<BlobEntityInfo> getBlobEntitiesByIds(String[] blobEntityIds) {
        return restTemplate.exchange(
                baseURL + "/api/blobEntities?blobEntityIds={blobEntityIds}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<BlobEntityInfo>>() {
                },
                String.join(",", blobEntityIds)).getBody();
    }

    //ComponentDescriptor
    public Optional<ComponentDescriptor> getComponentDescriptorByClazz(String componentDescriptorClazz) {
        try {
            ResponseEntity<ComponentDescriptor> componentDescriptor = restTemplate.getForEntity(baseURL + "/api/component/{componentDescriptorClazz}", ComponentDescriptor.class, componentDescriptorClazz);
            return Optional.ofNullable(componentDescriptor.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public List<ComponentDescriptor> getComponentDescriptorsByType(String componentType) {
        return restTemplate.exchange(
                baseURL + "/api/components?componentType={componentType}",
                HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<ComponentDescriptor>>() {
                },
                componentType).getBody();
    }

    public List<ComponentDescriptor> getComponentDescriptorsByTypes(String[] componentTypes) {
        return restTemplate.exchange(
                baseURL + "/api/components?componentTypes={componentTypes}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<ComponentDescriptor>>() {
                },
                String.join(",", componentTypes)).getBody();
    }

    //Converter

    public Optional<Converter> getConverterById(String converterId) {
        try {
            ResponseEntity<Converter> converter = restTemplate.getForEntity(baseURL + "/api/converter/{converterId}", Converter.class, converterId);
            return Optional.ofNullable(converter.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Converter saveConverter(Converter converter) {
        return restTemplate.postForEntity(baseURL + "/api/converter", converter, Converter.class).getBody();
    }

    public TextPageData<Converter> getConverters(TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        addPageLinkToParam(params, pageLink);

        return restTemplate.exchange(
                baseURL + "/api/converters?" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<Converter>>() {
                },
                params).getBody();
    }

    public void deleteConverter(String converterId) {
        restTemplate.delete(baseURL + "/api/converter/{converterId}", converterId);
    }

    public Optional<JsonNode> getLatestConverterDebugInput(String converterId) {
        try {
            ResponseEntity<JsonNode> jsonNode = restTemplate.getForEntity(baseURL + "/api/converter/{converterId}/debugIn", JsonNode.class, converterId);
            return Optional.ofNullable(jsonNode.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<JsonNode> testUpLinkConverter(JsonNode inputParams) {
        try {
            ResponseEntity<JsonNode> jsonNode = restTemplate.postForEntity(baseURL + "/api/converter/testUpLink", inputParams, JsonNode.class);
            return Optional.ofNullable(jsonNode.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<JsonNode> testDownLinkConverter(JsonNode inputParams) {
        try {
            ResponseEntity<JsonNode> jsonNode = restTemplate.postForEntity(baseURL + "/api/converter/testDownLink", inputParams, JsonNode.class);
            return Optional.ofNullable(jsonNode.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public List<Converter> getConvertersByIds(String[] converterIds) {
        return restTemplate.exchange(
                baseURL + "/api/converters?converterIds={converterIds}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<Converter>>() {
                },
                String.join(",", converterIds)).getBody();
    }

    //Customer

    public Optional<Customer> getCustomerById(String customerId) {
        try {
            ResponseEntity<Customer> customer = restTemplate.getForEntity(baseURL + "/api/customer/{customerId}", Customer.class, customerId);
            return Optional.ofNullable(customer.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<JsonNode> getShortCustomerInfoById(String customerId) {
        try {
            ResponseEntity<JsonNode> customerInfo = restTemplate.getForEntity(baseURL + "/api/customer/{customerId}/shortInfo", JsonNode.class, customerId);
            return Optional.ofNullable(customerInfo.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public String getCustomerTitleById(String customerId) {
        return restTemplate.getForObject(baseURL + "/api/customer/{customerId}/title", String.class, customerId);
    }

    public Customer saveCustomer(Customer customer) {
        return restTemplate.postForEntity(baseURL + "/api/customer", customer, Customer.class).getBody();
    }

    public void deleteCustomer(String customerId) {
        restTemplate.delete(baseURL + "/api/customer/{customerId}", customerId);
    }

    public TextPageData<Customer> getCustomers(TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        addPageLinkToParam(params, pageLink);

        ResponseEntity<TextPageData<Customer>> customer = restTemplate.exchange(
                baseURL + "/api/customers?" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<Customer>>() {
                },
                params);
        return customer.getBody();
    }

    public Optional<Customer> getTenantCustomer(String customerTitle) {
        try {
            ResponseEntity<Customer> customer = restTemplate.getForEntity(baseURL + "/api/tenant/customers?customerTitle={customerTitle}", Customer.class, customerTitle);
            return Optional.ofNullable(customer.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    //CustomMenu

    public Optional<CustomMenu> getCustomMenu() {
        try {
            ResponseEntity<CustomMenu> customMenu = restTemplate.getForEntity(baseURL + "/api/customMenu/customMenu", CustomMenu.class);
            return Optional.ofNullable(customMenu.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<CustomMenu> getCurrentCustomMenu() {
        try {
            ResponseEntity<CustomMenu> customMenu = restTemplate.getForEntity(baseURL + "/api/customMenu/currentCustomMenu", CustomMenu.class);
            return Optional.ofNullable(customMenu.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public CustomMenu saveCustomMenu(CustomMenu customMenu) {
        return restTemplate.postForEntity(baseURL + "/api/customMenu/customMenu", customMenu, CustomMenu.class).getBody();
    }

    //CustomTranslation

    public Optional<CustomTranslation> getCustomTranslation() {
        try {
            ResponseEntity<CustomTranslation> customTranslation = restTemplate.getForEntity(baseURL + "/api/customTranslation/customTranslation", CustomTranslation.class);
            return Optional.ofNullable(customTranslation.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<CustomTranslation> getCurrentCustomTranslation() {
        try {
            ResponseEntity<CustomTranslation> customTranslation = restTemplate.getForEntity(baseURL + "/api/customTranslation/currentCustomTranslation", CustomTranslation.class);
            return Optional.ofNullable(customTranslation.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public CustomTranslation saveCustomTranslation(CustomTranslation customTranslation) {
        return restTemplate.postForEntity(baseURL + "/api/customTranslation/customTranslation", customTranslation, CustomTranslation.class).getBody();
    }

    //Dashboard

    public Long getServerTime() {
        return restTemplate.getForObject(baseURL + "/api/dashboard/serverTime", Long.class);
    }

    public Long getMaxDatapointsLimit() {
        return restTemplate.getForObject(baseURL + "/api/dashboard/maxDatapointsLimit", Long.class);
    }

    public Optional<DashboardInfo> getDashboardInfoById(String dashboardId) {
        try {
            ResponseEntity<DashboardInfo> dashboardInfo = restTemplate.getForEntity(baseURL + "/api/dashboard/info/{dashboardId}", DashboardInfo.class, dashboardId);
            return Optional.ofNullable(dashboardInfo.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<Dashboard> getDashboardById(String dashboardId) {
        try {
            ResponseEntity<Dashboard> dashboard = restTemplate.getForEntity(baseURL + "/api/dashboard/{dashboardId}", Dashboard.class, dashboardId);
            return Optional.ofNullable(dashboard.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Dashboard saveDashboard(Dashboard dashboard) {
        return restTemplate.postForEntity(baseURL + "/api/dashboard", dashboard, Dashboard.class).getBody();
    }

    public void deleteDashboard(String dashboardId) {
        restTemplate.delete(baseURL + "/api/dashboard/{dashboardId}", dashboardId);
    }

    public TextPageData<DashboardInfo> getTenantDashboards(String tenantId, TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        params.put("tenantId", tenantId);
        addPageLinkToParam(params, pageLink);
        return restTemplate.exchange(
                baseURL + "/api/tenant/{tenantId}/dashboards?" + getUrlParams(pageLink),
                HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<DashboardInfo>>() {
                },
                params
        ).getBody();
    }

    public TextPageData<DashboardInfo> getTenantDashboards(TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        addPageLinkToParam(params, pageLink);
        return restTemplate.exchange(
                baseURL + "/api/tenant/dashboards?" + getUrlParams(pageLink),
                HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<DashboardInfo>>() {
                },
                params
        ).getBody();
    }

    public TextPageData<DashboardInfo> getUserDashboards(TextPageLink pageLink, String operation, String userId) {
        Map<String, String> params = new HashMap<>();
        params.put("operation", operation);
        params.put("userId", userId);
        addPageLinkToParam(params, pageLink);

        return restTemplate.exchange(
                baseURL + "/api/user/dashboards?operation={operation}&userId={userId}&" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<DashboardInfo>>() {
                },
                params).getBody();
    }

    public TextPageData<DashboardInfo> getGroupDashboards(String entityGroupId, TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        params.put("entityGroupId", entityGroupId);
        addPageLinkToParam(params, pageLink);

        return restTemplate.exchange(
                baseURL + "/api/entityGroup/{entityGroupId}/dashboards?" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<DashboardInfo>>() {
                },
                params).getBody();
    }

    public List<DashboardInfo> getDashboardsByIds(String[] dashboardIds) {
        return restTemplate.exchange(
                baseURL + "/api/dashboards?dashboardIds={dashboardIds}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<DashboardInfo>>() {
                },
                String.join(",", dashboardIds)).getBody();
    }

    //Device

    public Optional<Device> getDeviceById(String deviceId) {
        try {
            ResponseEntity<Device> device = restTemplate.getForEntity(baseURL + "/api/device/{deviceId}", Device.class, deviceId);
            return Optional.ofNullable(device.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Device saveDevice(Device device) {
        return restTemplate.postForEntity(baseURL + "/api/device", device, Device.class).getBody();
    }

    public void deleteDevice(String deviceId) {
        restTemplate.delete(baseURL + "/api/device/{deviceId}", deviceId);
    }

    public Optional<DeviceCredentials> getDeviceCredentialsByDeviceId(String deviceId) {
        try {
            ResponseEntity<DeviceCredentials> deviceCredentials = restTemplate.getForEntity(baseURL + "/api/device/{deviceId}/credentials", DeviceCredentials.class, deviceId);
            return Optional.ofNullable(deviceCredentials.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public DeviceCredentials saveDeviceCredentials(DeviceCredentials deviceCredentials) {
        return restTemplate.postForEntity(baseURL + "/api/device/credentials", deviceCredentials, DeviceCredentials.class).getBody();
    }

    public TextPageData<Device> getTenantDevices(String type, TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        params.put("type", type);
        addPageLinkToParam(params, pageLink);
        return restTemplate.exchange(
                baseURL + "/api/tenant/devices?type={type}&" + getUrlParams(pageLink),
                HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<Device>>() {
                },
                params)
                .getBody();
    }

    public Optional<Device> getTenantDevice(String deviceName) {
        try {
            ResponseEntity<Device> device = restTemplate.getForEntity(baseURL + "/api/tenant/devices?deviceName={deviceName}", Device.class, deviceName);
            return Optional.ofNullable(device.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public TextPageData<Device> getCustomerDevices(String customerId, String type, TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        params.put("customerId", customerId);
        params.put("type", type);
        addPageLinkToParam(params, pageLink);
        return restTemplate.exchange(
                baseURL + "/api/customer/{customerId}/devices?type={type}&" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<Device>>() {
                },
                params).getBody();
    }

    public TextPageData<Device> getUserDevices(String type, TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        params.put("type", type);
        addPageLinkToParam(params, pageLink);
        return restTemplate.exchange(
                baseURL + "/api/user/devices?type={type}&" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<Device>>() {
                },
                params).getBody();
    }

    public List<Device> getDevicesByIds(String[] deviceIds) {
        return restTemplate.exchange(baseURL + "/api/devices?deviceIds={deviceIds}",
                HttpMethod.GET,
                HttpEntity.EMPTY, new ParameterizedTypeReference<List<Device>>() {
                },
                String.join(",", deviceIds)).getBody();
    }

    public List<Device> findByQuery(DeviceSearchQuery query) {
        return restTemplate.exchange(
                baseURL + "/api/devices",
                HttpMethod.POST,
                new HttpEntity<>(query),
                new ParameterizedTypeReference<List<Device>>() {
                }).getBody();
    }

    public List<EntitySubtype> getDeviceTypes() {
        return restTemplate.exchange(
                baseURL + "/api/devices",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<EntitySubtype>>() {
                }).getBody();
    }

    public DeferredResult<ResponseEntity> claimDevice(String deviceName, ClaimRequest claimRequest) {
        return restTemplate.exchange(
                baseURL + "/api/customer/device/{deviceName}/claim",
                HttpMethod.POST,
                new HttpEntity<>(claimRequest),
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                deviceName).getBody();
    }

    public DeferredResult<ResponseEntity> reClaimDevice(String deviceName) {
        return restTemplate.exchange(
                baseURL + "/api/customer/device/{deviceName}/claim",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                deviceName).getBody();
    }

    //EntityGroup

    public Optional<EntityGroupInfo> getEntityGroupById(String entityGroupId) {
        try {
            ResponseEntity<EntityGroupInfo> entityGroupInfo = restTemplate.getForEntity(baseURL + "/api/entityGroup/{entityGroupId}", EntityGroupInfo.class, entityGroupId);
            return Optional.ofNullable(entityGroupInfo.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public EntityGroupInfo saveEntityGroup(EntityGroup entityGroup) {
        return restTemplate.postForEntity(baseURL + "/api/entityGroup", entityGroup, EntityGroupInfo.class).getBody();
    }

    public void deleteEntityGroup(String entityGroupId) {
        restTemplate.delete(baseURL + "/api/entityGroup/{entityGroupId}", entityGroupId);
    }

    public List<EntityGroupInfo> getEntityGroupsByType(EntityType entityType) {
        return restTemplate.exchange(
                baseURL + "/api/entityGroups/{groupType}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<EntityGroupInfo>>() {
                },
                entityType.name()).getBody();
    }

    public List<EntityGroupInfo> getEntityGroupsByOwnerAndType(String ownerType, String ownerId, EntityType entityType) {
        return restTemplate.exchange(
                baseURL + "/api/entityGroups/{ownerType}/{ownerId}/{groupType}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<EntityGroupInfo>>() {
                },
                ownerType,
                ownerId,
                entityType.name()).getBody();
    }

    public Optional<EntityGroupInfo> getEntityGroupAllByOwnerAndType(String ownerType, String ownerId, EntityType entityType) {
        try {
            ResponseEntity<EntityGroupInfo> entityGroupInfo =
                    restTemplate.getForEntity(baseURL + "/api/entityGroup/all/{ownerType}/{ownerId}/{groupType}", EntityGroupInfo.class, ownerType, ownerId, entityType.name());
            return Optional.ofNullable(entityGroupInfo.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public void addEntitiesToEntityGroup(String entityGroupId, String[] entityIds) {
        restTemplate.postForEntity(baseURL + "/api/entityGroup/{entityGroupId}/addEntities", entityIds, Object.class, entityGroupId);
    }

    public void removeEntitiesFromEntityGroup(String entityGroupId, String[] entityIds) {
        restTemplate.postForEntity(baseURL + "/api/entityGroup/{entityGroupId}/deleteEntities", entityIds, Object.class, entityGroupId);
    }

    public Optional<ShortEntityView> getGroupEntity(String entityGroupId, String entityId) {
        try {
            ResponseEntity<ShortEntityView> shortEntityView =
                    restTemplate.getForEntity(baseURL + "/api/entityGroup/{entityGroupId}/{entityId}", ShortEntityView.class, entityGroupId, entityId);
            return Optional.ofNullable(shortEntityView.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public TimePageData<ShortEntityView> getEntities(String entityGroupId, TimePageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        params.put("entityGroupId", entityGroupId);
        addPageLinkToParam(params, pageLink);

        return restTemplate.exchange(
                baseURL + "/api/entityGroup/{entityGroupId}/entities?" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TimePageData<ShortEntityView>>() {
                },
                params).getBody();
    }

    public List<EntityGroupId> getEntityGroupsForEntity(String entityType, String entityId) {
        return restTemplate.exchange(
                baseURL + "/api/entityGroups/{entityType}/{entityId}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<EntityGroupId>>() {
                },
                entityType,
                entityId).getBody();
    }

    public List<EntityGroup> getEntityGroupsByIds(String[] entityGroupIds) {
        return restTemplate.exchange(
                baseURL + "/entityGroups?entityGroupIds={entityGroupIds}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<EntityGroup>>() {
                },
                String.join(",", entityGroupIds)).getBody();
    }

    public TextPageData<ContactBased<?>> getOwners(TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        addPageLinkToParam(params, pageLink);

        return restTemplate.exchange(
                baseURL + "/api/owners?" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<ContactBased<?>>>() {
                },
                params).getBody();
    }

    public void makeEntityGroupPublic(String entityGroupId) {
        restTemplate.postForEntity(baseURL + "/api/entityGroup/{entityGroupId}/makePublic", null, Object.class, entityGroupId);
    }

    public void makeEntityGroupPrivate(String entityGroupId) {
        restTemplate.postForEntity(baseURL + "/api/entityGroup/{entityGroupId}/makePrivate", null, Object.class, entityGroupId);
    }

    //EntityRelation

    public void saveRelation(EntityRelation relation) {
        restTemplate.postForEntity(baseURL + "/api/relation", relation, Object.class);
    }

    public void deleteRelation(String fromId, String fromType, String relationType, String relationTypeGroup, String toId, String toType) {
        Map<String, String> params = new HashMap<>();
        params.put("fromId", fromId);
        params.put("fromType", fromType);
        params.put("relationType", relationType);
        params.put("relationTypeGroup", relationTypeGroup);
        params.put("toId", toId);
        params.put("toType", toType);
        restTemplate.delete(baseURL + "/api/relation?fromId={fromId}&fromType={fromType}&relationType={relationType}&relationTypeGroup={relationTypeGroup}&toId={toId}&toType={toType}", params);
    }

    public void deleteRelations(String entityId, String entityType) {
        restTemplate.delete(baseURL + "/api/relations?entityId={entityId}&entityType={entityType}", entityId, entityType);
    }

    public Optional<EntityRelation> getRelation(String fromId, String fromType, String relationType, String relationTypeGroup, String toId, String toType) {
        Map<String, String> params = new HashMap<>();
        params.put("fromId", fromId);
        params.put("fromType", fromType);
        params.put("relationType", relationType);
        params.put("relationTypeGroup", relationTypeGroup);
        params.put("toId", toId);
        params.put("toType", toType);

        try {
            ResponseEntity<EntityRelation> entityRelation = restTemplate.getForEntity(
                    baseURL + "/api/relation?fromId={fromId}&fromType={fromType}&relationType={relationType}&relationTypeGroup={relationTypeGroup}&toId={toId}&toType={toType}",
                    EntityRelation.class,
                    params);
            return Optional.ofNullable(entityRelation.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public List<EntityRelation> findByFrom(String fromId, String fromType, String relationTypeGroup) {
        Map<String, String> params = new HashMap<>();
        params.put("fromId", fromId);
        params.put("fromType", fromType);
        params.put("relationTypeGroup", relationTypeGroup);

        return restTemplate.exchange(
                baseURL + "/api/relations?fromId={fromId}&fromType={fromType}&relationTypeGroup={relationTypeGroup}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<EntityRelation>>() {
                },
                params).getBody();
    }

    public List<EntityRelationInfo> findInfoByFrom(String fromId, String fromType, String relationTypeGroup) {
        Map<String, String> params = new HashMap<>();
        params.put("fromId", fromId);
        params.put("fromType", fromType);
        params.put("relationTypeGroup", relationTypeGroup);

        return restTemplate.exchange(
                baseURL + "/api/relations/info?fromId={fromId}&fromType={fromType}&relationTypeGroup={relationTypeGroup}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<EntityRelationInfo>>() {
                },
                params).getBody();
    }

    public List<EntityRelation> findByFrom(String fromId, String fromType, String relationType, String relationTypeGroup) {
        Map<String, String> params = new HashMap<>();
        params.put("fromId", fromId);
        params.put("fromType", fromType);
        params.put("relationType", relationType);
        params.put("relationTypeGroup", relationTypeGroup);

        return restTemplate.exchange(
                baseURL + "/api/relations?fromId={fromId}&fromType={fromType}&relationType={relationType}&relationTypeGroup={relationTypeGroup}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<EntityRelation>>() {
                },
                params).getBody();
    }

    public List<EntityRelation> findByTo(String toId, String toType, String relationTypeGroup) {
        Map<String, String> params = new HashMap<>();
        params.put("toId", toId);
        params.put("toType", toType);
        params.put("relationTypeGroup", relationTypeGroup);

        return restTemplate.exchange(
                baseURL + "/api/relations?toId={toId}&toType={toType}&relationTypeGroup={relationTypeGroup}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<EntityRelation>>() {
                },
                params).getBody();
    }

    public List<EntityRelationInfo> findInfoByTo(String toId, String toType, String relationTypeGroup) {
        Map<String, String> params = new HashMap<>();
        params.put("toId", toId);
        params.put("toType", toType);
        params.put("relationTypeGroup", relationTypeGroup);

        return restTemplate.exchange(
                baseURL + "/api/relations?toId={toId}&toType={toType}&relationTypeGroup={relationTypeGroup}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<EntityRelationInfo>>() {
                },
                params).getBody();
    }

    public List<EntityRelation> findByTo(String toId, String toType, String relationType, String relationTypeGroup) {
        Map<String, String> params = new HashMap<>();
        params.put("toId", toId);
        params.put("toType", toType);
        params.put("relationType", relationType);
        params.put("relationTypeGroup", relationTypeGroup);

        return restTemplate.exchange(
                baseURL + "/api/relations?toId={toId}&toType={toType}&relationType={relationType}&relationTypeGroup={relationTypeGroup}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<EntityRelation>>() {
                },
                params).getBody();
    }

    public List<EntityRelation> findByQuery(EntityRelationsQuery query) {
        return restTemplate.exchange(
                baseURL + "/api/relations",
                HttpMethod.POST,
                new HttpEntity<>(query),
                new ParameterizedTypeReference<List<EntityRelation>>() {
                }).getBody();
    }

    public List<EntityRelationInfo> findInfoByQuery(EntityRelationsQuery query) {
        return restTemplate.exchange(
                baseURL + "/api/relations",
                HttpMethod.POST,
                new HttpEntity<>(query),
                new ParameterizedTypeReference<List<EntityRelationInfo>>() {
                }).getBody();
    }

    //EntityView

    public Optional<EntityView> getEntityViewById(String entityViewId) {
        try {
            ResponseEntity<EntityView> entityView = restTemplate.getForEntity(baseURL + "/api/entityView/{entityViewId}", EntityView.class, entityViewId);
            return Optional.ofNullable(entityView.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public EntityView saveEntityView(EntityView entityView) {
        return restTemplate.postForEntity(baseURL + "/api/entityView", entityView, EntityView.class).getBody();
    }

    public void deleteEntityView(String entityViewId) {
        restTemplate.delete(baseURL + "/api/entityView/{entityViewId}", entityViewId);
    }

    public Optional<EntityView> getTenantEntityView(String entityViewName) {
        try {
            ResponseEntity<EntityView> entityView = restTemplate.getForEntity(baseURL + "/api/tenant/entityViews?entityViewName={entityViewName}", EntityView.class, entityViewName);
            return Optional.ofNullable(entityView.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public TextPageData<EntityView> getCustomerEntityViews(String customerId, String type, TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        params.put("customerId", customerId);
        params.put("type", type);
        addPageLinkToParam(params, pageLink);
        return restTemplate.exchange(
                baseURL + "/api/customer/{customerId}/entityViews?type={type}&" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<EntityView>>() {
                },
                params).getBody();
    }

    public TextPageData<EntityView> getTenantEntityViews(String type, TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        params.put("type", type);
        addPageLinkToParam(params, pageLink);

        return restTemplate.exchange(
                baseURL + "/api/tenant/entityViews?type={type}&" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<EntityView>>() {
                },
                params).getBody();
    }

    public TextPageData<EntityView> getUserEntityViews(String type, TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        params.put("type", type);
        addPageLinkToParam(params, pageLink);

        return restTemplate.exchange(
                baseURL + "/api/user/entityViews?type={type}&" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<EntityView>>() {
                },
                params).getBody();
    }

    public List<EntityView> getEntityViewsByIds(String[] entityViewIds) {
        return restTemplate.exchange(
                baseURL + "/api/entityViews?entityViewIds={entityViewIds}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<EntityView>>() {
                },
                String.join(",", entityViewIds)).getBody();
    }

    public List<EntityView> findByQuery(EntityViewSearchQuery query) {
        return restTemplate.exchange(baseURL + "/api/entityViews", HttpMethod.POST, new HttpEntity<>(query), new ParameterizedTypeReference<List<EntityView>>() {
        }).getBody();
    }

    public List<EntitySubtype> getEntityViewTypes() {
        return restTemplate.exchange(baseURL + "/api/entityView/types", HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<List<EntitySubtype>>() {
        }).getBody();
    }

    //Event

    public TimePageData<Event> getEvents(String entityType, String entityId, String eventType, String tenantId, TimePageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        params.put("entityType", entityType);
        params.put("entityId", entityId);
        params.put("eventType", eventType);
        params.put("tenantId", tenantId);
        addPageLinkToParam(params, pageLink);

        return restTemplate.exchange(
                baseURL + "/api/events/{entityType}/{entityId}/{eventType}?tenantId={tenantId}&" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TimePageData<Event>>() {
                },
                params).getBody();
    }

    public TimePageData<Event> getEvents(String entityType, String entityId, String tenantId, TimePageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        params.put("entityType", entityType);
        params.put("entityId", entityId);
        params.put("tenantId", tenantId);
        addPageLinkToParam(params, pageLink);

        return restTemplate.exchange(
                baseURL + "/api/events/{entityType}/{entityId}?tenantId={tenantId}&" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TimePageData<Event>>() {
                },
                params).getBody();
    }

    //GroupPermission

    public Optional<GroupPermission> getGroupPermissionById(String groupPermissionId) {
        try {
            ResponseEntity<GroupPermission> groupPermission = restTemplate.getForEntity(baseURL + "/api/groupPermission/{groupPermissionId}", GroupPermission.class, groupPermissionId);
            return Optional.ofNullable(groupPermission.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public GroupPermission saveGroupPermission(GroupPermission groupPermission) {
        return restTemplate.postForEntity(baseURL + "/api/groupPermission", groupPermission, GroupPermission.class).getBody();
    }

    public void deleteGroupPermission(String groupPermissionId) {
        restTemplate.delete(baseURL + "/api/groupPermission/{groupPermissionId}", groupPermissionId);
    }

    public List<GroupPermissionInfo> getUserGroupPermissions(String userGroupId) {
        return restTemplate.exchange(
                baseURL + "/api/userGroup/{userGroupId}/groupPermissions",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<GroupPermissionInfo>>() {
                },
                userGroupId).getBody();
    }

    public List<GroupPermissionInfo> getEntityGroupPermissions(String entityGroupId) {
        return restTemplate.exchange(
                baseURL + "/api/entityGroup/{entityGroupId}/groupPermissions",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<GroupPermissionInfo>>() {
                },
                entityGroupId).getBody();
    }

    //Integration

    public Optional<Integration> getIntegrationById(String integrationId) {
        try {
            ResponseEntity<Integration> integration = restTemplate.getForEntity(baseURL + "/api/integration/{integrationId}", Integration.class, integrationId);
            return Optional.ofNullable(integration.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<Integration> getIntegrationByRoutingKey(String routingKey) {
        try {
            ResponseEntity<Integration> integration = restTemplate.getForEntity(baseURL + "/api/integration/routingKey/{routingKey}", Integration.class, routingKey);
            return Optional.ofNullable(integration.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Integration saveIntegration(Integration integration) {
        return restTemplate.postForEntity(baseURL + "/api/integration", integration, Integration.class).getBody();
    }


    public TextPageData<Integration> getIntegrations(TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        addPageLinkToParam(params, pageLink);
        return restTemplate.exchange(
                baseURL + "/api/integrations?" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<Integration>>() {
                }).getBody();
    }

    public void deleteIntegration(String integrationId) {
        restTemplate.delete(baseURL + "/api/integration/{integrationId}", integrationId);
    }

    public List<Integration> getIntegrationsByIds(String[] integrationIds) {
        return restTemplate.exchange(
                baseURL + "/api/integrations?integrationIds={integrationIds}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<Integration>>() {
                },
                String.join(",", integrationIds)).getBody();
    }

    //Owner

    public void changeOwnerToTenant(String ownerId, String entityType, String entityId) {
        restTemplate.postForEntity(baseURL + "/api/owner/TENANT/{ownerId}/{entityType}/{entityId}", null, Object.class, ownerId, entityType, entityId);
    }

    public void changeOwnerToCustomer(String ownerId, String entityType, String entityId) {
        restTemplate.postForEntity(baseURL + "/api/owner/CUSTOMER/{ownerId}/{entityType}/{entityId}", null, Object.class, ownerId, entityType, entityId);
    }

    //Report

    public DeferredResult<ResponseEntity> downloadDashboardReport(String dashboardId, JsonNode reportParams) {
        return restTemplate.exchange(
                baseURL + "/api/report/{dashboardId}/download",
                HttpMethod.POST,
                new HttpEntity<>(reportParams),
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                dashboardId).getBody();
    }

    public DeferredResult<ResponseEntity> downloadTestReport(ReportConfig reportConfig, String reportsServerEndpointUrl) {
        return restTemplate.exchange(
                baseURL + "/api/report/test?reportsServerEndpointUrl={reportsServerEndpointUrl}",
                HttpMethod.POST,
                new HttpEntity<>(reportConfig),
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                reportsServerEndpointUrl).getBody();
    }

    //Role

    public Optional<Role> getRoleById(String roleId) {
        try {
            ResponseEntity<Role> role = restTemplate.getForEntity(baseURL + "/api/role/{roleId}", Role.class, roleId);
            return Optional.ofNullable(role.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Role saveRole(Role role) {
        return restTemplate.postForEntity(baseURL + "/api/role", role, Role.class).getBody();
    }

    public void deleteRole(String roleId) {
        restTemplate.delete(baseURL + "/api/role/{roleId}", roleId);
    }

    public TextPageData<Role> getRoles(RoleType type, TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        params.put("type", type.name());
        addPageLinkToParam(params, pageLink);
        return restTemplate.exchange(
                baseURL + "/api/roles?type={type}&" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<Role>>() {
                },
                params).getBody();
    }

    public List<Role> getRolesByIds(String[] roleIds) {
        return restTemplate.exchange(
                baseURL + "/api/roles?roleIds={roleIds}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<Role>>() {
                },
                String.join(",", roleIds)).getBody();
    }

    //Rpc

    public DeferredResult<ResponseEntity> handleOneWayDeviceRPCRequest(String deviceId, String requestBody) {
        return restTemplate.exchange(
                baseURL + "/oneway/{deviceId}",
                HttpMethod.POST,
                new HttpEntity<>(requestBody),
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                deviceId).getBody();
    }

    public DeferredResult<ResponseEntity> handleTwoWayDeviceRPCRequest(String deviceId, String requestBody) {
        return restTemplate.exchange(
                baseURL + "/twoway/{deviceId}",
                HttpMethod.POST,
                new HttpEntity<>(requestBody),
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                deviceId).getBody();
    }

    //RuleChain

    public Optional<RuleChain> getRuleChainById(String ruleChainId) {
        try {
            ResponseEntity<RuleChain> ruleChain = restTemplate.getForEntity(baseURL + "/api/ruleChain/{ruleChainId}", RuleChain.class, ruleChainId);
            return Optional.ofNullable(ruleChain.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<RuleChainMetaData> getRuleChainMetaData(String ruleChainId) {
        try {
            ResponseEntity<RuleChainMetaData> ruleChainMetaData = restTemplate.getForEntity(baseURL + "/api/ruleChain/{ruleChainId}/metadata", RuleChainMetaData.class, ruleChainId);
            return Optional.ofNullable(ruleChainMetaData.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public RuleChain saveRuleChain(RuleChain ruleChain) {
        return restTemplate.postForEntity(baseURL + "/api/ruleChain", ruleChain, RuleChain.class).getBody();
    }

    public Optional<RuleChain> setRootRuleChain(String ruleChainId) {
        try {
            ResponseEntity<RuleChain> ruleChain = restTemplate.postForEntity(baseURL + "/api/ruleChain/{ruleChainId}/root", null, RuleChain.class, ruleChainId);
            return Optional.ofNullable(ruleChain.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public RuleChainMetaData saveRuleChainMetaData(RuleChainMetaData ruleChainMetaData) {
        return restTemplate.postForEntity(baseURL + "/api/ruleChain/metadata", ruleChainMetaData, RuleChainMetaData.class).getBody();
    }

    public TextPageData<RuleChain> getRuleChains(TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        addPageLinkToParam(params, pageLink);
        return restTemplate.exchange(
                baseURL + "/api/ruleChains" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<RuleChain>>() {
                }
        ).getBody();
    }

    public void deleteRuleChain(String ruleChainId) {
        restTemplate.delete(baseURL + "/api/ruleChain/{ruleChainId}", ruleChainId);
    }

    public Optional<JsonNode> getLatestRuleNodeDebugInput(String ruleNodeId) {
        try {
            ResponseEntity<JsonNode> jsonNode = restTemplate.getForEntity(baseURL + "/api/ruleNode/{ruleNodeId}/debugIn", JsonNode.class, ruleNodeId);
            return Optional.ofNullable(jsonNode.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<JsonNode> testScript(JsonNode inputParams) {
        try {
            ResponseEntity<JsonNode> jsonNode = restTemplate.postForEntity(baseURL + "/api/ruleChain/testScript", inputParams, JsonNode.class);
            return Optional.ofNullable(jsonNode.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    //RuleEngine

    public DeferredResult<ResponseEntity> handleRuleEngineRequest(String requestBody) {
        return restTemplate.exchange(
                baseURL + "/",
                HttpMethod.POST,
                new HttpEntity<>(requestBody),
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                }).getBody();
    }

    public DeferredResult<ResponseEntity> handleRuleEngineRequest(String entityType, String entityId, String requestBody) {
        return restTemplate.exchange(
                baseURL + "/{entityType}/{entityId}",
                HttpMethod.POST,
                new HttpEntity<>(requestBody),
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                entityType,
                entityId).getBody();
    }

    public DeferredResult<ResponseEntity> handleRuleEngineRequest(String entityType, String entityId, int timeout, String requestBody) {
        return restTemplate.exchange(
                baseURL + "/{entityType}/{entityId}/{timeout}",
                HttpMethod.POST,
                new HttpEntity<>(requestBody),
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                entityType,
                entityId,
                timeout).getBody();
    }

    //SchedulerEvent

    public Optional<SchedulerEventInfo> getSchedulerEventInfoById(String schedulerEventId) {
        try {
            ResponseEntity<SchedulerEventInfo> schedulerEventInfo = restTemplate.getForEntity(baseURL + "/api/schedulerEvent/info/{schedulerEventId}", SchedulerEventInfo.class, schedulerEventId);
            return Optional.ofNullable(schedulerEventInfo.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<SchedulerEvent> getSchedulerEventById(String schedulerEventId) {
        try {
            ResponseEntity<SchedulerEvent> schedulerEvent = restTemplate.getForEntity(baseURL + "/api/schedulerEvent/{schedulerEventId}", SchedulerEvent.class, schedulerEventId);
            return Optional.ofNullable(schedulerEvent.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public SchedulerEvent saveSchedulerEvent(SchedulerEvent schedulerEvent) {
        return restTemplate.postForEntity(baseURL + "/api/schedulerEvent", schedulerEvent, SchedulerEvent.class).getBody();
    }

    public void deleteSchedulerEvent(String schedulerEventId) {
        restTemplate.delete(baseURL + "/api/schedulerEvent/{schedulerEventId}", schedulerEventId);
    }

    public List<SchedulerEventInfo> getSchedulerEvents(String type) {
        return restTemplate.exchange(
                baseURL + "/api/schedulerEvents&type={type}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<SchedulerEventInfo>>() {
                },
                type).getBody();
    }

    public List<SchedulerEventInfo> getSchedulerEventsByIds(String[] schedulerEventIds) {
        return restTemplate.exchange(
                baseURL + "/api/schedulerEvents?schedulerEventIds={schedulerEventIds}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<SchedulerEventInfo>>() {
                },
                String.join(",", schedulerEventIds)).getBody();
    }

    //SelfRegistration

    public SelfRegistrationParams saveSelfRegistrationParams(SelfRegistrationParams selfRegistrationParams) {
        return restTemplate.postForEntity(baseURL + "/api/selfRegistration/selfRegistrationParams", selfRegistrationParams, SelfRegistrationParams.class).getBody();
    }

    public Optional<SelfRegistrationParams> getSelfRegistrationParams() {
        try {
            ResponseEntity<SelfRegistrationParams> selfRegistrationParams = restTemplate.getForEntity(baseURL + "/api/selfRegistration/selfRegistrationParams}", SelfRegistrationParams.class);
            return Optional.ofNullable(selfRegistrationParams.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<SignUpSelfRegistrationParams> getSignUpSelfRegistrationParams() {
        try {
            ResponseEntity<SignUpSelfRegistrationParams> selfRegistrationParams = restTemplate.getForEntity(baseURL + "/api/noauth/selfRegistration/signUpSelfRegistrationParams", SignUpSelfRegistrationParams.class);
            return Optional.ofNullable(selfRegistrationParams.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public String getPrivacyPolicy() {
        return restTemplate.getForEntity(baseURL + "/api/noauth/selfRegistration/privacyPolicy", String.class).getBody();
    }

    //SignUp

    public SignUpResult signUp(SignUpRequest signUpRequest) {
        return restTemplate.postForEntity(baseURL + "/api/noauth/signup", signUpRequest, SignUpResult.class).getBody();
    }


    public void resendEmailActivation(String email) {
        restTemplate.postForEntity(baseURL + "/api/noauth/resendEmailActivation?email={email}", null, Object.class, email);
    }

    public ResponseEntity<String> activateEmail(String emailCode) {
        return restTemplate.exchange(
                baseURL + "/api/noauth/activateEmail?emailCode={emailCode}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<ResponseEntity<String>>() {
                },
                emailCode).getBody();
    }

    public Optional<JsonNode> activateUserByEmailCode(String emailCode) {
        try {
            ResponseEntity<JsonNode> jsonNode = restTemplate.postForEntity(baseURL + "/api/noauth/activateByEmailCode?emailCode={emailCode}", null, JsonNode.class, emailCode);
            return Optional.ofNullable(jsonNode.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Boolean privacyPolicyAccepted() {
        return restTemplate.getForEntity(baseURL + "/api/signup/privacyPolicyAccepted", Boolean.class).getBody();
    }

    public Optional<JsonNode> acceptPrivacyPolicy() {
        try {
            ResponseEntity<JsonNode> jsonNode = restTemplate.postForEntity(baseURL + "/api/signup/acceptPrivacyPolicy", null, JsonNode.class);
            return Optional.ofNullable(jsonNode.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    //Telemetry

    public DeferredResult<ResponseEntity> getAttributeKeys(String entityType, String entityId) {
        return restTemplate.exchange(
                baseURL + "/{entityType}/{entityId}/keys/attributes",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                entityType,
                entityId).getBody();
    }

    public DeferredResult<ResponseEntity> getAttributeKeysByScope(String entityType, String entityId, String scope) {
        return restTemplate.exchange(
                baseURL + "/{entityType}/{entityId}/keys/attributes/{scope}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                entityType,
                entityId,
                scope).getBody();
    }

    public DeferredResult<ResponseEntity> getAttributesResponseEntity(String entityType, String entityId, String keys) {
        return restTemplate.exchange(
                baseURL + "/{entityType}/{entityId}/values/attributes?keys={keys}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                entityType,
                entityId,
                keys).getBody();
    }

    public DeferredResult<ResponseEntity> getAttributesByScope(String entityType, String entityId, String scope, String keys) {
        return restTemplate.exchange(
                baseURL + "/{entityType}/{entityId}/values/attributes/{scope}?keys={keys}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                entityType,
                entityId,
                scope,
                keys).getBody();
    }

    public DeferredResult<ResponseEntity> getTimeseriesKeys(String entityType, String entityId) {
        return restTemplate.exchange(
                baseURL + "/{entityType}/{entityId}/keys/timeseries",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                entityType,
                entityId).getBody();
    }

    public DeferredResult<ResponseEntity> getLatestTimeseries(String entityType, String entityId, String keys) {
        return restTemplate.exchange(
                baseURL + "/{entityType}/{entityId}/values/timeseries?keys={keys}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                entityType,
                entityId,
                keys).getBody();
    }


    public DeferredResult<ResponseEntity> getTimeseries(String entityType, String entityId, String keys, Long startTs, Long endTs, Long interval, Integer limit, String agg) {
        Map<String, String> params = new HashMap<>();
        params.put("entityType", entityType);
        params.put("entityId", entityId);
        params.put("keys", keys);
        params.put("startTs", startTs.toString());
        params.put("endTs", endTs.toString());
        params.put("interval", interval == null ? "0" : interval.toString());
        params.put("limit", limit == null ? "100" : limit.toString());
        params.put("agg", agg == null ? "NONE" : agg);

        return restTemplate.exchange(
                baseURL + "/{entityType}/{entityId}/values/timeseries?keys={keys}&startTs={startTs}&endTs={endTs}&interval={interval}&limit={limit}&agg={agg}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                params).getBody();
    }

    public DeferredResult<ResponseEntity> saveDeviceAttributes(String deviceId, String scope, JsonNode request) {
        return restTemplate.exchange(
                baseURL + "/{deviceId}/{scope}",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                deviceId,
                scope).getBody();
    }

    public DeferredResult<ResponseEntity> saveEntityAttributesV1(String entityType, String entityId, String scope, JsonNode request) {
        return restTemplate.exchange(
                baseURL + "/{entityType}/{entityId}/{scope}",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                entityType,
                entityId,
                scope).getBody();
    }

    public DeferredResult<ResponseEntity> saveEntityAttributesV2(String entityType, String entityId, String scope, JsonNode request) {
        return restTemplate.exchange(
                baseURL + "/{entityType}/{entityId}/attributes/{scope}",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                entityType,
                entityId,
                scope).getBody();
    }

    public DeferredResult<ResponseEntity> saveEntityTelemetry(String entityType, String entityId, String scope, String requestBody) {
        return restTemplate.exchange(
                baseURL + "/{entityType}/{entityId}/timeseries/{scope}",
                HttpMethod.POST,
                new HttpEntity<>(requestBody),
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                entityType,
                entityId,
                scope).getBody();
    }

    public DeferredResult<ResponseEntity> saveEntityTelemetryWithTTL(String entityType, String entityId, String scope, Long ttl, String requestBody) {
        return restTemplate.exchange(
                baseURL + "/{entityType}/{entityId}/timeseries/{scope}/{ttl}",
                HttpMethod.POST,
                new HttpEntity<>(requestBody),
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                entityType,
                entityId,
                scope,
                ttl).getBody();
    }

    public DeferredResult<ResponseEntity> deleteEntityTimeseries(String entityType,
                                                                 String entityId,
                                                                 String keys,
                                                                 boolean deleteAllDataForKeys,
                                                                 Long startTs,
                                                                 Long endTs,
                                                                 boolean rewriteLatestIfDeleted) {
        Map<String, String> params = new HashMap<>();
        params.put("entityType", entityType);
        params.put("entityId", entityId);
        params.put("keys", keys);
        params.put("deleteAllDataForKeys", String.valueOf(deleteAllDataForKeys));
        params.put("startTs", startTs.toString());
        params.put("endTs", endTs.toString());
        params.put("rewriteLatestIfDeleted", String.valueOf(rewriteLatestIfDeleted));

        return restTemplate.exchange(
                baseURL + "/{entityType}/{entityId}/timeseries/delete?keys={keys}&deleteAllDataForKeys={deleteAllDataForKeys}&startTs={startTs}&endTs={endTs}&rewriteLatestIfDeleted={rewriteLatestIfDeleted}",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                params).getBody();
    }

    public DeferredResult<ResponseEntity> deleteEntityAttributes(String deviceId, String scope, String keys) {
        return restTemplate.exchange(
                baseURL + "/{deviceId}/{scope}?keys={keys}",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                deviceId,
                scope,
                keys).getBody();
    }

    public DeferredResult<ResponseEntity> deleteEntityAttributes(String entityType, String entityId, String scope, String keys) {
        return restTemplate.exchange(
                baseURL + "/{entityType}/{entityId}/{scope}?keys={keys}",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<DeferredResult<ResponseEntity>>() {
                },
                entityType,
                entityId,
                scope,
                keys).getBody();
    }

    //Tenant

    public Optional<Tenant> getTenantById(String tenantId) {
        try {
            ResponseEntity<Tenant> tenant = restTemplate.getForEntity(baseURL + "/api/tenant/{tenantId}", Tenant.class, tenantId);
            return Optional.ofNullable(tenant.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Tenant saveTenant(Tenant tenant) {
        return restTemplate.postForEntity(baseURL + "/api/tenant", tenant, Tenant.class).getBody();
    }

    public void deleteTenant(String tenantId) {
        restTemplate.delete(baseURL + "/api/tenant/{tenantId}", tenantId);
    }

    public TextPageData<Tenant> getTenants(TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        addPageLinkToParam(params, pageLink);
        return restTemplate.exchange(
                baseURL + "/api/tenants?" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<Tenant>>() {
                },
                params).getBody();
    }

    public List<Tenant> getTenantsByIds(String[] tenantIds) {
        return restTemplate.exchange(
                baseURL + "/api/tenants?tenantIds={tenantIds}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<Tenant>>() {
                },
                String.join(",", tenantIds)).getBody();
    }

    //User

    public Optional<User> getUserById(String userId) {
        try {
            ResponseEntity<User> user = restTemplate.getForEntity(baseURL + "/api/user/{userId}", User.class, userId);
            return Optional.ofNullable(user.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Boolean isUserTokenAccessEnabled() {
        return restTemplate.getForEntity(baseURL + "/api/user/tokenAccessEnabled", Boolean.class).getBody();
    }

    public Optional<JsonNode> getUserToken(String userId) {
        try {
            ResponseEntity<JsonNode> userToken = restTemplate.getForEntity(baseURL + "/api/user/{userId}/token", JsonNode.class, userId);
            return Optional.ofNullable(userToken.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public User saveUser(User user, boolean sendActivationMail) {
        return restTemplate.postForEntity(baseURL + "/api/user?sendActivationMail={sendActivationMail}", user, User.class, sendActivationMail).getBody();
    }

    public void sendActivationEmail(String email) {
        restTemplate.postForEntity(baseURL + "/api/user/sendActivationMail?email={email}", null, Object.class, email);
    }

    public String getActivationLink(String userId) {
        return restTemplate.getForEntity(baseURL + "/api/user/{userId}/activationLink", String.class, userId).getBody();
    }

    public void deleteUser(String userId) {
        restTemplate.delete(baseURL + "/api/user/{userId}", userId);
    }

    public TextPageData<User> getTenantAdmins(String tenantId, TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        params.put("tenantId", tenantId);
        addPageLinkToParam(params, pageLink);

        return restTemplate.exchange(
                baseURL + "/api/tenant/{tenantId}/users?" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<User>>() {
                },
                params).getBody();
    }

    public TextPageData<User> getCustomerUsers(String customerId, TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        params.put("customerId", customerId);
        addPageLinkToParam(params, pageLink);

        return restTemplate.exchange(
                baseURL + "/api/customer/{customerId}/users?" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<User>>() {
                },
                params).getBody();
    }

    public TextPageData<User> getAllCustomerUsers(TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        addPageLinkToParam(params, pageLink);

        return restTemplate.exchange(
                baseURL + "/api/customer/users?" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<User>>() {
                },
                params).getBody();
    }

    public TextPageData<User> getUserUsers(TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        addPageLinkToParam(params, pageLink);

        return restTemplate.exchange(
                baseURL + "/api/user/users?" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<User>>() {
                },
                params).getBody();
    }

    public List<User> getUsersByIds(String[] userIds) {
        return restTemplate.exchange(
                baseURL + "/api/users?userIds={userIds}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<User>>() {
                },
                String.join(",", userIds)).getBody();
    }

    public void setUserCredentialsEnabled(String userId, boolean userCredentialsEnabled) {
        restTemplate.postForEntity(
                baseURL + "/api/user/{userId}/userCredentialsEnabled?serCredentialsEnabled={serCredentialsEnabled}",
                null,
                Object.class,
                userId,
                userCredentialsEnabled);
    }

    //UserPermissions

    public Optional<AllowedPermissionsInfo> getAllowedPermissions() {
        try {
            ResponseEntity<AllowedPermissionsInfo> allowedPermissionsInfo = restTemplate.getForEntity(baseURL + "/api/permissions/allowedPermissions", AllowedPermissionsInfo.class);
            return Optional.ofNullable(allowedPermissionsInfo.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    //WhiteLabeling

    public Optional<WhiteLabelingParams> getWhiteLabelParams(String logoImageChecksum, String faviconChecksum) {
        try {
            ResponseEntity<WhiteLabelingParams> whiteLabelingParams =
                    restTemplate.getForEntity(
                            baseURL + "/api/whiteLabel/whiteLabelParams?logoImageChecksum={logoImageChecksum}&faviconChecksum={faviconChecksum}",
                            WhiteLabelingParams.class,
                            logoImageChecksum, faviconChecksum);
            return Optional.ofNullable(whiteLabelingParams.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<LoginWhiteLabelingParams> getLoginWhiteLabelParams(String logoImageChecksum, String faviconChecksum) {
        try {
            ResponseEntity<LoginWhiteLabelingParams> loginWhiteLabelingParams =
                    restTemplate.getForEntity(
                            baseURL + "/api/noauth/whiteLabel/loginWhiteLabelParams?logoImageChecksum={logoImageChecksum}&faviconChecksum={faviconChecksum}",
                            LoginWhiteLabelingParams.class,
                            logoImageChecksum,
                            faviconChecksum);
            return Optional.ofNullable(loginWhiteLabelingParams.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<WhiteLabelingParams> getCurrentWhiteLabelParams() {
        try {
            ResponseEntity<WhiteLabelingParams> whiteLabelingParams =
                    restTemplate.getForEntity(baseURL + "/api/whiteLabel/currentWhiteLabelParams", WhiteLabelingParams.class);
            return Optional.ofNullable(whiteLabelingParams.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<LoginWhiteLabelingParams> getCurrentLoginWhiteLabelParams() {
        try {
            ResponseEntity<LoginWhiteLabelingParams> loginWhiteLabelingParams =
                    restTemplate.getForEntity(baseURL + "/api/whiteLabel/currentLoginWhiteLabelParams", LoginWhiteLabelingParams.class);
            return Optional.ofNullable(loginWhiteLabelingParams.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public WhiteLabelingParams saveWhiteLabelParams(WhiteLabelingParams whiteLabelingParams) {
        return restTemplate.postForEntity(baseURL + "/api/whiteLabel/whiteLabelParams", whiteLabelingParams, WhiteLabelingParams.class).getBody();
    }

    public LoginWhiteLabelingParams saveLoginWhiteLabelParams(LoginWhiteLabelingParams loginWhiteLabelingParams) {
        return restTemplate.postForEntity(baseURL + "/api/whiteLabel/loginWhiteLabelParams", loginWhiteLabelingParams, LoginWhiteLabelingParams.class).getBody();
    }

    public WhiteLabelingParams previewWhiteLabelParams(WhiteLabelingParams whiteLabelingParams) {
        return restTemplate.postForEntity(baseURL + "/api/whiteLabel/previewWhiteLabelParams", whiteLabelingParams, WhiteLabelingParams.class).getBody();
    }

    public Boolean isWhiteLabelingAllowed() {
        return restTemplate.getForEntity(baseURL + "/api/whiteLabel/isWhiteLabelingAllowed", Boolean.class).getBody();
    }

    public Boolean isCustomerWhiteLabelingAllowed() {
        return restTemplate.getForEntity(baseURL + "/api/whiteLabel/isCustomerWhiteLabelingAllowed", Boolean.class).getBody();
    }

    //WidgetsBundle

    public Optional<WidgetsBundle> getWidgetsBundleById(String widgetsBundleId) {
        try {
            ResponseEntity<WidgetsBundle> widgetsBundle =
                    restTemplate.getForEntity(baseURL + "/api/widgetsBundle/{widgetsBundleId}", WidgetsBundle.class, widgetsBundleId);
            return Optional.ofNullable(widgetsBundle.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public WidgetsBundle saveWidgetsBundle(WidgetsBundle widgetsBundle) {
        return restTemplate.postForEntity(baseURL + "/api/widgetsBundle", widgetsBundle, WidgetsBundle.class).getBody();
    }

    public void deleteWidgetsBundle(String widgetsBundleId) {
        restTemplate.delete(baseURL + "/api/widgetsBundle/{widgetsBundleId}", widgetsBundleId);
    }

    public TextPageData<WidgetsBundle> getWidgetsBundles(TextPageLink pageLink) {
        Map<String, String> params = new HashMap<>();
        addPageLinkToParam(params, pageLink);
        return restTemplate.exchange(
                baseURL + "/api/widgetsBundles?" + getUrlParams(pageLink),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<TextPageData<WidgetsBundle>>() {
                }).getBody();
    }

    public List<WidgetsBundle> getWidgetsBundles() {
        return restTemplate.exchange(
                baseURL + "/api/widgetsBundles",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<WidgetsBundle>>() {
                }).getBody();
    }

    //WidgetType

    public Optional<WidgetType> getWidgetTypeById(String widgetTypeId) {
        try {
            ResponseEntity<WidgetType> widgetType =
                    restTemplate.getForEntity(baseURL + "/api/widgetType/{widgetTypeId}", WidgetType.class, widgetTypeId);
            return Optional.ofNullable(widgetType.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public WidgetType saveWidgetType(WidgetType widgetType) {
        return restTemplate.postForEntity(baseURL + "/api/widgetType", widgetType, WidgetType.class).getBody();
    }

    public void deleteWidgetType(String widgetTypeId) {
        restTemplate.delete(baseURL + "/api/widgetType/{widgetTypeId}", widgetTypeId);
    }

    public List<WidgetType> getBundleWidgetTypes(boolean isSystem, String bundleAlias) {
        return restTemplate.exchange(
                baseURL + "/api/widgetTypes?isSystem={isSystem}&bundleAlias={bundleAlias}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<WidgetType>>() {
                },
                isSystem,
                bundleAlias).getBody();
    }

    public Optional<WidgetType> getWidgetType(boolean isSystem, String bundleAlias, String alias) {
        try {
            ResponseEntity<WidgetType> widgetType =
                    restTemplate.getForEntity(
                            baseURL + "/api/widgetType?isSystem={isSystem}&bundleAlias={bundleAlias}&alias={alias}",
                            WidgetType.class,
                            isSystem,
                            bundleAlias,
                            alias);
            return Optional.ofNullable(widgetType.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    private void addPageLinkToParam(Map<String, String> params, TimePageLink pageLink) {
        params.put("limit", String.valueOf(pageLink.getLimit()));
        if (pageLink.getStartTime() != null) {
            params.put("startTime", String.valueOf(pageLink.getStartTime()));
        }
        if (pageLink.getEndTime() != null) {
            params.put("endTime", String.valueOf(pageLink.getEndTime()));
        }
        params.put("ascOrder", String.valueOf(pageLink.isAscOrder()));
        if (pageLink.getIdOffset() != null) {
            params.put("offset", pageLink.getIdOffset().toString());
        }
    }

    private void addPageLinkToParam(Map<String, String> params, TextPageLink pageLink) {
        params.put("limit", String.valueOf(pageLink.getLimit()));
        if (pageLink.getTextSearch() != null) {
            params.put("textSearch", pageLink.getTextSearch());
        }

        if (pageLink.getIdOffset() != null) {
            params.put("idOffset", pageLink.getIdOffset().toString());

        }
        if (pageLink.getTextOffset() != null) {
            params.put("textOffset", pageLink.getTextOffset());
        }
    }
}
