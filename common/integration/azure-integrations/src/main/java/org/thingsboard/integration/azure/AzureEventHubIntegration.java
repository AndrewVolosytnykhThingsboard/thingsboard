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
package org.thingsboard.integration.azure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.microsoft.azure.eventhubs.ConnectionStringBuilder;
import com.microsoft.azure.eventhubs.EventData;
import com.microsoft.azure.eventhubs.EventHubClient;
import com.microsoft.azure.eventhubs.EventHubException;
import com.microsoft.azure.eventhubs.EventHubRuntimeInformation;
import com.microsoft.azure.eventhubs.PartitionReceiver;
import com.microsoft.azure.eventhubs.impl.EventPositionImpl;
import com.microsoft.azure.sdk.iot.service.DeliveryAcknowledgement;
import com.microsoft.azure.sdk.iot.service.IotHubServiceClientProtocol;
import com.microsoft.azure.sdk.iot.service.Message;
import com.microsoft.azure.sdk.iot.service.ServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Base64Utils;
import org.thingsboard.integration.api.AbstractIntegration;
import org.thingsboard.integration.api.IntegrationContext;
import org.thingsboard.integration.api.TbIntegrationInitParams;
import org.thingsboard.integration.api.data.DownlinkData;
import org.thingsboard.integration.api.data.IntegrationDownlinkMsg;
import org.thingsboard.integration.api.data.IntegrationMetaData;
import org.thingsboard.integration.api.data.UplinkData;
import org.thingsboard.integration.api.data.UplinkMetaData;
import org.thingsboard.server.common.msg.TbMsg;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Slf4j
public class AzureEventHubIntegration extends AbstractIntegration<AzureEventHubIntegrationMsg> {

    private IntegrationContext context;
    private EventHubClient ehClient;
    private ServiceClient serviceClient;
    private List<PartitionReceiver> receivers;
    private volatile boolean started = false;
    private ExecutorService executorService;
    private ScheduledExecutorService clientExecutor;
    private List<Future> receiverFutures;

    @Override
    public void init(TbIntegrationInitParams params) throws Exception {
        super.init(params);
        if (!this.configuration.isEnabled()) {
            return;
        }
        clientExecutor = Executors.newSingleThreadScheduledExecutor();

        this.context = params.getContext();
        AzureEventHubClientConfiguration clientConfiguration = mapper.readValue(
                mapper.writeValueAsString(configuration.getConfiguration().get("clientConfiguration")),
                AzureEventHubClientConfiguration.class);
        ehClient = initClient(clientConfiguration);
        EventHubRuntimeInformation runtimeInfo = ehClient.getRuntimeInformation().get();
        receivers = new ArrayList<>();
        for (String partitionId : runtimeInfo.getPartitionIds()) {
            PartitionReceiver receiver = ehClient.createReceiverSync(
                    EventHubClient.DEFAULT_CONSUMER_GROUP_NAME,
                    partitionId,
                    EventPositionImpl.fromEndOfStream());
            receiver.setReceiveTimeout(Duration.ofSeconds(20));
            receivers.add(receiver);
        }
        if (downlinkConverter != null) {
            serviceClient = initServiceClient(clientConfiguration);
        }
        started = true;
        executorService = Executors.newFixedThreadPool(receivers.size());
        receiverFutures = new ArrayList<>();
        receiverFutures.addAll(receivers.stream().map(receiver -> executorService.submit(new ReceiverRunnable(receiver))).collect(Collectors.toList()));
    }

    @Override
    public void update(TbIntegrationInitParams params) throws Exception {
        destroy();
        init(params);
    }

    @Override
    public void destroy() {
        started = false;
        if (receiverFutures != null) {
            for (Future receiverFuture : receiverFutures) {
                receiverFuture.cancel(true);
            }
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
        if (receivers != null) {
            receivers.forEach(PartitionReceiver::close);
        }
        if (ehClient != null) {
            ehClient.close();
        }
        if (serviceClient != null) {
            serviceClient.closeAsync();
        }
        if (clientExecutor != null) {
            try {
                clientExecutor.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.error("Failed to stop Event Hub Client!!!");
            }
        }
    }

    class ReceiverRunnable implements Runnable {

        private final PartitionReceiver receiver;

        ReceiverRunnable(PartitionReceiver receiver) {
            this.receiver = receiver;
        }

        @Override
        public void run() {
            while (started) {
                try {
                    Iterable<EventData> events = this.receiver.receiveSync(10);
                    if (events != null) {
                        for (EventData event : events) {
                            process(new AzureEventHubIntegrationMsg(event));
                        }
                    }
                } catch (EventHubException e) {
                    log.error("Failed to receive events from Event Hub", e);
                }
            }
        }
    }

    @Override
    public void process(AzureEventHubIntegrationMsg msg) {
        String status = "OK";
        Exception exception = null;
        try {
            doProcess(context, msg);
            integrationStatistics.incMessagesProcessed();
        } catch (Exception e) {
            log.debug("Failed to apply data converter function: {}", e.getMessage(), e);
            exception = e;
            status = "ERROR";
        }
        if (!status.equals("OK")) {
            integrationStatistics.incErrorsOccurred();
        }
        if (configuration.isDebugMode()) {
            try {
                persistDebug(context, "Uplink", getUplinkContentType(), mapper.writeValueAsString(msg.toJson()), status, exception);
            } catch (Exception e) {
                log.warn("Failed to persist debug message", e);
            }
        }
    }

    @Override
    public void onDownlinkMsg(IntegrationDownlinkMsg downlink) {
        TbMsg msg = downlink.getTbMsg();
        logDownlink(context, "Downlink: " + msg.getType(), msg);
        if (downlinkConverter != null) {
            processDownLinkMsg(context, msg);
        }
    }

    protected void processDownLinkMsg(IntegrationContext context, TbMsg msg) {
        String status = "OK";
        Exception exception = null;
        try {
            if (doProcessDownLinkMsg(context, msg)) {
                integrationStatistics.incMessagesProcessed();
            }
        } catch (Exception e) {
            log.warn("Failed to process downLink message", e);
            exception = e;
            status = "ERROR";
        }
        reportDownlinkError(context, msg, status, exception);
    }

    private void doProcess(IntegrationContext context, AzureEventHubIntegrationMsg msg) throws Exception {
        Map<String, String> mdMap = new HashMap<>(metadataTemplate.getKvMap());
        msg.getSystemProperties().forEach(
                (key, value) -> {
                    if (value != null) {
                        mdMap.put("sysProp:" + key, value.toString());
                    }
                }
        );
        List<UplinkData> uplinkDataList = convertToUplinkDataList(context, msg.getPayload(), new UplinkMetaData(getUplinkContentType(), mdMap));
        if (uplinkDataList != null) {
            for (UplinkData data : uplinkDataList) {
                processUplinkData(context, data);
                log.info("[{}] Processing uplink data", data);
            }
        }
    }

    private boolean doProcessDownLinkMsg(IntegrationContext context, TbMsg msg) throws Exception {
        if (serviceClient == null) {
            return false;
        }
        Map<String, List<Message>> deviceIdToMessage = convertDownLinkMsg(context, msg);
        for (Map.Entry<String, List<Message>> messageEntry : deviceIdToMessage.entrySet()) {
            for (Message message : messageEntry.getValue()) {
                logEventHubDownlink(context, message, messageEntry.getKey(), message.getProperties().get("content-type"));
                serviceClient.sendAsync(messageEntry.getKey(), message);
            }
        }
        return !deviceIdToMessage.isEmpty();
    }

    private Map<String, List<Message>> convertDownLinkMsg(IntegrationContext context, TbMsg msg) throws Exception {
        Map<String, List<Message>> deviceIdToMessage = new HashMap<>();
        Map<String, String> mdMap = new HashMap<>(metadataTemplate.getKvMap());
        List<DownlinkData> result = downlinkConverter.convertDownLink(context.getDownlinkConverterContext(), Collections.singletonList(msg), new IntegrationMetaData(mdMap));
        for (DownlinkData data : result) {
            if (!data.isEmpty()) {
                String deviceId = data.getMetadata().get("deviceId");
                if (StringUtils.isEmpty(deviceId)) {
                    continue;
                }
                Message message = new Message(data.getData());
                message.setDeliveryAcknowledgement(DeliveryAcknowledgement.Full);
                message.setMessageId(UUID.randomUUID().toString());
                message.setTo(deviceId);
                message.getProperties().putAll(data.getMetadata());
                message.getProperties().put("content-type", data.getContentType());
                deviceIdToMessage.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(message);
            }
        }
        return deviceIdToMessage;
    }

    private EventHubClient initClient(AzureEventHubClientConfiguration clientConfiguration) throws Exception {
        ConnectionStringBuilder connStr = new ConnectionStringBuilder();
        connStr.setNamespaceName(clientConfiguration.getNamespaceName());
        connStr.setEventHubName(clientConfiguration.getEventHubName());
        connStr.setSasKeyName(clientConfiguration.getSasKeyName());
        connStr.setSasKey(clientConfiguration.getSasKey());

        CompletableFuture<EventHubClient> ehClientFuture = EventHubClient.createFromConnectionString(connStr.toString(), clientExecutor);
        EventHubClient ehClient;
        try {
            ehClient = ehClientFuture.get(clientConfiguration.getConnectTimeoutSec(), TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            ehClientFuture.cancel(true);
            throw new RuntimeException(String.format("Failed to connect to the Event Hub Endpoint %s within specified timeout.",
                    clientConfiguration.getEventHubName()));
        }
        return ehClient;
    }

    private ServiceClient initServiceClient(AzureEventHubClientConfiguration clientConfiguration) throws Exception {
        if (StringUtils.isEmpty(clientConfiguration.getIotHubName())) {
            return null;
        }
        String iotHubConnectionString =
                String.format("HostName=%s.azure-devices.net;SharedAccessKeyName=%s;SharedAccessKey=%s", clientConfiguration.getIotHubName(),
                        clientConfiguration.getSasKeyName(), clientConfiguration.getSasKey());
        ServiceClient serviceClient = ServiceClient.createFromConnectionString(iotHubConnectionString, IotHubServiceClientProtocol.AMQPS);
        CompletableFuture<Void> serviceClientFuture = serviceClient.openAsync();
        try {
            serviceClientFuture.get(clientConfiguration.getConnectTimeoutSec(), TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            serviceClientFuture.cancel(true);
            throw new RuntimeException(String.format("Failed to connect to the IoT Hub %s within specified timeout.",
                    clientConfiguration.getIotHubName()));
        }
        return serviceClient;
    }

    private void logEventHubDownlink(IntegrationContext context, Message message, String deviceId, String contentType) {
        if (configuration.isDebugMode()) {
            try {
                ObjectNode json = mapper.createObjectNode();
                json.put("deviceId", deviceId);
                json.set("payload", getDownlinkPayloadJson(message, contentType));
                json.set("properties", mapper.valueToTree(message.getProperties()));
                persistDebug(context, "Downlink", "JSON", mapper.writeValueAsString(json), downlinkConverter != null ? "OK" : "FAILURE", null);
            } catch (Exception e) {
                log.warn("Failed to persist debug message", e);
            }
        }
    }

    private JsonNode getDownlinkPayloadJson(Message message, String contentType) throws IOException {
        if ("JSON".equals(contentType)) {
            return mapper.readTree(message.getBytes());
        } else if ("TEXT".equals(contentType)) {
            return new TextNode(new String(message.getBytes(), StandardCharsets.UTF_8));
        } else { //BINARY
            return new TextNode(Base64Utils.encodeToString(message.getBytes()));
        }
    }

}
