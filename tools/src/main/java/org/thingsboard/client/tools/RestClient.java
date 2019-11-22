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
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.device.DeviceSearchQuery;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.group.EntityGroupInfo;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.scheduler.SchedulerEvent;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.data.security.DeviceCredentialsType;
import org.thingsboard.server.common.data.security.UserCredentials;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Andrew Shvayka
 */
@RequiredArgsConstructor
public class RestClient implements ClientHttpRequestInterceptor {
    private static final String JWT_TOKEN_HEADER_PARAM = "X-Authorization";
    private final RestTemplate restTemplate = new RestTemplate();
    private String token;
    private final String baseURL;

    public void login(String username, String password) {
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", username);
        loginRequest.put("password", password);
        ResponseEntity<JsonNode> tokenInfo = restTemplate.postForEntity(baseURL + "/api/auth/login", loginRequest, JsonNode.class);
        this.token = tokenInfo.getBody().get("token").asText();
        restTemplate.setInterceptors(Collections.singletonList(this));
    }

    public Optional<Device> findDevice(String name) {
        Map<String, String> params = new HashMap<String, String>();
        params.put("deviceName", name);
        try {
            ResponseEntity<Device> deviceEntity = restTemplate.getForEntity(baseURL + "/api/tenant/devices?deviceName={deviceName}", Device.class, params);
            return Optional.ofNullable(deviceEntity.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public List<Device> findDevicesByQuery(DeviceSearchQuery deviceSearchQuery) {
        try {
            ResponseEntity<List<Device>> responseEntity = restTemplate.exchange(baseURL + "/api/devices", HttpMethod.POST, new HttpEntity<>(deviceSearchQuery), new ParameterizedTypeReference<List<Device>>() {
            });
            return responseEntity.getBody();
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Collections.emptyList();
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
            return Optional.ofNullable(customerEntity.getBody());
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
            return Optional.ofNullable(assetEntity.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<TextPageData<RuleChain>> findRuleChains(int limit, UUID idOffset, String textOffset, String textSearch) {
        Map<String, Object> params = new HashMap<>();
        params.put("limit", limit);
        params.put("textSearch", textSearch);
        params.put("idOffset", idOffset);
        params.put("textOffset", textOffset);
        try {
            ResponseEntity<TextPageData<RuleChain>> responseEntity = restTemplate.exchange(baseURL + "/api/ruleChains?limit={limit}&idOffset={idOffset}&textOffset={textOffset}&textSearch={textSearch}", HttpMethod.GET, null, new ParameterizedTypeReference<TextPageData<RuleChain>>() {
            }, params);
            return Optional.ofNullable(responseEntity.getBody());
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
            return Optional.ofNullable(telemetryEntity.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<JsonNode> getEntityAttributesByIdAndType(String entityType, String entityId, String keys) {
        Map<String, String> params = new HashMap<>();
        params.put("entityType", entityType);
        params.put("entityId", entityId);
        params.put("keys", keys);
        try {
            ResponseEntity<JsonNode> telemetryEntity = restTemplate.getForEntity(baseURL + "/api/plugins/telemetry/{entityType}/{entityId}/values/attributes?keys={keys}", JsonNode.class, params);
            return Optional.ofNullable(telemetryEntity.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<JsonNode> getLatestTimeseries(String entityType, String entityId, String keys) {
        Map<String, String> params = new HashMap<>();
        params.put("entityType", entityType);
        params.put("entityId", entityId);
        params.put("keys", keys);
        try {
            ResponseEntity<JsonNode> currentUserResponceEntity = restTemplate.getForEntity(baseURL + "/api/plugins/telemetry/{entityType}/{entityId}/values/timeseries?keys={keys}", JsonNode.class, params);
            return Optional.ofNullable(currentUserResponceEntity.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public Optional<User> getCurrentTenantUser() {
        try {
            ResponseEntity<User> currentUserResponceEntity = restTemplate.getForEntity(baseURL + "/api/auth/user", User.class);
            return Optional.ofNullable(currentUserResponceEntity.getBody());
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

    public Device createDevice(String name, String type, String label) {
        Device device = new Device();
        device.setName(name);
        device.setType(type);
        device.setLabel(label);
        return restTemplate.postForEntity(baseURL + "/api/device", device, Device.class).getBody();
    }

    public Device createDevice(String name, String type, String label, CustomerId customerId) {
        Device device = new Device();
        device.setName(name);
        device.setType(type);
        device.setLabel(label);
        device.setCustomerId(customerId);
        return restTemplate.postForEntity(baseURL + "/api/device", device, Device.class).getBody();
    }

    public DeviceCredentials updateDeviceCredentials(DeviceId deviceId, String token) {
        DeviceCredentials deviceCredentials = getCredentials(deviceId);
        deviceCredentials.setCredentialsType(DeviceCredentialsType.ACCESS_TOKEN);
        deviceCredentials.setCredentialsId(token);
        return saveDeviceCredentials(deviceCredentials);
    }

    public DeviceCredentials saveDeviceCredentials(DeviceCredentials deviceCredentials) {
        return restTemplate.postForEntity(baseURL + "/api/device/credentials", deviceCredentials, DeviceCredentials.class).getBody();
    }

    public Device createDevice(Device device) {
        return restTemplate.postForEntity(baseURL + "/api/device", device, Device.class).getBody();
    }

    public UserCredentials saveUserCredentials(UserCredentials userCredentials) {
        return restTemplate.postForEntity(baseURL + "/api/user/credentials", userCredentials, UserCredentials.class).getBody();
    }

    public JsonNode activateUser(JsonNode activateRequest) {
        return restTemplate.postForEntity(baseURL + "/api/noauth/activate/", activateRequest, JsonNode.class).getBody();
    }

    public User createUser(User user) {
        return restTemplate.postForEntity(baseURL + "/api/user?sendActivationMail=false", user, User.class).getBody();
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

    public EntityGroup createEntityGroup(EntityGroup entityGroup) {
        return restTemplate.postForEntity(baseURL + "/api/entityGroup", entityGroup, EntityGroup.class).getBody();
    }

    public void addEntitiesToEntityGroup(String entityGroupId, List<String> strEntityIds) {
        restTemplate.postForEntity(baseURL + "/api/entityGroup/{entityGroupId}/addEntities", strEntityIds, Void.class, entityGroupId);
    }

    public EntityGroup createEntityGroupByNameAndType(String name, String type) {
        EntityGroup entityGroup = new EntityGroup();
        entityGroup.setName(name);
        entityGroup.setType(EntityType.valueOf(type));
        return restTemplate.postForEntity(baseURL + "/api/entityGroup", entityGroup, EntityGroup.class).getBody();
    }

    public SchedulerEvent createSchedulerEvent(SchedulerEvent schedulerEvent) {
        return restTemplate.postForEntity(baseURL + "/api/schedulerEvent", schedulerEvent, SchedulerEvent.class).getBody();
    }

    public Dashboard createDashboard(Dashboard dashboard) {
        return restTemplate.postForEntity(baseURL + "/api/dashboard", dashboard, Dashboard.class).getBody();
    }

    public RuleChain createRuleChain(RuleChain ruleChain) {
        return restTemplate.postForEntity(baseURL + "/api/ruleChain", ruleChain, RuleChain.class).getBody();
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

    public Device assignDevice(CustomerId customerId, DeviceId deviceId) {
        return restTemplate.postForEntity(baseURL + "/api/customer/{customerId}/device/{deviceId}", null, Device.class,
                customerId.toString(), deviceId.toString()).getBody();
    }

    public Asset assignAsset(CustomerId customerId, AssetId assetId) {
        return restTemplate.postForEntity(baseURL + "/api/customer/{customerId}/asset/{assetId}", null, Asset.class,
                customerId.toString(), assetId.toString()).getBody();
    }

    public EntityRelation makeRelation(String relationType, EntityId idFrom, EntityId idTo) {
        EntityRelation relation = new EntityRelation();
        relation.setFrom(idFrom);
        relation.setTo(idTo);
        relation.setType(relationType);
        return restTemplate.postForEntity(baseURL + "/api/relation", relation, EntityRelation.class).getBody();
    }

    public JsonNode saveEntityAttributes(EntityId entityId, String scope, JsonNode attributes) {
        return restTemplate.postForEntity(baseURL + "/api/plugins/telemetry/{entityType}/{entityId}/attributes/{scope}", attributes, JsonNode.class, entityId.getEntityType().name(), entityId.toString(), scope).getBody();
    }

    public RuleChainMetaData saveRuleChainMetaData(RuleChainMetaData metaData) {
        return restTemplate.postForEntity(baseURL + "/api/ruleChain/metadata", metaData, RuleChainMetaData.class).getBody();
    }

    public RuleChain setRootRuleChain(RuleChainId ruleChainId) {
        return restTemplate.postForEntity(baseURL + "/api/ruleChain/{ruleChainId}/root", null, RuleChain.class, ruleChainId.toString()).getBody();
    }

    public Optional<RuleChainMetaData> getRuleChainMetaData(RuleChainId ruleChainId) {
        try {
            ResponseEntity<RuleChainMetaData> responseEntity = restTemplate.getForEntity(baseURL + "/api/ruleChain/{ruleChainId}/metadata", RuleChainMetaData.class, ruleChainId.toString());
            return Optional.ofNullable(responseEntity.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public DeviceCredentials getCredentials(DeviceId id) {
        return restTemplate.getForEntity(baseURL + "/api/device/" + id.getId().toString() + "/credentials", DeviceCredentials.class).getBody();
    }

    public UserCredentials getUserCredentials(UserId userId) {
        return restTemplate.getForEntity(baseURL + "/api/user/{userId}/credentials", UserCredentials.class, userId.getId().toString()).getBody();
    }

    public String getUserActivationLink(UserId userId) {
        return restTemplate.getForEntity(baseURL + "/api/user/{userId}/activationLink", String.class, userId.getId().toString()).getBody();
    }

    public Optional<EntityGroupInfo> getEntityGroupInfoByOwnerAndNameAndType(String ownerType, String ownerId, String groupType, String groupName) {
        try {
            EntityGroupInfo entity = restTemplate.getForEntity(baseURL + "/api/entityGroup/{ownerType}/{ownerId}/{groupType}/{groupName}"
                    , EntityGroupInfo.class, ownerType, ownerId, groupType, groupName
            ).getBody();
            return Optional.ofNullable(entity);
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw exception;
            }
        }
    }

    public RestTemplate getRestTemplate() {
        return restTemplate;
    }

    public String getToken() {
        return token;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] bytes, ClientHttpRequestExecution execution) throws IOException {
        HttpRequest wrapper = new HttpRequestWrapper(request);
        wrapper.getHeaders().set(JWT_TOKEN_HEADER_PARAM, "Bearer " + token);
        return execution.execute(wrapper, bytes);
    }
}