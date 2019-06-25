/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 * <p>
 * Copyright © 2016-2019 ThingsBoard, Inc. All Rights Reserved.
 * <p>
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * <p>
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 * <p>
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 * <p>
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
package org.thingsboard.integration.rpc;

import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.server.gen.integration.ConnectRequestMsg;
import org.thingsboard.server.gen.integration.ConnectResponseCode;
import org.thingsboard.server.gen.integration.ConnectResponseMsg;
import org.thingsboard.server.gen.integration.IntegrationConfigurationProto;
import org.thingsboard.server.gen.integration.IntegrationTransportGrpc;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
@Slf4j
public class IntegrationGrpcClient implements IntegrationRpcClient {

    @Value("${rpc.client.host}")
    private String rpcHost;
    @Value("${rpc.client.port}")
    private int rpcPort;
    @Value("${rpc.client.port}")
    private int timeoutSecs;
    @Value("${rpc.client.cert}")
    private String certResource;

    private ManagedChannel channel;
    private IntegrationTransportGrpc.IntegrationTransportStub stub;

    @Override
    public void connect(String integrationKey, String integrationSecret, Consumer<IntegrationConfigurationProto> onSuccess, Consumer<Exception> onError) {
        try {
            channel = NettyChannelBuilder
                    .forAddress(rpcHost, rpcPort)
//                    .sslContext(GrpcSslContexts.forClient().trustManager(new File(Resources.getResource(certResource).toURI())).build())
                    .usePlaintext()
                    .build();
        } catch (Exception e) {
            log.error("Failed to initialize channel!", e);
            throw new RuntimeException(e);
        }
        stub = IntegrationTransportGrpc.newStub(channel);
        log.info("[{}] Sending a connect request to the TB!", integrationKey);
        StreamObserver<ConnectResponseMsg> responseObserver = new StreamObserver<ConnectResponseMsg>() {
            @Override
            public void onNext(ConnectResponseMsg value) {
                if (value.getResponseCode().equals(ConnectResponseCode.ACCEPTED)) {
                    log.info("[{}]: {}", integrationKey, value.getConfiguration());
                    onSuccess.accept(value.getConfiguration());
                } else {
                    log.error("[{}] Failed to establish the connection! Code: {}. Error message: {}.", integrationKey, value.getResponseCode(), value.getErrorMsg());
                    //TODO: custom exception type
                    onError.accept(new RuntimeException(value.getResponseCode().name()));
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("[{}] Failed to establish the connection!", integrationKey, t);
                onError.accept(new RuntimeException(t));
            }


            @Override
            public void onCompleted() {
                log.info("[{}] Integration connection completed successfully!", integrationKey);
            }
        };
        stub.connect(ConnectRequestMsg.newBuilder().setIntegrationRoutingKey(integrationKey).setIntegrationSecret(integrationSecret).build(), responseObserver);
    }

    @Override
    public void disconnect() throws InterruptedException {
        if (channel != null) {
            channel.shutdown().awaitTermination(timeoutSecs, TimeUnit.SECONDS);
        }
    }

}
