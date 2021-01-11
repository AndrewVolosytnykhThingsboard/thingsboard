/**
 * Copyright © 2016-2021 The Thingsboard Authors
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
package org.thingsboard.server.service.integration.rpc;

import com.google.common.io.Resources;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.thingsboard.integration.api.data.IntegrationDownlinkMsg;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.converter.Converter;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.IntegrationId;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.gen.integration.IntegrationTransportGrpc;
import org.thingsboard.server.gen.integration.RequestMsg;
import org.thingsboard.server.gen.integration.ResponseMsg;
import org.thingsboard.server.queue.TbQueueCallback;
import org.thingsboard.server.queue.discovery.TbServiceInfoProvider;
import org.thingsboard.server.service.integration.IntegrationContextComponent;
import org.thingsboard.server.service.queue.TbClusterService;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@ConditionalOnExpression("('${service.type:null}'=='monolith' || '${service.type:null}'=='tb-core') && ('${integrations.rpc.enabled:false}'=='true')")
public class GrpcIntegrationRpcService extends IntegrationTransportGrpc.IntegrationTransportImplBase implements IntegrationRpcService {

    private final Map<IntegrationId, IntegrationGrpcSession> sessions = new ConcurrentHashMap<>();

    @Value("${integrations.rpc.port}")
    private int rpcPort;
    @Value("${integrations.rpc.ssl.enabled}")
    private boolean sslEnabled;
    @Value("${integrations.rpc.ssl.cert}")
    private String certFileResource;
    @Value("${integrations.rpc.ssl.privateKey}")
    private String privateKeyResource;

    private final TbServiceInfoProvider serviceInfoProvider;
    private final IntegrationContextComponent ctx;
    private final RemoteIntegrationSessionService sessionsCache;
    private final TbClusterService clusterService;
    private final DeviceService deviceService;
    private Server server;

    public GrpcIntegrationRpcService(TbServiceInfoProvider serviceInfoProvider, IntegrationContextComponent ctx,
                                     RemoteIntegrationSessionService sessionsCache, TbClusterService clusterService, DeviceService deviceService) {
        this.serviceInfoProvider = serviceInfoProvider;
        this.ctx = ctx;
        this.sessionsCache = sessionsCache;
        this.clusterService = clusterService;
        this.deviceService = deviceService;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing RPC service!");
        ServerBuilder builder = ServerBuilder.forPort(rpcPort).addService(this);
        if (sslEnabled) {
            try {
                File certFile = new File(Resources.getResource(certFileResource).toURI());
                File privateKeyFile = new File(Resources.getResource(privateKeyResource).toURI());
                builder.useTransportSecurity(certFile, privateKeyFile);
            } catch (Exception e) {
                log.error("Unable to set up SSL context. Reason: " + e.getMessage(), e);
                throw new RuntimeException("Unable to set up SSL context!", e);
            }
        }
        server = builder.build();
        log.info("Going to start RPC server using port: {}", rpcPort);
        try {
            server.start();
        } catch (IOException e) {
            log.error("Failed to start RPC server!", e);
            throw new RuntimeException("Failed to start RPC server!");
        }
        log.info("RPC service initialized!");
    }

    @PreDestroy
    public void destroy() {
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Override
    public StreamObserver<RequestMsg> handleMsgs(StreamObserver<ResponseMsg> responseObserver) {
        return new IntegrationGrpcSession(ctx, responseObserver, this::onIntegrationConnect, this::onIntegrationDisconnect).getInputStream();
    }

    @Override
    public void updateIntegration(Integration configuration) {
        IntegrationGrpcSession session = sessions.get(configuration.getId());
        if (session != null && session.isConnected()) {
            session.onConfigurationUpdate(configuration);
        }
    }

    @Override
    public void updateConverter(Converter converter) {
        for (Map.Entry<IntegrationId, IntegrationGrpcSession> entry : sessions.entrySet()) {
            Integration configuration = entry.getValue().getConfiguration();
            if (entry.getValue().isConnected()
                    && (configuration.getDefaultConverterId().equals(converter.getId()) || converter.getId().equals(configuration.getDownlinkConverterId()))) {
                try {
                    entry.getValue().onConverterUpdate(converter);
                } catch (Exception e) {
                    log.error("Failed to update integration [{}] with converter [{}]", entry.getKey().getId(), converter, e);
                }
            }
        }
    }

    @Override
    public boolean handleRemoteDownlink(IntegrationDownlinkMsg msg) {
        boolean sessionFound = false;
        IntegrationGrpcSession session = sessions.get(msg.getIntegrationId());
        if (session != null) {
            log.debug("[{}] Remote integration session found for [{}] downlink.", msg.getIntegrationId(), msg.getEntityId());
            Device device = deviceService.findDeviceById(msg.getTenantId(), new DeviceId(msg.getEntityId().getId()));
            if (device != null) {
                session.onDownlink(device, msg);
            } else {
                log.debug("[{}] device [{}] not found.", msg.getIntegrationId(), msg.getEntityId());
            }
            sessionFound = true;
        } else {
            IntegrationSession remoteSession = sessionsCache.findIntegrationSession(msg.getIntegrationId());
            if (remoteSession != null && !remoteSession.getServiceId().equals(serviceInfoProvider.getServiceId())) {
                log.debug("[{}] Remote integration session found for [{}] downlink @ Server [{}].", msg.getIntegrationId(), msg.getEntityId(), remoteSession.getServiceId());
                clusterService.pushNotificationToCore(remoteSession.getServiceId(), msg, null);
                sessionFound = true;
            }
        }
        return sessionFound;
    }

    private void onIntegrationConnect(IntegrationId integrationId, IntegrationGrpcSession integrationGrpcSession) {
        sessions.put(integrationId, integrationGrpcSession);
        sessionsCache.putIntegrationSession(integrationId, new IntegrationSession(serviceInfoProvider.getServiceId()));
    }

    private void onIntegrationDisconnect(IntegrationId integrationId) {
        sessions.remove(integrationId);
        sessionsCache.removeIntegrationSession(integrationId);
    }
}
