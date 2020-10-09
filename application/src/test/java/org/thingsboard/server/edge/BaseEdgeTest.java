/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.edge;

import com.datastax.driver.core.utils.UUIDs;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonObject;
import com.google.protobuf.AbstractMessage;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.server.common.adaptor.JsonConverter;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.Edge;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmInfo;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.alarm.AlarmStatus;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.edge.EdgeEventType;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.page.TimePageData;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainType;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.data.widget.WidgetType;
import org.thingsboard.server.common.data.widget.WidgetsBundle;
import org.thingsboard.server.controller.AbstractControllerTest;
import org.thingsboard.server.dao.edge.EdgeEventService;
import org.thingsboard.server.dao.util.mapping.JacksonUtil;
import org.thingsboard.server.edge.imitator.EdgeImitator;
import org.thingsboard.server.gen.edge.AlarmUpdateMsg;
import org.thingsboard.server.gen.edge.AssetUpdateMsg;
import org.thingsboard.server.gen.edge.CustomerUpdateMsg;
import org.thingsboard.server.gen.edge.DashboardUpdateMsg;
import org.thingsboard.server.gen.edge.DeviceCredentialsRequestMsg;
import org.thingsboard.server.gen.edge.DeviceCredentialsUpdateMsg;
import org.thingsboard.server.gen.edge.DeviceUpdateMsg;
import org.thingsboard.server.gen.edge.EdgeConfiguration;
import org.thingsboard.server.gen.edge.EntityDataProto;
import org.thingsboard.server.gen.edge.EntityViewUpdateMsg;
import org.thingsboard.server.gen.edge.RelationUpdateMsg;
import org.thingsboard.server.gen.edge.RuleChainMetadataRequestMsg;
import org.thingsboard.server.gen.edge.RuleChainMetadataUpdateMsg;
import org.thingsboard.server.gen.edge.RuleChainUpdateMsg;
import org.thingsboard.server.gen.edge.UpdateMsgType;
import org.thingsboard.server.gen.edge.UplinkMsg;
import org.thingsboard.server.gen.edge.UserCredentialsRequestMsg;
import org.thingsboard.server.gen.edge.UserCredentialsUpdateMsg;
import org.thingsboard.server.gen.edge.WidgetTypeUpdateMsg;
import org.thingsboard.server.gen.edge.WidgetsBundleUpdateMsg;
import org.thingsboard.server.gen.transport.TransportProtos;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

;


@Slf4j
abstract public class BaseEdgeTest extends AbstractControllerTest {

    private Tenant savedTenant;
    private TenantId tenantId;
    private User tenantAdmin;

    private EdgeImitator edgeImitator;
    private Edge edge;

    @Autowired
    private EdgeEventService edgeEventService;

    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();

        Tenant tenant = new Tenant();
        tenant.setTitle("My tenant");
        savedTenant = doPost("/api/tenant", tenant, Tenant.class);
        tenantId = savedTenant.getId();
        Assert.assertNotNull(savedTenant);

        tenantAdmin = new User();
        tenantAdmin.setAuthority(Authority.TENANT_ADMIN);
        tenantAdmin.setTenantId(savedTenant.getId());
        tenantAdmin.setEmail("tenant2@thingsboard.org");
        tenantAdmin.setFirstName("Joe");
        tenantAdmin.setLastName("Downs");

        tenantAdmin = createUserAndLogin(tenantAdmin, "testPassword1");
        installation();

        edgeImitator = new EdgeImitator("localhost", 7070, edge.getRoutingKey(), edge.getSecret());
        // should be less, but events from SyncEdgeService stack with events from controller. will be fixed in next releases
        edgeImitator.expectMessageAmount(6);
        edgeImitator.connect();
    }

    @After
    public void afterTest() throws Exception {
        edgeImitator.disconnect();
        uninstallation();

        loginSysAdmin();

        doDelete("/api/tenant/" + savedTenant.getId().getId().toString())
                .andExpect(status().isOk());
    }


    @Test
    public void test() throws Exception {
        testReceivedInitialData();
        testDevices();
        testAssets();
        testRuleChains();
        testDashboards();
        testRelations();
        testAlarms();
        testEntityView();
        testCustomer();
        testWidgetsBundleAndWidgetType();
        testTimeseries();
        testAttributes();
        testSendMessagesToCloud();
    }

    private void testReceivedInitialData() throws Exception {
        log.info("Checking received data");
        edgeImitator.waitForMessages();

        EdgeConfiguration configuration = edgeImitator.getConfiguration();
        Assert.assertNotNull(configuration);

        UserId userId = edgeImitator.getUserId();
        Assert.assertNotNull(userId);

        Optional<DeviceUpdateMsg> optionalMsg1 = edgeImitator.findMessageByType(DeviceUpdateMsg.class);
        Assert.assertTrue(optionalMsg1.isPresent());
        DeviceUpdateMsg deviceUpdateMsg = optionalMsg1.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, deviceUpdateMsg.getMsgType());
        UUID deviceUUID = new UUID(deviceUpdateMsg.getIdMSB(), deviceUpdateMsg.getIdLSB());
        Device device = doGet("/api/device/" + deviceUUID.toString(), Device.class);
        Assert.assertNotNull(device);
        List<Device> edgeDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100)).getData();
        Assert.assertTrue(edgeDevices.contains(device));

        Optional<AssetUpdateMsg> optionalMsg2 = edgeImitator.findMessageByType(AssetUpdateMsg.class);
        Assert.assertTrue(optionalMsg2.isPresent());
        AssetUpdateMsg assetUpdateMsg = optionalMsg2.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, assetUpdateMsg.getMsgType());
        UUID assetUUID = new UUID(assetUpdateMsg.getIdMSB(), assetUpdateMsg.getIdLSB());
        Asset asset = doGet("/api/asset/" + assetUUID.toString(), Asset.class);
        Assert.assertNotNull(asset);
        List<Asset> edgeAssets = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/assets?",
                new TypeReference<TimePageData<Asset>>() {}, new TextPageLink(100)).getData();
        Assert.assertTrue(edgeAssets.contains(asset));

        Optional<RuleChainUpdateMsg> optionalMsg3 = edgeImitator.findMessageByType(RuleChainUpdateMsg.class);
        Assert.assertTrue(optionalMsg3.isPresent());
        RuleChainUpdateMsg ruleChainUpdateMsg = optionalMsg3.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE, ruleChainUpdateMsg.getMsgType());
        UUID ruleChainUUID = new UUID(ruleChainUpdateMsg.getIdMSB(), ruleChainUpdateMsg.getIdLSB());
        RuleChain ruleChain = doGet("/api/ruleChain/" + ruleChainUUID.toString(), RuleChain.class);
        Assert.assertNotNull(ruleChain);
        List<RuleChain> edgeRuleChains = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/ruleChains?",
                new TypeReference<TimePageData<RuleChain>>() {}, new TextPageLink(100)).getData();
        Assert.assertTrue(edgeRuleChains.contains(ruleChain));

        log.info("Received data checked");
    }

   private void testDevices() throws Exception {
        log.info("Testing devices");

        Device device = new Device();
        device.setName("Edge Device 2");
        device.setType("test");
        Device savedDevice = doPost("/api/device", device, Device.class);

        edgeImitator.expectMessageAmount(1);
        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/device/" + savedDevice.getId().getId().toString(), Device.class);
        edgeImitator.waitForMessages();

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DeviceUpdateMsg);
        DeviceUpdateMsg deviceUpdateMsg = (DeviceUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, deviceUpdateMsg.getMsgType());
        Assert.assertEquals(deviceUpdateMsg.getIdMSB(), savedDevice.getUuidId().getMostSignificantBits());
        Assert.assertEquals(deviceUpdateMsg.getIdLSB(), savedDevice.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(deviceUpdateMsg.getName(), savedDevice.getName());
        Assert.assertEquals(deviceUpdateMsg.getType(), savedDevice.getType());

        edgeImitator.expectMessageAmount(1);
        doDelete("/api/edge/" + edge.getId().getId().toString()
                + "/device/" + savedDevice.getId().getId().toString(), Device.class);
        edgeImitator.waitForMessages();

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DeviceUpdateMsg);
        deviceUpdateMsg = (DeviceUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, deviceUpdateMsg.getMsgType());
        Assert.assertEquals(deviceUpdateMsg.getIdMSB(), savedDevice.getUuidId().getMostSignificantBits());
        Assert.assertEquals(deviceUpdateMsg.getIdLSB(), savedDevice.getUuidId().getLeastSignificantBits());

        edgeImitator.expectMessageAmount(1);
        doDelete("/api/device/" + savedDevice.getId().getId().toString())
                .andExpect(status().isOk());
        edgeImitator.waitForMessages();

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DeviceUpdateMsg);
        deviceUpdateMsg = (DeviceUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, deviceUpdateMsg.getMsgType());
        Assert.assertEquals(deviceUpdateMsg.getIdMSB(), savedDevice.getUuidId().getMostSignificantBits());
        Assert.assertEquals(deviceUpdateMsg.getIdLSB(), savedDevice.getUuidId().getLeastSignificantBits());

        log.info("Devices tested successfully");
    }


    private void testAssets() throws Exception {
        log.info("Testing assets");
        Asset asset = new Asset();
        asset.setName("Edge Asset 2");
        asset.setType("test");
        Asset savedAsset = doPost("/api/asset", asset, Asset.class);

        edgeImitator.expectMessageAmount(1);
        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/asset/" + savedAsset.getId().getId().toString(), Asset.class);
        edgeImitator.waitForMessages();

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof AssetUpdateMsg);
        AssetUpdateMsg assetUpdateMsg = (AssetUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, assetUpdateMsg.getMsgType());
        Assert.assertEquals(assetUpdateMsg.getIdMSB(), savedAsset.getUuidId().getMostSignificantBits());
        Assert.assertEquals(assetUpdateMsg.getIdLSB(), savedAsset.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(assetUpdateMsg.getName(), savedAsset.getName());
        Assert.assertEquals(assetUpdateMsg.getType(), savedAsset.getType());

        edgeImitator.expectMessageAmount(1);
        doDelete("/api/edge/" + edge.getId().getId().toString()
                + "/asset/" + savedAsset.getId().getId().toString(), Asset.class);
        edgeImitator.waitForMessages();

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof AssetUpdateMsg);
        assetUpdateMsg = (AssetUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, assetUpdateMsg.getMsgType());
        Assert.assertEquals(assetUpdateMsg.getIdMSB(), savedAsset.getUuidId().getMostSignificantBits());
        Assert.assertEquals(assetUpdateMsg.getIdLSB(), savedAsset.getUuidId().getLeastSignificantBits());

        edgeImitator.expectMessageAmount(1);
        doDelete("/api/asset/" + savedAsset.getId().getId().toString())
                .andExpect(status().isOk());
        edgeImitator.waitForMessages();

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof AssetUpdateMsg);
        assetUpdateMsg = (AssetUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, assetUpdateMsg.getMsgType());
        Assert.assertEquals(assetUpdateMsg.getIdMSB(), savedAsset.getUuidId().getMostSignificantBits());
        Assert.assertEquals(assetUpdateMsg.getIdLSB(), savedAsset.getUuidId().getLeastSignificantBits());

        log.info("Assets tested successfully");
    }

    private void testRuleChains() throws Exception {
        log.info("Testing RuleChains");
        RuleChain ruleChain = new RuleChain();
        ruleChain.setName("Edge Test Rule Chain");
        ruleChain.setType(RuleChainType.EDGE);
        RuleChain savedRuleChain = doPost("/api/ruleChain", ruleChain, RuleChain.class);

        edgeImitator.expectMessageAmount(1);
        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/ruleChain/" + savedRuleChain.getId().getId().toString(), RuleChain.class);
        edgeImitator.waitForMessages();

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof RuleChainUpdateMsg);
        RuleChainUpdateMsg ruleChainUpdateMsg = (RuleChainUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, ruleChainUpdateMsg.getMsgType());
        Assert.assertEquals(ruleChainUpdateMsg.getIdMSB(), savedRuleChain.getUuidId().getMostSignificantBits());
        Assert.assertEquals(ruleChainUpdateMsg.getIdLSB(), savedRuleChain.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(ruleChainUpdateMsg.getName(), savedRuleChain.getName());

        edgeImitator.expectMessageAmount(1);
        doDelete("/api/edge/" + edge.getId().getId().toString()
                + "/ruleChain/" + savedRuleChain.getId().getId().toString(), RuleChain.class);
        edgeImitator.waitForMessages();

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof RuleChainUpdateMsg);
        ruleChainUpdateMsg = (RuleChainUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, ruleChainUpdateMsg.getMsgType());
        Assert.assertEquals(ruleChainUpdateMsg.getIdMSB(), savedRuleChain.getUuidId().getMostSignificantBits());
        Assert.assertEquals(ruleChainUpdateMsg.getIdLSB(), savedRuleChain.getUuidId().getLeastSignificantBits());

        edgeImitator.expectMessageAmount(1);
        doDelete("/api/ruleChain/" + savedRuleChain.getId().getId().toString())
                .andExpect(status().isOk());
        edgeImitator.waitForMessages();

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof RuleChainUpdateMsg);
        ruleChainUpdateMsg = (RuleChainUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, ruleChainUpdateMsg.getMsgType());
        Assert.assertEquals(ruleChainUpdateMsg.getIdMSB(), savedRuleChain.getUuidId().getMostSignificantBits());
        Assert.assertEquals(ruleChainUpdateMsg.getIdLSB(), savedRuleChain.getUuidId().getLeastSignificantBits());

        log.info("RuleChains tested successfully");
    }

    private void testDashboards() throws Exception {
        log.info("Testing Dashboards");
        Dashboard dashboard = new Dashboard();
        dashboard.setTitle("Edge Test Dashboard");
        Dashboard savedDashboard = doPost("/api/dashboard", dashboard, Dashboard.class);

        edgeImitator.expectMessageAmount(1);
        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/dashboard/" + savedDashboard.getId().getId().toString(), Dashboard.class);
        edgeImitator.waitForMessages();

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DashboardUpdateMsg);
        DashboardUpdateMsg dashboardUpdateMsg = (DashboardUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, dashboardUpdateMsg.getMsgType());
        Assert.assertEquals(dashboardUpdateMsg.getIdMSB(), savedDashboard.getUuidId().getMostSignificantBits());
        Assert.assertEquals(dashboardUpdateMsg.getIdLSB(), savedDashboard.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(dashboardUpdateMsg.getTitle(), savedDashboard.getName());

        edgeImitator.expectMessageAmount(1);
        savedDashboard.setTitle("Updated Edge Test Dashboard");
        doPost("/api/dashboard", savedDashboard, Dashboard.class);
        edgeImitator.waitForMessages();

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DashboardUpdateMsg);
        dashboardUpdateMsg = (DashboardUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE, dashboardUpdateMsg.getMsgType());
        Assert.assertEquals(dashboardUpdateMsg.getTitle(), savedDashboard.getName());

        edgeImitator.expectMessageAmount(1);
        doDelete("/api/edge/" + edge.getId().getId().toString()
                + "/dashboard/" + savedDashboard.getId().getId().toString(), Dashboard.class);
        edgeImitator.waitForMessages();

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DashboardUpdateMsg);
        dashboardUpdateMsg = (DashboardUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, dashboardUpdateMsg.getMsgType());
        Assert.assertEquals(dashboardUpdateMsg.getIdMSB(), savedDashboard.getUuidId().getMostSignificantBits());
        Assert.assertEquals(dashboardUpdateMsg.getIdLSB(), savedDashboard.getUuidId().getLeastSignificantBits());

        edgeImitator.expectMessageAmount(1);
        doDelete("/api/dashboard/" + savedDashboard.getId().getId().toString())
                .andExpect(status().isOk());
        edgeImitator.waitForMessages();

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DashboardUpdateMsg);
        dashboardUpdateMsg = (DashboardUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, dashboardUpdateMsg.getMsgType());
        Assert.assertEquals(dashboardUpdateMsg.getIdMSB(), savedDashboard.getUuidId().getMostSignificantBits());
        Assert.assertEquals(dashboardUpdateMsg.getIdLSB(), savedDashboard.getUuidId().getLeastSignificantBits());

        log.info("Dashboards tested successfully");
    }

    private void testRelations() throws Exception {
        log.info("Testing Relations");
        List<Device> edgeDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100)).getData();
        List<Asset> edgeAssets = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/assets?",
                new TypeReference<TimePageData<Asset>>() {}, new TextPageLink(100)).getData();

        Assert.assertEquals(1, edgeDevices.size());
        Assert.assertEquals(1, edgeAssets.size());
        Device device = edgeDevices.get(0);
        Asset asset = edgeAssets.get(0);
        Assert.assertEquals("Edge Device 1", device.getName());
        Assert.assertEquals("Edge Asset 1", asset.getName());

        EntityRelation relation = new EntityRelation();
        relation.setType("test");
        relation.setFrom(device.getId());
        relation.setTo(asset.getId());
        relation.setTypeGroup(RelationTypeGroup.COMMON);

        edgeImitator.expectMessageAmount(1);
        doPost("/api/relation", relation);
        edgeImitator.waitForMessages();

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof RelationUpdateMsg);
        RelationUpdateMsg relationUpdateMsg = (RelationUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, relationUpdateMsg.getMsgType());
        Assert.assertEquals(relationUpdateMsg.getType(), relation.getType());
        Assert.assertEquals(relationUpdateMsg.getFromIdMSB(), relation.getFrom().getId().getMostSignificantBits());
        Assert.assertEquals(relationUpdateMsg.getFromIdLSB(), relation.getFrom().getId().getLeastSignificantBits());
        Assert.assertEquals(relationUpdateMsg.getToEntityType(), relation.getTo().getEntityType().name());
        Assert.assertEquals(relationUpdateMsg.getFromIdMSB(), relation.getFrom().getId().getMostSignificantBits());
        Assert.assertEquals(relationUpdateMsg.getToIdLSB(), relation.getTo().getId().getLeastSignificantBits());
        Assert.assertEquals(relationUpdateMsg.getToEntityType(), relation.getTo().getEntityType().name());
        Assert.assertEquals(relationUpdateMsg.getTypeGroup(), relation.getTypeGroup().name());

        edgeImitator.expectMessageAmount(1);
        doDelete("/api/relation?" +
                "fromId=" + relation.getFrom().getId().toString() +
                "&fromType=" + relation.getFrom().getEntityType().name() +
                "&relationType=" + relation.getType() +
                "&relationTypeGroup=" + relation.getTypeGroup().name() +
                "&toId=" + relation.getTo().getId().toString() +
                "&toType=" + relation.getTo().getEntityType().name())
                .andExpect(status().isOk());
        edgeImitator.waitForMessages();

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof RelationUpdateMsg);
        relationUpdateMsg = (RelationUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, relationUpdateMsg.getMsgType());
        Assert.assertEquals(relationUpdateMsg.getType(), relation.getType());
        Assert.assertEquals(relationUpdateMsg.getFromIdMSB(), relation.getFrom().getId().getMostSignificantBits());
        Assert.assertEquals(relationUpdateMsg.getFromIdLSB(), relation.getFrom().getId().getLeastSignificantBits());
        Assert.assertEquals(relationUpdateMsg.getToEntityType(), relation.getTo().getEntityType().name());Assert.assertEquals(relationUpdateMsg.getFromIdMSB(), relation.getFrom().getId().getMostSignificantBits());
        Assert.assertEquals(relationUpdateMsg.getToIdLSB(), relation.getTo().getId().getLeastSignificantBits());
        Assert.assertEquals(relationUpdateMsg.getToEntityType(), relation.getTo().getEntityType().name());
        Assert.assertEquals(relationUpdateMsg.getTypeGroup(), relation.getTypeGroup().name());

        log.info("Relations tested successfully");
    }

    private void testAlarms() throws Exception {
        log.info("Testing Alarms");
        List<Device> edgeDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100)).getData();
        Assert.assertEquals(1, edgeDevices.size());
        Device device = edgeDevices.get(0);
        Assert.assertEquals("Edge Device 1", device.getName());

        Alarm alarm = new Alarm();
        alarm.setOriginator(device.getId());
        alarm.setStatus(AlarmStatus.ACTIVE_UNACK);
        alarm.setType("alarm");
        alarm.setSeverity(AlarmSeverity.CRITICAL);

        edgeImitator.expectMessageAmount(1);
        Alarm savedAlarm = doPost("/api/alarm", alarm, Alarm.class);
        edgeImitator.waitForMessages();

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof AlarmUpdateMsg);
        AlarmUpdateMsg alarmUpdateMsg = (AlarmUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, alarmUpdateMsg.getMsgType());
        Assert.assertEquals(alarmUpdateMsg.getType(), savedAlarm.getType());
        Assert.assertEquals(alarmUpdateMsg.getName(), savedAlarm.getName());
        Assert.assertEquals(alarmUpdateMsg.getOriginatorName(), device.getName());
        Assert.assertEquals(alarmUpdateMsg.getStatus(), savedAlarm.getStatus().name());
        Assert.assertEquals(alarmUpdateMsg.getSeverity(), savedAlarm.getSeverity().name());

        edgeImitator.expectMessageAmount(1);
        doPost("/api/alarm/" + savedAlarm.getId().getId().toString() + "/ack");
        edgeImitator.waitForMessages();

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof AlarmUpdateMsg);
        alarmUpdateMsg = (AlarmUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ALARM_ACK_RPC_MESSAGE, alarmUpdateMsg.getMsgType());
        Assert.assertEquals(alarmUpdateMsg.getType(), savedAlarm.getType());
        Assert.assertEquals(alarmUpdateMsg.getName(), savedAlarm.getName());
        Assert.assertEquals(alarmUpdateMsg.getOriginatorName(), device.getName());
        Assert.assertEquals(alarmUpdateMsg.getStatus(), AlarmStatus.ACTIVE_ACK.name());

        edgeImitator.expectMessageAmount(1);
        doPost("/api/alarm/" + savedAlarm.getId().getId().toString() + "/clear");
        edgeImitator.waitForMessages();

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof AlarmUpdateMsg);
        alarmUpdateMsg = (AlarmUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ALARM_CLEAR_RPC_MESSAGE, alarmUpdateMsg.getMsgType());
        Assert.assertEquals(alarmUpdateMsg.getType(), savedAlarm.getType());
        Assert.assertEquals(alarmUpdateMsg.getName(), savedAlarm.getName());
        Assert.assertEquals(alarmUpdateMsg.getOriginatorName(), device.getName());
        Assert.assertEquals(alarmUpdateMsg.getStatus(), AlarmStatus.CLEARED_ACK.name());

        doDelete("/api/alarm/" + savedAlarm.getId().getId().toString())
            .andExpect(status().isOk());
        log.info("Alarms tested successfully");
    }

    private void testEntityView() throws Exception {
        log.info("Testing EntityView");
        List<Device> edgeDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100)).getData();
        Assert.assertEquals(1, edgeDevices.size());
        Device device = edgeDevices.get(0);
        Assert.assertEquals("Edge Device 1", device.getName());

        EntityView entityView = new EntityView();
        entityView.setName("Edge EntityView 1");
        entityView.setType("test");
        entityView.setEntityId(device.getId());
        EntityView savedEntityView = doPost("/api/entityView", entityView, EntityView.class);

        edgeImitator.expectMessageAmount(1);
        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/entityView/" + savedEntityView.getId().getId().toString(), EntityView.class);
        edgeImitator.waitForMessages();

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityViewUpdateMsg);
        EntityViewUpdateMsg entityViewUpdateMsg = (EntityViewUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, entityViewUpdateMsg.getMsgType());
        Assert.assertEquals(entityViewUpdateMsg.getType(), savedEntityView.getType());
        Assert.assertEquals(entityViewUpdateMsg.getName(), savedEntityView.getName());
        Assert.assertEquals(entityViewUpdateMsg.getIdMSB(), savedEntityView.getUuidId().getMostSignificantBits());
        Assert.assertEquals(entityViewUpdateMsg.getIdLSB(), savedEntityView.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(entityViewUpdateMsg.getEntityIdMSB(), device.getUuidId().getMostSignificantBits());
        Assert.assertEquals(entityViewUpdateMsg.getEntityIdLSB(), device.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(entityViewUpdateMsg.getEntityType().name(), device.getId().getEntityType().name());

        edgeImitator.expectMessageAmount(1);
        doDelete("/api/edge/" + edge.getId().getId().toString()
                + "/entityView/" + savedEntityView.getId().getId().toString(), EntityView.class);
        edgeImitator.waitForMessages();

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityViewUpdateMsg);
        entityViewUpdateMsg = (EntityViewUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, entityViewUpdateMsg.getMsgType());
        Assert.assertEquals(entityViewUpdateMsg.getIdMSB(), savedEntityView.getUuidId().getMostSignificantBits());
        Assert.assertEquals(entityViewUpdateMsg.getIdLSB(), savedEntityView.getUuidId().getLeastSignificantBits());

        edgeImitator.expectMessageAmount(1);
        doDelete("/api/entityView/" + savedEntityView.getId().getId().toString())
                .andExpect(status().isOk());
        edgeImitator.waitForMessages();

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityViewUpdateMsg);
        entityViewUpdateMsg = (EntityViewUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, entityViewUpdateMsg.getMsgType());
        Assert.assertEquals(entityViewUpdateMsg.getIdMSB(), savedEntityView.getUuidId().getMostSignificantBits());
        Assert.assertEquals(entityViewUpdateMsg.getIdLSB(), savedEntityView.getUuidId().getLeastSignificantBits());

        log.info("EntityView tested successfully");
    }

    private void testCustomer() throws Exception {
        log.info("Testing Customer");

        Customer customer = new Customer();
        customer.setTitle("Edge Customer 1");
        Customer savedCustomer = doPost("/api/customer", customer, Customer.class);

        edgeImitator.expectMessageAmount(1);
        doPost("/api/customer/" + savedCustomer.getId().getId().toString()
                + "/edge/" + edge.getId().getId().toString(), Edge.class);
        edgeImitator.waitForMessages();

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof CustomerUpdateMsg);
        CustomerUpdateMsg customerUpdateMsg = (CustomerUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, customerUpdateMsg.getMsgType());
        Assert.assertEquals(customerUpdateMsg.getIdMSB(), savedCustomer.getUuidId().getMostSignificantBits());
        Assert.assertEquals(customerUpdateMsg.getIdLSB(), savedCustomer.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(customerUpdateMsg.getTitle(), savedCustomer.getTitle());

        edgeImitator.expectMessageAmount(1);
        doDelete("/api/customer/edge/" + edge.getId().getId().toString(), Edge.class);
        edgeImitator.waitForMessages();

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof CustomerUpdateMsg);
        customerUpdateMsg = (CustomerUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, customerUpdateMsg.getMsgType());
        Assert.assertEquals(customerUpdateMsg.getIdMSB(), savedCustomer.getUuidId().getMostSignificantBits());
        Assert.assertEquals(customerUpdateMsg.getIdLSB(), savedCustomer.getUuidId().getLeastSignificantBits());

        edgeImitator.expectMessageAmount(1);
        doDelete("/api/customer/" + savedCustomer.getId().getId().toString())
                .andExpect(status().isOk());
        edgeImitator.waitForMessages();

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof CustomerUpdateMsg);
        customerUpdateMsg = (CustomerUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, customerUpdateMsg.getMsgType());
        Assert.assertEquals(customerUpdateMsg.getIdMSB(), savedCustomer.getUuidId().getMostSignificantBits());
        Assert.assertEquals(customerUpdateMsg.getIdLSB(), savedCustomer.getUuidId().getLeastSignificantBits());

        log.info("Customer tested successfully");
    }

    private void testWidgetsBundleAndWidgetType() throws Exception {
        log.info("Testing WidgetsBundle and WidgetType");

        WidgetsBundle widgetsBundle = new WidgetsBundle();
        widgetsBundle.setTitle("Test Widget Bundle");

        edgeImitator.expectMessageAmount(1);
        WidgetsBundle savedWidgetsBundle = doPost("/api/widgetsBundle", widgetsBundle, WidgetsBundle.class);
        edgeImitator.waitForMessages();

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof WidgetsBundleUpdateMsg);
        WidgetsBundleUpdateMsg widgetsBundleUpdateMsg = (WidgetsBundleUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, widgetsBundleUpdateMsg.getMsgType());
        Assert.assertEquals(widgetsBundleUpdateMsg.getIdMSB(), savedWidgetsBundle.getUuidId().getMostSignificantBits());
        Assert.assertEquals(widgetsBundleUpdateMsg.getIdLSB(), savedWidgetsBundle.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(widgetsBundleUpdateMsg.getAlias(), savedWidgetsBundle.getAlias());
        Assert.assertEquals(widgetsBundleUpdateMsg.getTitle(), savedWidgetsBundle.getTitle());

        WidgetType widgetType = new WidgetType();
        widgetType.setName("Test Widget Type");
        widgetType.setBundleAlias(savedWidgetsBundle.getAlias());
        ObjectNode descriptor = mapper.createObjectNode();
        descriptor.put("key", "value");
        widgetType.setDescriptor(descriptor);

        edgeImitator.expectMessageAmount(1);
        WidgetType savedWidgetType = doPost("/api/widgetType", widgetType, WidgetType.class);
        edgeImitator.waitForMessages();

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof WidgetTypeUpdateMsg);
        WidgetTypeUpdateMsg widgetTypeUpdateMsg = (WidgetTypeUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, widgetTypeUpdateMsg.getMsgType());
        Assert.assertEquals(widgetTypeUpdateMsg.getIdMSB(), savedWidgetType.getUuidId().getMostSignificantBits());
        Assert.assertEquals(widgetTypeUpdateMsg.getIdLSB(), savedWidgetType.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(widgetTypeUpdateMsg.getAlias(), savedWidgetType.getAlias());
        Assert.assertEquals(widgetTypeUpdateMsg.getName(), savedWidgetType.getName());
        Assert.assertEquals(JacksonUtil.toJsonNode(widgetTypeUpdateMsg.getDescriptorJson()), savedWidgetType.getDescriptor());

        edgeImitator.expectMessageAmount(1);
        doDelete("/api/widgetType/" + savedWidgetType.getId().getId().toString())
                .andExpect(status().isOk());
        edgeImitator.waitForMessages();

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof WidgetTypeUpdateMsg);
        widgetTypeUpdateMsg = (WidgetTypeUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, widgetTypeUpdateMsg.getMsgType());
        Assert.assertEquals(widgetTypeUpdateMsg.getIdMSB(), savedWidgetType.getUuidId().getMostSignificantBits());
        Assert.assertEquals(widgetTypeUpdateMsg.getIdLSB(), savedWidgetType.getUuidId().getLeastSignificantBits());

        edgeImitator.expectMessageAmount(1);
        doDelete("/api/widgetsBundle/" + savedWidgetsBundle.getId().getId().toString())
                .andExpect(status().isOk());
        edgeImitator.waitForMessages();

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof WidgetsBundleUpdateMsg);
        widgetsBundleUpdateMsg = (WidgetsBundleUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, widgetsBundleUpdateMsg.getMsgType());
        Assert.assertEquals(widgetsBundleUpdateMsg.getIdMSB(), savedWidgetsBundle.getUuidId().getMostSignificantBits());
        Assert.assertEquals(widgetsBundleUpdateMsg.getIdLSB(), savedWidgetsBundle.getUuidId().getLeastSignificantBits());

        log.info("WidgetsBundle and WidgetType tested successfully");
    }

    private void testTimeseries() throws Exception {
        log.info("Testing timeseries");
        List<Device> edgeDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100)).getData();
        Assert.assertEquals(1, edgeDevices.size());
        Device device = edgeDevices.get(0);
        Assert.assertEquals("Edge Device 1", device.getName());

        String timeseriesData = "{\"data\":{\"temperature\":25},\"ts\":" + System.currentTimeMillis() + "}";
        JsonNode timeseriesEntityData = mapper.readTree(timeseriesData);
        EdgeEvent edgeEvent1 = constructEdgeEvent(tenantId, edge.getId(), ActionType.TIMESERIES_UPDATED, device.getId().getId(), EdgeEventType.DEVICE, timeseriesEntityData);
        edgeImitator.expectMessageAmount(1);
        edgeEventService.saveAsync(edgeEvent1);
        edgeImitator.waitForMessages();

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityDataProto);
        EntityDataProto latestEntityDataMsg = (EntityDataProto) latestMessage;
        Assert.assertEquals(latestEntityDataMsg.getEntityIdMSB(), device.getUuidId().getMostSignificantBits());
        Assert.assertEquals(latestEntityDataMsg.getEntityIdLSB(), device.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(latestEntityDataMsg.getEntityType(), device.getId().getEntityType().name());
        Assert.assertTrue(latestEntityDataMsg.hasPostTelemetryMsg());

        TransportProtos.PostTelemetryMsg postTelemetryMsg = latestEntityDataMsg.getPostTelemetryMsg();
        Assert.assertEquals(1, postTelemetryMsg.getTsKvListCount());
        TransportProtos.TsKvListProto tsKvListProto = postTelemetryMsg.getTsKvList(0);
        Assert.assertEquals(timeseriesEntityData.get("ts").asLong(), tsKvListProto.getTs());
        Assert.assertEquals(1, tsKvListProto.getKvCount());
        TransportProtos.KeyValueProto keyValueProto = tsKvListProto.getKv(0);
        Assert.assertEquals("temperature", keyValueProto.getKey());
        Assert.assertEquals(25, keyValueProto.getLongV());
        log.info("Timeseries tested successfully");
    }

    private void testAttributes() throws Exception {
        log.info("Testing attributes");
        List<Device> edgeDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100)).getData();
        Assert.assertEquals(1, edgeDevices.size());
        Device device = edgeDevices.get(0);
        Assert.assertEquals("Edge Device 1", device.getName());

        String attributesData = "{\"scope\":\"SERVER_SCOPE\",\"kv\":{\"key\":\"value\"}}";
        JsonNode attributesEntityData = mapper.readTree(attributesData);
        EdgeEvent edgeEvent1 = constructEdgeEvent(tenantId, edge.getId(), ActionType.ATTRIBUTES_UPDATED, device.getId().getId(), EdgeEventType.DEVICE, attributesEntityData);
        edgeImitator.expectMessageAmount(1);
        edgeEventService.saveAsync(edgeEvent1);
        edgeImitator.waitForMessages();

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityDataProto);
        EntityDataProto latestEntityDataMsg = (EntityDataProto) latestMessage;
        Assert.assertEquals(latestEntityDataMsg.getEntityIdMSB(), device.getUuidId().getMostSignificantBits());
        Assert.assertEquals(latestEntityDataMsg.getEntityIdLSB(), device.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(latestEntityDataMsg.getEntityType(), device.getId().getEntityType().name());
        Assert.assertEquals(latestEntityDataMsg.getPostAttributeScope(), attributesEntityData.get("scope").asText());
        Assert.assertTrue(latestEntityDataMsg.hasAttributesUpdatedMsg());

        TransportProtos.PostAttributeMsg attributesUpdatedMsg = latestEntityDataMsg.getAttributesUpdatedMsg();
        Assert.assertEquals(1, attributesUpdatedMsg.getKvCount());
        TransportProtos.KeyValueProto keyValueProto = attributesUpdatedMsg.getKv(0);
        Assert.assertEquals("key", keyValueProto.getKey());
        Assert.assertEquals("value", keyValueProto.getStringV());

        ((ObjectNode) attributesEntityData).put("isPostAttributes", true);
        EdgeEvent edgeEvent2 = constructEdgeEvent(tenantId, edge.getId(), ActionType.ATTRIBUTES_UPDATED, device.getId().getId(), EdgeEventType.DEVICE, attributesEntityData);
        edgeImitator.expectMessageAmount(1);
        edgeEventService.saveAsync(edgeEvent2);
        edgeImitator.waitForMessages();

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityDataProto);
        latestEntityDataMsg = (EntityDataProto) latestMessage;
        Assert.assertEquals(latestEntityDataMsg.getEntityIdMSB(), device.getUuidId().getMostSignificantBits());
        Assert.assertEquals(latestEntityDataMsg.getEntityIdLSB(), device.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(latestEntityDataMsg.getEntityType(), device.getId().getEntityType().name());
        Assert.assertEquals(latestEntityDataMsg.getPostAttributeScope(), attributesEntityData.get("scope").asText());
        Assert.assertTrue(latestEntityDataMsg.hasPostAttributesMsg());

        attributesUpdatedMsg = latestEntityDataMsg.getPostAttributesMsg();
        Assert.assertEquals(1, attributesUpdatedMsg.getKvCount());
        keyValueProto = attributesUpdatedMsg.getKv(0);
        Assert.assertEquals("key", keyValueProto.getKey());
        Assert.assertEquals("value", keyValueProto.getStringV());

        log.info("Attributes tested successfully");
    }

    private void testSendMessagesToCloud() throws Exception {
        log.info("Sending messages to cloud");
        sendDevice();
        sendAlarm();
        sendTelemetry();
        sendRelation();
        sendDeleteDeviceOnEdge();
        sendRuleChainMetadataRequest();
        sendUserCredentialsRequest();
        sendDeviceCredentialsRequest();
        log.info("Messages were sent successfully");
    }

    private void sendDevice() throws Exception {
        UUID uuid = UUIDs.timeBased();

        UplinkMsg.Builder builder =  UplinkMsg.newBuilder();
        DeviceUpdateMsg.Builder deviceUpdateMsgBuilder = DeviceUpdateMsg.newBuilder();
        deviceUpdateMsgBuilder.setIdMSB(uuid.getMostSignificantBits());
        deviceUpdateMsgBuilder.setIdLSB(uuid.getLeastSignificantBits());
        deviceUpdateMsgBuilder.setName("Edge Device 2");
        deviceUpdateMsgBuilder.setType("test");
        deviceUpdateMsgBuilder.setMsgType(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE);
        builder.addDeviceUpdateMsg(deviceUpdateMsgBuilder.build());
        edgeImitator.expectResponsesAmount(1);
        edgeImitator.sendUplinkMsg(builder.build());
        edgeImitator.waitForResponses();

        Device device = doGet("/api/device/" + uuid.toString(), Device.class);
        Assert.assertNotNull(device);
        Assert.assertEquals("Edge Device 2", device.getName());
    }

    private void sendAlarm() throws Exception {
        List<Device> edgeDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100)).getData();
        Optional<Device> foundDevice = edgeDevices.stream().filter(device1 -> device1.getName().equals("Edge Device 2")).findAny();
        Assert.assertTrue(foundDevice.isPresent());
        Device device = foundDevice.get();

        UplinkMsg.Builder builder = UplinkMsg.newBuilder();
        AlarmUpdateMsg.Builder alarmUpdateMgBuilder = AlarmUpdateMsg.newBuilder();
        alarmUpdateMgBuilder.setName("alarm from edge");
        alarmUpdateMgBuilder.setStatus(AlarmStatus.ACTIVE_UNACK.name());
        alarmUpdateMgBuilder.setSeverity(AlarmSeverity.CRITICAL.name());
        alarmUpdateMgBuilder.setOriginatorName(device.getName());
        alarmUpdateMgBuilder.setOriginatorType(EntityType.DEVICE.name());
        builder.addAlarmUpdateMsg(alarmUpdateMgBuilder.build());
        edgeImitator.expectResponsesAmount(1);
        edgeImitator.sendUplinkMsg(builder.build());
        edgeImitator.waitForResponses();


        List<AlarmInfo> alarms = doGetTypedWithPageLink("/api/alarm/{entityType}/{entityId}?",
                new TypeReference<TimePageData<AlarmInfo>>() {},
                new TextPageLink(100), device.getId().getEntityType().name(), device.getId().getId().toString())
                .getData();
        Optional<AlarmInfo> foundAlarm = alarms.stream().filter(alarm -> alarm.getType().equals("alarm from edge")).findAny();
        Assert.assertTrue(foundAlarm.isPresent());
        AlarmInfo alarmInfo = foundAlarm.get();
        Assert.assertEquals(device.getId(), alarmInfo.getOriginator());
        Assert.assertEquals(AlarmStatus.ACTIVE_UNACK, alarmInfo.getStatus());
        Assert.assertEquals(AlarmSeverity.CRITICAL, alarmInfo.getSeverity());
    }

    private void sendRelation() throws Exception {
        List<Device> edgeDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100)).getData();
        Optional<Device> foundDevice1 = edgeDevices.stream().filter(device1 -> device1.getName().equals("Edge Device 1")).findAny();
        Assert.assertTrue(foundDevice1.isPresent());
        Device device1 = foundDevice1.get();
        Optional<Device> foundDevice2 = edgeDevices.stream().filter(device2 -> device2.getName().equals("Edge Device 2")).findAny();
        Assert.assertTrue(foundDevice2.isPresent());
        Device device2 = foundDevice2.get();

        UplinkMsg.Builder builder = UplinkMsg.newBuilder();
        RelationUpdateMsg.Builder relationUpdateMsgBuilder = RelationUpdateMsg.newBuilder();
        relationUpdateMsgBuilder.setType("test");
        relationUpdateMsgBuilder.setTypeGroup(RelationTypeGroup.COMMON.name());
        relationUpdateMsgBuilder.setToIdMSB(device1.getId().getId().getMostSignificantBits());
        relationUpdateMsgBuilder.setToIdLSB(device1.getId().getId().getLeastSignificantBits());
        relationUpdateMsgBuilder.setToEntityType(device1.getId().getEntityType().name());
        relationUpdateMsgBuilder.setFromIdMSB(device2.getId().getId().getMostSignificantBits());
        relationUpdateMsgBuilder.setFromIdLSB(device2.getId().getId().getLeastSignificantBits());
        relationUpdateMsgBuilder.setFromEntityType(device2.getId().getEntityType().name());
        relationUpdateMsgBuilder.setAdditionalInfo("{}");
        builder.addRelationUpdateMsg(relationUpdateMsgBuilder.build());
        UplinkMsg msg = builder.build();
        edgeImitator.expectResponsesAmount(1);
        edgeImitator.sendUplinkMsg(msg);
        edgeImitator.waitForResponses();

        EntityRelation relation = doGet("/api/relation?" +
                "&fromId=" + device2.getId().getId().toString() +
                "&fromType=" + device2.getId().getEntityType().name() +
                "&relationType=" + "test" +
                "&relationTypeGroup=" + RelationTypeGroup.COMMON.name() +
                "&toId=" + device1.getId().getId().toString() +
                "&toType=" + device1.getId().getEntityType().name(), EntityRelation.class);
        Assert.assertNotNull(relation);
    }

    private void sendTelemetry() throws Exception {
        List<Device> edgeDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100)).getData();
        Optional<Device> foundDevice = edgeDevices.stream().filter(device1 -> device1.getName().equals("Edge Device 2")).findAny();
        Assert.assertTrue(foundDevice.isPresent());
        Device device = foundDevice.get();

        edgeImitator.expectResponsesAmount(2);

        JsonObject data = new JsonObject();
        String timeseriesKey = "key";
        String timeseriesValue = "25";
        data.addProperty(timeseriesKey, timeseriesValue);
        UplinkMsg.Builder builder1 = UplinkMsg.newBuilder();
        EntityDataProto.Builder entityDataBuilder = EntityDataProto.newBuilder();
        entityDataBuilder.setPostTelemetryMsg(JsonConverter.convertToTelemetryProto(data, System.currentTimeMillis()));
        entityDataBuilder.setEntityType(device.getId().getEntityType().name());
        entityDataBuilder.setEntityIdMSB(device.getUuidId().getMostSignificantBits());
        entityDataBuilder.setEntityIdLSB(device.getUuidId().getLeastSignificantBits());
        builder1.addEntityData(entityDataBuilder.build());
        edgeImitator.sendUplinkMsg(builder1.build());

        JsonObject attributesData = new JsonObject();
        String attributesKey = "test_attr";
        String attributesValue = "test_value";
        attributesData.addProperty(attributesKey, attributesValue);
        UplinkMsg.Builder builder2 = UplinkMsg.newBuilder();
        EntityDataProto.Builder entityDataBuilder2 = EntityDataProto.newBuilder();
        entityDataBuilder2.setEntityType(device.getId().getEntityType().name());
        entityDataBuilder2.setEntityIdMSB(device.getId().getId().getMostSignificantBits());
        entityDataBuilder2.setEntityIdLSB(device.getId().getId().getLeastSignificantBits());
        entityDataBuilder2.setAttributesUpdatedMsg(JsonConverter.convertToAttributesProto(attributesData));
        entityDataBuilder2.setPostAttributeScope(DataConstants.SERVER_SCOPE);
        builder2.addEntityData(entityDataBuilder2.build());
        edgeImitator.sendUplinkMsg(builder2.build());
        edgeImitator.waitForResponses();

        Thread.sleep(1000);
        Map<String, List<Map<String, String>>> timeseries = doGetAsync("/api/plugins/telemetry/DEVICE/" + device.getUuidId() + "/values/timeseries?keys=" + timeseriesKey, Map.class);
        Assert.assertTrue(timeseries.containsKey(timeseriesKey));
        Assert.assertEquals(1, timeseries.get(timeseriesKey).size());
        Assert.assertEquals(timeseriesValue, timeseries.get(timeseriesKey).get(0).get("value"));

        List<Map<String, String>> attributes = doGetAsync("/api/plugins/telemetry/DEVICE/" + device.getId() + "/values/attributes/" + DataConstants.SERVER_SCOPE, List.class);
        Assert.assertEquals(1, attributes.size());
        Assert.assertEquals(attributes.get(0).get("key"), attributesKey);
        Assert.assertEquals(attributes.get(0).get("value"), attributesValue);

    }

    private void sendRuleChainMetadataRequest() throws Exception {
        RuleChainId edgeRootRuleChainId = edge.getRootRuleChainId();

        UplinkMsg.Builder builder = UplinkMsg.newBuilder();
        RuleChainMetadataRequestMsg.Builder ruleChainMetadataRequestMsgBuilder = RuleChainMetadataRequestMsg.newBuilder();
        ruleChainMetadataRequestMsgBuilder.setRuleChainIdMSB(edgeRootRuleChainId.getId().getMostSignificantBits());
        ruleChainMetadataRequestMsgBuilder.setRuleChainIdLSB(edgeRootRuleChainId.getId().getLeastSignificantBits());
        builder.addRuleChainMetadataRequestMsg(ruleChainMetadataRequestMsgBuilder.build());
        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);
        edgeImitator.sendUplinkMsg(builder.build());
        edgeImitator.waitForResponses();
        edgeImitator.waitForMessages();

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof RuleChainMetadataUpdateMsg);
        RuleChainMetadataUpdateMsg ruleChainMetadataUpdateMsg = (RuleChainMetadataUpdateMsg) latestMessage;
        Assert.assertEquals(ruleChainMetadataUpdateMsg.getRuleChainIdMSB(), edgeRootRuleChainId.getId().getMostSignificantBits());
        Assert.assertEquals(ruleChainMetadataUpdateMsg.getRuleChainIdLSB(), edgeRootRuleChainId.getId().getLeastSignificantBits());
    }

    private void sendUserCredentialsRequest() throws Exception {
        UserId userId = edgeImitator.getUserId();

        UplinkMsg.Builder builder = UplinkMsg.newBuilder();
        UserCredentialsRequestMsg.Builder userCredentialsRequestMsgBuilder = UserCredentialsRequestMsg.newBuilder();
        userCredentialsRequestMsgBuilder.setUserIdMSB(userId.getId().getMostSignificantBits());
        userCredentialsRequestMsgBuilder.setUserIdLSB(userId.getId().getLeastSignificantBits());
        builder.addUserCredentialsRequestMsg(userCredentialsRequestMsgBuilder.build());
        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);
        edgeImitator.sendUplinkMsg(builder.build());
        edgeImitator.waitForResponses();
        edgeImitator.waitForMessages();

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof UserCredentialsUpdateMsg);
        UserCredentialsUpdateMsg userCredentialsUpdateMsg = (UserCredentialsUpdateMsg) latestMessage;
        Assert.assertEquals(userCredentialsUpdateMsg.getUserIdMSB(), userId.getId().getMostSignificantBits());
        Assert.assertEquals(userCredentialsUpdateMsg.getUserIdLSB(), userId.getId().getLeastSignificantBits());
    }

    private void sendDeviceCredentialsRequest() throws Exception {
        List<Device> edgeDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100)).getData();
        Optional<Device> foundDevice = edgeDevices.stream().filter(device1 -> device1.getName().equals("Edge Device 1")).findAny();
        Assert.assertTrue(foundDevice.isPresent());
        Device device = foundDevice.get();

        DeviceCredentials deviceCredentials = doGet("/api/device/" + device.getId().getId().toString() + "/credentials", DeviceCredentials.class);

        UplinkMsg.Builder builder = UplinkMsg.newBuilder();
        DeviceCredentialsRequestMsg.Builder deviceCredentialsRequestMsgBuilder = DeviceCredentialsRequestMsg.newBuilder();
        deviceCredentialsRequestMsgBuilder.setDeviceIdMSB(device.getUuidId().getMostSignificantBits());
        deviceCredentialsRequestMsgBuilder.setDeviceIdLSB(device.getUuidId().getLeastSignificantBits());
        builder.addDeviceCredentialsRequestMsg(deviceCredentialsRequestMsgBuilder.build());

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);
        edgeImitator.sendUplinkMsg(builder.build());
        edgeImitator.waitForResponses();
        edgeImitator.waitForMessages();

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DeviceCredentialsUpdateMsg);
        DeviceCredentialsUpdateMsg deviceCredentialsUpdateMsg = (DeviceCredentialsUpdateMsg) latestMessage;
        Assert.assertEquals(deviceCredentialsUpdateMsg.getDeviceIdMSB(), device.getUuidId().getMostSignificantBits());
        Assert.assertEquals(deviceCredentialsUpdateMsg.getDeviceIdLSB(), device.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(deviceCredentialsUpdateMsg.getCredentialsType(), deviceCredentials.getCredentialsType().name());
        Assert.assertEquals(deviceCredentialsUpdateMsg.getCredentialsId(), deviceCredentials.getCredentialsId());
    }

    private void sendDeleteDeviceOnEdge() throws Exception {
        List<Device> edgeDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100)).getData();
        Optional<Device> foundDevice = edgeDevices.stream().filter(device1 -> device1.getName().equals("Edge Device 2")).findAny();
        Assert.assertTrue(foundDevice.isPresent());
        Device device = foundDevice.get();
        UplinkMsg.Builder builder = UplinkMsg.newBuilder();
        DeviceUpdateMsg.Builder deviceDeleteMsgBuilder = DeviceUpdateMsg.newBuilder();
        deviceDeleteMsgBuilder.setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE);
        deviceDeleteMsgBuilder.setIdMSB(device.getId().getId().getMostSignificantBits());
        deviceDeleteMsgBuilder.setIdLSB(device.getId().getId().getLeastSignificantBits());
        builder.addDeviceUpdateMsg(deviceDeleteMsgBuilder.build());
        edgeImitator.expectResponsesAmount(1);
        edgeImitator.sendUplinkMsg(builder.build());
        edgeImitator.waitForResponses();
        device = doGet("/api/device/" + device.getId().getId().toString(), Device.class);
        Assert.assertNotNull(device);
        edgeDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {
                }, new TextPageLink(100)).getData();
        Assert.assertFalse(edgeDevices.contains(device));
    }

    private void installation() throws Exception {
        edge = doPost("/api/edge", constructEdge("Test Edge", "test"), Edge.class);

        Device device = new Device();
        device.setName("Edge Device 1");
        device.setType("test");
        Device savedDevice = doPost("/api/device", device, Device.class);
        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/device/" + savedDevice.getId().getId().toString(), Device.class);

        Asset asset = new Asset();
        asset.setName("Edge Asset 1");
        asset.setType("test");
        Asset savedAsset = doPost("/api/asset", asset, Asset.class);
        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/asset/" + savedAsset.getId().getId().toString(), Asset.class);
    }

    private void uninstallation() throws Exception {

        TimePageData<Device> pageDataDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100));
        for (Device device: pageDataDevices.getData()) {
            doDelete("/api/device/" + device.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        TimePageData<Asset> pageDataAssets = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/assets?",
                new TypeReference<TimePageData<Asset>>() {}, new TextPageLink(100));
        for (Asset asset: pageDataAssets.getData()) {
            doDelete("/api/asset/" + asset.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        doDelete("/api/edge/" + edge.getId().getId().toString())
                .andExpect(status().isOk());
    }

    private EdgeEvent constructEdgeEvent(TenantId tenantId, EdgeId edgeId, ActionType edgeEventAction, UUID entityId, EdgeEventType edgeEventType, JsonNode entityBody) {
        EdgeEvent edgeEvent = new EdgeEvent();
        edgeEvent.setEdgeId(edgeId);
        edgeEvent.setTenantId(tenantId);
        edgeEvent.setAction(edgeEventAction.name());
        edgeEvent.setEntityId(entityId);
        edgeEvent.setType(edgeEventType);
        edgeEvent.setBody(entityBody);
        return edgeEvent;
    }
}
