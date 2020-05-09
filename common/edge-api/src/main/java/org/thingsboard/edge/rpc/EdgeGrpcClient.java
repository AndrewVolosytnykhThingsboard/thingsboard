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
package org.thingsboard.edge.rpc;

import com.google.common.io.Resources;
import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.edge.exception.EdgeConnectionException;
import org.thingsboard.server.gen.edge.ConnectRequestMsg;
import org.thingsboard.server.gen.edge.ConnectResponseCode;
import org.thingsboard.server.gen.edge.ConnectResponseMsg;
import org.thingsboard.server.gen.edge.DownlinkMsg;
import org.thingsboard.server.gen.edge.EdgeConfiguration;
import org.thingsboard.server.gen.edge.EdgeRpcServiceGrpc;
import org.thingsboard.server.gen.edge.EntityUpdateMsg;
import org.thingsboard.server.gen.edge.RequestMsg;
import org.thingsboard.server.gen.edge.RequestMsgType;
import org.thingsboard.server.gen.edge.ResponseMsg;
import org.thingsboard.server.gen.edge.UplinkMsg;
import org.thingsboard.server.gen.edge.UplinkResponseMsg;

import javax.net.ssl.SSLException;
import java.io.File;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
@Slf4j
public class EdgeGrpcClient implements EdgeRpcClient {

    @Value("${cloud.rpc.host}")
    private String rpcHost;
    @Value("${cloud.rpc.port}")
    private int rpcPort;
    @Value("${cloud.rpc.timeout}")
    private int timeoutSecs;
    @Value("${cloud.rpc.ssl.enabled}")
    private boolean sslEnabled;
    @Value("${cloud.rpc.ssl.cert}")
    private String certResource;

    private ManagedChannel channel;

    private StreamObserver<RequestMsg> inputStream;

    @Override
    public void connect(String edgeKey,
                        String edgeSecret,
                        Consumer<UplinkResponseMsg> onUplinkResponse,
                        Consumer<EdgeConfiguration> onEdgeUpdate,
                        Consumer<EntityUpdateMsg> onEntityUpdate,
                        Consumer<DownlinkMsg> onDownlink,
                        Consumer<Exception> onError) {
        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(rpcHost, rpcPort).usePlaintext();
        if (sslEnabled) {
            try {
                builder.sslContext(GrpcSslContexts.forClient().trustManager(new File(Resources.getResource(certResource).toURI())).build());
            } catch (URISyntaxException | SSLException e) {
                log.error("Failed to initialize channel!", e);
                throw new RuntimeException(e);
            }
        }
        channel = builder.build();
        EdgeRpcServiceGrpc.EdgeRpcServiceStub stub = EdgeRpcServiceGrpc.newStub(channel);
        log.info("[{}] Sending a connect request to the TB!", edgeKey);
        this.inputStream = stub.handleMsgs(initOutputStream(edgeKey, onUplinkResponse, onEdgeUpdate, onEntityUpdate, onDownlink, onError));
        this.inputStream.onNext(RequestMsg.newBuilder()
                .setMsgType(RequestMsgType.CONNECT_RPC_MESSAGE)
                .setConnectRequestMsg(ConnectRequestMsg.newBuilder().setEdgeRoutingKey(edgeKey).setEdgeSecret(edgeSecret).build())
                .build());
    }

    @Override
    public void disconnect() throws InterruptedException {
        inputStream.onCompleted();
        if (channel != null) {
            channel.shutdown().awaitTermination(timeoutSecs, TimeUnit.SECONDS);
        }
    }

    @Override
    public void sendUplinkMsg(UplinkMsg msg) {
        this.inputStream.onNext(RequestMsg.newBuilder()
                .setMsgType(RequestMsgType.UPLINK_RPC_MESSAGE)
                .setUplinkMsg(msg)
                .build());
    }

    private StreamObserver<ResponseMsg> initOutputStream(String edgeKey,
                                                         Consumer<UplinkResponseMsg> onUplinkResponse,
                                                         Consumer<EdgeConfiguration> onEdgeUpdate,
                                                         Consumer<EntityUpdateMsg> onEntityUpdate,
                                                         Consumer<DownlinkMsg> onDownlink,
                                                         Consumer<Exception> onError) {
        return new StreamObserver<ResponseMsg>() {
            @Override
            public void onNext(ResponseMsg responseMsg) {
                if (responseMsg.hasConnectResponseMsg()) {
                    ConnectResponseMsg connectResponseMsg = responseMsg.getConnectResponseMsg();
                    if (connectResponseMsg.getResponseCode().equals(ConnectResponseCode.ACCEPTED)) {
                        log.info("[{}] Configuration received: {}", edgeKey, connectResponseMsg.getConfiguration());
                        onEdgeUpdate.accept(connectResponseMsg.getConfiguration());
                    } else {
                        log.error("[{}] Failed to establish the connection! Code: {}. Error message: {}.", edgeKey, connectResponseMsg.getResponseCode(), connectResponseMsg.getErrorMsg());
                        onError.accept(new EdgeConnectionException("Failed to establish the connection! Response code: " + connectResponseMsg.getResponseCode().name()));
                    }
                } else if (responseMsg.hasUplinkResponseMsg()) {
                    log.debug("[{}] Uplink response message received {}", edgeKey, responseMsg.getUplinkResponseMsg());
                    onUplinkResponse.accept(responseMsg.getUplinkResponseMsg());
                } else if (responseMsg.hasEntityUpdateMsg()) {
                    log.debug("[{}] Entity update message received {}", edgeKey, responseMsg.getEntityUpdateMsg());
                    onEntityUpdate.accept(responseMsg.getEntityUpdateMsg());
                } else if (responseMsg.hasDownlinkMsg()) {
                    log.debug("[{}] Downlink message received for rule chain {}", edgeKey, responseMsg.getDownlinkMsg());
                    onDownlink.accept(responseMsg.getDownlinkMsg());
                }
            }

            @Override
            public void onError(Throwable t) {
                log.debug("[{}] The rpc session received an error!", edgeKey, t);
                onError.accept(new RuntimeException(t));
            }

            @Override
            public void onCompleted() {
                log.debug("[{}] The rpc session was closed!", edgeKey);
            }
        };
    }
}
