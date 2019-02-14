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
package org.thingsboard.server.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Base64Utils;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.Event;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.service.converter.DownlinkData;
import org.thingsboard.server.service.converter.TBDownlinkDataConverter;
import org.thingsboard.server.service.converter.TBUplinkDataConverter;
import org.thingsboard.server.service.converter.UplinkData;
import org.thingsboard.server.service.converter.UplinkMetaData;
import org.thingsboard.server.service.integration.msg.IntegrationDownlinkMsg;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by ashvayka on 25.12.17.
 */
@Slf4j
public abstract class AbstractIntegration<T> implements ThingsboardPlatformIntegration<T> {

    protected final ObjectMapper mapper = new ObjectMapper();
    protected Integration configuration;
    protected TBUplinkDataConverter uplinkConverter;
    protected TBDownlinkDataConverter downlinkConverter;
    protected UplinkMetaData metadataTemplate;
    protected IntegrationStatistics integrationStatistics;

    private ReentrantLock deviceCreationLock = new ReentrantLock();

    @Override
    public void init(TbIntegrationInitParams params) throws Exception {
        this.configuration = params.getConfiguration();
        this.uplinkConverter = params.getUplinkConverter();
        this.downlinkConverter = params.getDownlinkConverter();
        Map<String, String> mdMap = new HashMap<>();
        mdMap.put("integrationName", configuration.getName());
        JsonNode metadata = configuration.getConfiguration().get("metadata");
        for (Iterator<Map.Entry<String, JsonNode>> it = metadata.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> md = it.next();
            mdMap.put(md.getKey(), md.getValue().asText());
        }
        this.metadataTemplate = new UplinkMetaData(getUplinkContentType(), mdMap);
        this.integrationStatistics = new IntegrationStatistics();
    }

    protected String getUplinkContentType() {
        return "JSON";
    }

    @Override
    public void update(TbIntegrationInitParams params) throws Exception {
        init(params);
    }

    @Override
    public Integration getConfiguration() {
        return configuration;
    }

    @Override
    public void validateConfiguration(Integration configuration, boolean allowLocalNetworkHosts) {
        if (configuration == null || configuration.getConfiguration() == null) {
            throw new IllegalArgumentException("Integration configuration is empty!");
        }
        doValidateConfiguration(configuration.getConfiguration(), allowLocalNetworkHosts);
    }

    @Override
    public void destroy() {

    }

    @Override
    public void onDownlinkMsg(IntegrationContext context, IntegrationDownlinkMsg msg) {

    }

    @Override
    public IntegrationStatistics popStatistics() {
        IntegrationStatistics statistics = this.integrationStatistics;
        this.integrationStatistics = new IntegrationStatistics();
        return statistics;
    }

    protected void doValidateConfiguration(JsonNode configuration, boolean allowLocalNetworkHosts) {

    }

    protected static boolean isLocalNetworkHost(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() ||
                    address.isSiteLocalAddress()) {
                return true;
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unable to resolve provided hostname: " + host);
        }
        return false;
    }

    protected Device processUplinkData(IntegrationContext context, UplinkData data) {
        Device device = getOrCreateDevice(context, data);

        UUID sessionId = UUID.randomUUID();
        TransportProtos.SessionInfoProto sessionInfo = TransportProtos.SessionInfoProto.newBuilder()
                .setSessionIdMSB(sessionId.getMostSignificantBits())
                .setSessionIdLSB(sessionId.getLeastSignificantBits())
                .setTenantIdMSB(device.getTenantId().getId().getMostSignificantBits())
                .setTenantIdLSB(device.getTenantId().getId().getLeastSignificantBits())
                .setDeviceIdMSB(device.getId().getId().getMostSignificantBits())
                .setDeviceIdLSB(device.getId().getId().getLeastSignificantBits())
                .build();

        if (data.getTelemetry() != null) {
            context.getIntegrationService().process(sessionInfo, data.getTelemetry(), null);
        }

        if (data.getAttributesUpdate() != null) {
            context.getIntegrationService().process(sessionInfo, data.getAttributesUpdate(), null);
        }

        if (data.getAttributesRequest() != null) {
            context.getIntegrationService().process(sessionInfo, data.getAttributesRequest(), null);
        }

        return device;
    }

    private Device getOrCreateDevice(IntegrationContext context, UplinkData data) {
        Device device = context.getDeviceService().findDeviceByTenantIdAndName(configuration.getTenantId(), data.getDeviceName());
        if (device == null) {
            deviceCreationLock.lock();
            try {
                device = context.getDeviceService().findDeviceByTenantIdAndName(configuration.getTenantId(), data.getDeviceName());
                if (device == null) {
                    device = new Device();
                    device.setName(data.getDeviceName());
                    device.setType(data.getDeviceType());
                    device.setTenantId(configuration.getTenantId());
                    device = context.getDeviceService().saveDevice(device);
                    EntityRelation relation = new EntityRelation();
                    relation.setFrom(configuration.getId());
                    relation.setTo(device.getId());
                    relation.setTypeGroup(RelationTypeGroup.COMMON);
                    relation.setType(EntityRelation.INTEGRATION_TYPE);
                    context.getRelationService().saveRelation(configuration.getTenantId(), relation);
                    context.getActorService().onDeviceAdded(device);
                }
            } finally {
                deviceCreationLock.unlock();
            }
        }
        return device;
    }

    protected void persistDebug(IntegrationContext context, String type, String messageType, String message, String status, Exception exception) {
        Event event = new Event();
        event.setTenantId(configuration.getTenantId());
        event.setEntityId(configuration.getId());
        event.setType(DataConstants.DEBUG_INTEGRATION);

        ObjectNode node = mapper.createObjectNode()
                .put("server", context.getDiscoveryService().getCurrentServer().getServerAddress().toString())
                .put("type", type)
                .put("messageType", messageType)
                .put("message", message)
                .put("status", status);

        if (exception != null) {
            node = node.put("error", toString(exception));
        }

        event.setBody(node);
        context.getEventService().save(event);
    }

    private String toString(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    protected List<UplinkData> convertToUplinkDataList(IntegrationContext context, byte[] data, UplinkMetaData md) throws Exception {
        try {
            return this.uplinkConverter.convertUplink(context.getConverterContext(), data, md);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("[{}][{}] Failed to apply uplink data converter function for data: {} and metadata: {}", configuration.getId(), configuration.getName(), Base64Utils.encodeToString(data), md);
            }
            throw e;
        }
    }

    protected void reportDownlinkOk(IntegrationContext context, DownlinkData data) {
        integrationStatistics.incMessagesProcessed();
        if (configuration.isDebugMode()) {
            try {
                ObjectNode json = mapper.createObjectNode();
                if (data.getMetadata() != null && !data.getMetadata().isEmpty()) {
                    json.set("metadata", mapper.valueToTree(data.getMetadata()));
                }
                json.set("payload", getDownlinkPayloadJson(data));
                persistDebug(context, "Downlink", "JSON", mapper.writeValueAsString(json), downlinkConverter != null ? "OK" : "FAILURE", null);
            } catch (Exception e) {
                log.warn("Failed to persist debug message", e);
            }
        }
    }

    protected void reportDownlinkError(IntegrationContext context, TbMsg msg, String status, Exception exception) {
        if (!status.equals("OK")) {
            integrationStatistics.incErrorsOccurred();
            if (log.isDebugEnabled()) {
                log.debug("[{}][{}] Failed to apply downlink data converter function for data: {} and metadata: {}", configuration.getId(), configuration.getName(), msg.getData(), msg.getMetaData());
            }
            if (configuration.isDebugMode()) {
                try {
                    persistDebug(context, "Downlink", "JSON", mapper.writeValueAsString(msg), status, exception);
                } catch (Exception e) {
                    log.warn("Failed to persist debug message", e);
                }
            }
        }
    }

    protected JsonNode getDownlinkPayloadJson(DownlinkData data) throws IOException {
        String contentType = data.getContentType();
        if ("JSON".equals(contentType)) {
            return mapper.readTree(data.getData());
        } else if ("TEXT".equals(contentType)) {
            return new TextNode(new String(data.getData(), StandardCharsets.UTF_8));
        } else { //BINARY
            return new TextNode(Base64Utils.encodeToString(data.getData()));
        }
    }

    protected <T> void logDownlink(IntegrationContext context, String updateType, T msg) {
        if (configuration.isDebugMode()) {
            try {
                persistDebug(context, updateType, "JSON", mapper.writeValueAsString(msg), downlinkConverter != null ? "OK" : "FAILURE", null);
            } catch (Exception e) {
                log.warn("Failed to persist debug message", e);
            }
        }
    }

}
