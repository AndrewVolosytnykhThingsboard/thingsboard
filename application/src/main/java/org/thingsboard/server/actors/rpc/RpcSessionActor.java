/**
 * Thingsboard OÜ ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2018 Thingsboard OÜ. All Rights Reserved.
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
package org.thingsboard.server.actors.rpc;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.thingsboard.server.actors.ActorSystemContext;
import org.thingsboard.server.actors.service.ContextAwareActor;
import org.thingsboard.server.actors.service.ContextBasedCreator;
import org.thingsboard.server.common.msg.cluster.ServerAddress;
import org.thingsboard.server.gen.cluster.ClusterAPIProtos;
import org.thingsboard.server.gen.cluster.ClusterRpcServiceGrpc;
import org.thingsboard.server.service.cluster.rpc.GrpcSession;
import org.thingsboard.server.service.cluster.rpc.GrpcSessionListener;

import java.util.UUID;

/**
 * @author Andrew Shvayka
 */
public class RpcSessionActor extends ContextAwareActor {

    private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final UUID sessionId;
    private GrpcSession session;
    private GrpcSessionListener listener;

    public RpcSessionActor(ActorSystemContext systemContext, UUID sessionId) {
        super(systemContext);
        this.sessionId = sessionId;
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof RpcSessionTellMsg) {
            tell((RpcSessionTellMsg) msg);
        } else if (msg instanceof RpcSessionCreateRequestMsg) {
            initSession((RpcSessionCreateRequestMsg) msg);
        }
    }

    private void tell(RpcSessionTellMsg msg) {
        session.sendMsg(msg.getMsg());
    }

    @Override
    public void postStop() {
        log.info("Closing session -> {}", session.getRemoteServer());
        session.close();
    }

    private void initSession(RpcSessionCreateRequestMsg msg) {
        log.info("[{}] Initializing session", context().self());
        ServerAddress remoteServer = msg.getRemoteAddress();
        listener = new BasicRpcSessionListener(systemContext, context().parent(), context().self());
        if (msg.getRemoteAddress() == null) {
            // Server session
            session = new GrpcSession(listener);
            session.setOutputStream(msg.getResponseObserver());
            session.initInputStream();
            session.initOutputStream();
            systemContext.getRpcService().onSessionCreated(msg.getMsgUid(), session.getInputStream());
        } else {
            // Client session
            Channel channel = ManagedChannelBuilder.forAddress(remoteServer.getHost(), remoteServer.getPort()).usePlaintext(true).build();
            session = new GrpcSession(remoteServer, listener);
            session.initInputStream();

            ClusterRpcServiceGrpc.ClusterRpcServiceStub stub = ClusterRpcServiceGrpc.newStub(channel);
            StreamObserver<ClusterAPIProtos.ToRpcServerMessage> outputStream = stub.handlePluginMsgs(session.getInputStream());

            session.setOutputStream(outputStream);
            session.initOutputStream();
            outputStream.onNext(toConnectMsg());
        }
    }

    public static class ActorCreator extends ContextBasedCreator<RpcSessionActor> {
        private static final long serialVersionUID = 1L;

        private final UUID sessionId;

        public ActorCreator(ActorSystemContext context, UUID sessionId) {
            super(context);
            this.sessionId = sessionId;
        }

        @Override
        public RpcSessionActor create() throws Exception {
            return new RpcSessionActor(context, sessionId);
        }
    }

    private ClusterAPIProtos.ToRpcServerMessage toConnectMsg() {
        ServerAddress instance = systemContext.getDiscoveryService().getCurrentServer().getServerAddress();
        return ClusterAPIProtos.ToRpcServerMessage.newBuilder().setConnectMsg(
                ClusterAPIProtos.ConnectRpcMessage.newBuilder().setServerAddress(
                        ClusterAPIProtos.ServerAddress.newBuilder().setHost(instance.getHost()).setPort(instance.getPort()).build()).build()).build();

    }
}
