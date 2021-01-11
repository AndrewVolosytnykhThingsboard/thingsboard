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
package org.thingsboard.integration.rpc;

import com.google.common.io.Resources;
import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.integration.exception.IntegrationConnectionException;
import org.thingsboard.integration.storage.EventStorage;
import org.thingsboard.server.gen.integration.ConnectRequestMsg;
import org.thingsboard.server.gen.integration.ConnectResponseCode;
import org.thingsboard.server.gen.integration.ConnectResponseMsg;
import org.thingsboard.server.gen.integration.ConverterConfigurationProto;
import org.thingsboard.server.gen.integration.DeviceDownlinkDataProto;
import org.thingsboard.server.gen.integration.IntegrationConfigurationProto;
import org.thingsboard.server.gen.integration.IntegrationTransportGrpc;
import org.thingsboard.server.gen.integration.MessageType;
import org.thingsboard.server.gen.integration.RequestMsg;
import org.thingsboard.server.gen.integration.ResponseMsg;
import org.thingsboard.server.gen.integration.UplinkMsg;
import org.thingsboard.server.gen.integration.UplinkResponseMsg;

import javax.net.ssl.SSLException;
import java.io.File;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
@Slf4j
public class IntegrationGrpcClient implements IntegrationRpcClient {

    @Value("${rpc.host}")
    private String rpcHost;
    @Value("${rpc.port}")
    private int rpcPort;
    @Value("${rpc.timeout}")
    private int timeoutSecs;
    @Value("${rpc.ssl.enabled}")
    private boolean sslEnabled;
    @Value("${rpc.ssl.cert}")
    private String certResource;

    @Autowired
    private EventStorage eventStorage;

    private ManagedChannel channel;
    private StreamObserver<RequestMsg> inputStream;
    private CountDownLatch latch;

    @Override
    public void connect(String integrationKey, String integrationSecret, Consumer<IntegrationConfigurationProto> onIntegrationUpdate
            , Consumer<ConverterConfigurationProto> onConverterUpdate, Consumer<DeviceDownlinkDataProto> onDownlink, Consumer<Exception> onError) {
        NettyChannelBuilder builder = NettyChannelBuilder
                .forAddress(rpcHost, rpcPort)
                .usePlaintext();
        if (sslEnabled) {
            try {
                builder.sslContext(GrpcSslContexts.forClient().trustManager(new File(Resources.getResource(certResource).toURI())).build());
            } catch (URISyntaxException | SSLException e) {
                log.error("Failed to initialize channel!", e);
                throw new RuntimeException(e);
            }
        }
        channel = builder.build();
        IntegrationTransportGrpc.IntegrationTransportStub stub = IntegrationTransportGrpc.newStub(channel);
        log.info("[{}] Sending a connect request to the TB!", integrationKey);
        this.inputStream = stub.handleMsgs(initOutputStream(integrationKey, onIntegrationUpdate, onConverterUpdate, onDownlink, onError));
        this.inputStream.onNext(RequestMsg.newBuilder()
                .setMessageType(MessageType.CONNECT_RPC_MESSAGE)
                .setConnectRequestMsg(ConnectRequestMsg.newBuilder().setIntegrationRoutingKey(integrationKey).setIntegrationSecret(integrationSecret).build())
                .build());
    }

    private StreamObserver<ResponseMsg> initOutputStream(String integrationKey, Consumer<IntegrationConfigurationProto> onIntegrationUpdate, Consumer<ConverterConfigurationProto> onConverterUpdate, Consumer<DeviceDownlinkDataProto> onDownlink, Consumer<Exception> onError) {
        return new StreamObserver<ResponseMsg>() {
            @Override
            public void onNext(ResponseMsg responseMsg) {
                if (responseMsg.hasConnectResponseMsg()) {
                    ConnectResponseMsg connectResponseMsg = responseMsg.getConnectResponseMsg();
                    if (connectResponseMsg.getResponseCode().equals(ConnectResponseCode.ACCEPTED)) {
                        log.info("[{}] Configuration received: {}", integrationKey, connectResponseMsg.getConfiguration());
                        onIntegrationUpdate.accept(connectResponseMsg.getConfiguration());
                    } else {
                        log.error("[{}] Failed to establish the connection! Code: {}. Error message: {}.", integrationKey, connectResponseMsg.getResponseCode(), connectResponseMsg.getErrorMsg());
                        try {
                            IntegrationGrpcClient.this.disconnect();
                        } catch (InterruptedException e) {
                            log.error("[{}] Got interruption during disconnect!", integrationKey, e);
                        }
                        onError.accept(new IntegrationConnectionException("Failed to establish the connection! Response code: " + connectResponseMsg.getResponseCode().name()));
                    }
                } else if (responseMsg.hasUplinkResponseMsg()) {
                    UplinkResponseMsg msg = responseMsg.getUplinkResponseMsg();
                    if (msg.getSuccess()) {
                        log.debug("[{}] Msg has been processed successfully! {}", integrationKey, msg);
                    } else {
                        log.error("[{}] Msg processing failed! Error msg: {}", integrationKey, msg.getErrorMsg());
                    }
                    latch.countDown();
                } else if (responseMsg.hasIntegrationUpdateMsg()) {
                    log.info("[{}] Configuration updated: {}", integrationKey, responseMsg.getIntegrationUpdateMsg().getConfiguration());
                    onIntegrationUpdate.accept(responseMsg.getIntegrationUpdateMsg().getConfiguration());
                } else if (responseMsg.hasConverterUpdateMsg()) {
                    log.info("[{}] Converter configuration updated: {}", integrationKey, responseMsg.getConverterUpdateMsg().getConfiguration());
                    onConverterUpdate.accept(responseMsg.getConverterUpdateMsg().getConfiguration());
                } else if (responseMsg.hasDownlinkMsg()) {
                    log.debug("[{}] Downlink message received for device {}", integrationKey, responseMsg.getDownlinkMsg().getDeviceData().getDeviceName());
                    onDownlink.accept(responseMsg.getDownlinkMsg().getDeviceData());
                }
            }

            @Override
            public void onError(Throwable t) {
                log.debug("[{}] The rpc session received an error!", integrationKey, t);
                onError.accept(new RuntimeException(t));
            }

            @Override
            public void onCompleted() {
                log.debug("[{}] The rpc session was closed!", integrationKey);
            }
        };
    }

    @Override
    public void disconnect() throws InterruptedException {
        try {
            inputStream.onCompleted();
        } catch (Exception e) {
        }
        if (channel != null) {
            channel.shutdown().awaitTermination(timeoutSecs, TimeUnit.SECONDS);
        }
    }

    @Override
    public void handleMsgs() throws InterruptedException {
        List<UplinkMsg> uplinkMsgList = eventStorage.readCurrentBatch();
        latch = new CountDownLatch(uplinkMsgList.size());
        for (UplinkMsg msg : uplinkMsgList) {
            this.inputStream.onNext(RequestMsg.newBuilder()
                    .setMessageType(MessageType.UPLINK_RPC_MESSAGE)
                    .setUplinkMsg(msg)
                    .build());
        }
        boolean success = latch.await(10, TimeUnit.SECONDS);
        if (!success) {
            log.warn("Failed to deliver the batch: {}", uplinkMsgList);
        }
        if (success && !uplinkMsgList.isEmpty()) {
            eventStorage.discardCurrentBatch();
        } else {
            eventStorage.sleep();
        }
    }
}
