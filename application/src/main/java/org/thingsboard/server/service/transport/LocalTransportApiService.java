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
package org.thingsboard.server.service.transport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.data.security.DeviceCredentialsType;
import org.thingsboard.server.dao.device.DeviceCredentialsService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.relation.RelationService;
import org.thingsboard.server.gen.transport.DeviceInfoProto;
import org.thingsboard.server.gen.transport.GetOrCreateDeviceFromGatewayRequestMsg;
import org.thingsboard.server.gen.transport.GetOrCreateDeviceFromGatewayResponseMsg;
import org.thingsboard.server.gen.transport.TransportApiRequestMsg;
import org.thingsboard.server.gen.transport.TransportApiResponseMsg;
import org.thingsboard.server.gen.transport.ValidateDeviceCredentialsResponseMsg;
import org.thingsboard.server.gen.transport.ValidateDeviceTokenRequestMsg;
import org.thingsboard.server.gen.transport.ValidateDeviceX509CertRequestMsg;
import org.thingsboard.server.service.executors.DbCallbackExecutorService;
import org.thingsboard.server.service.state.DeviceStateService;

import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by ashvayka on 05.10.18.
 */
@Slf4j
@Service
public class LocalTransportApiService implements TransportApiService {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private RelationService relationService;

    @Autowired
    private DeviceCredentialsService deviceCredentialsService;

    @Autowired
    private DeviceStateService deviceStateService;

    @Autowired
    private DbCallbackExecutorService dbCallbackExecutorService;

    private ReentrantLock deviceCreationLock = new ReentrantLock();

    @Override
    public ListenableFuture<TransportApiResponseMsg> handle(TransportApiRequestMsg transportApiRequestMsg) {
        if (transportApiRequestMsg.hasValidateTokenRequestMsg()) {
            ValidateDeviceTokenRequestMsg msg = transportApiRequestMsg.getValidateTokenRequestMsg();
            return validateCredentials(msg.getToken(), DeviceCredentialsType.ACCESS_TOKEN);
        } else if (transportApiRequestMsg.hasValidateX509CertRequestMsg()) {
            ValidateDeviceX509CertRequestMsg msg = transportApiRequestMsg.getValidateX509CertRequestMsg();
            return validateCredentials(msg.getHash(), DeviceCredentialsType.X509_CERTIFICATE);
        } else if (transportApiRequestMsg.hasGetOrCreateDeviceRequestMsg()) {
            return handle(transportApiRequestMsg.getGetOrCreateDeviceRequestMsg());
        }
        return getEmptyTransportApiResponseFuture();
    }

    private ListenableFuture<TransportApiResponseMsg> validateCredentials(String credentialsId, DeviceCredentialsType credentialsType) {
        //TODO: Make async and enable caching
        DeviceCredentials credentials = deviceCredentialsService.findDeviceCredentialsByCredentialsId(credentialsId);
        if (credentials != null && credentials.getCredentialsType() == credentialsType) {
            return getDeviceInfo(credentials.getDeviceId(), credentials);
        } else {
            return getEmptyTransportApiResponseFuture();
        }
    }

    private ListenableFuture<TransportApiResponseMsg> handle(GetOrCreateDeviceFromGatewayRequestMsg requestMsg) {
        DeviceId gatewayId = new DeviceId(new UUID(requestMsg.getGatewayIdMSB(), requestMsg.getGatewayIdLSB()));
        ListenableFuture<Device> gatewayFuture = deviceService.findDeviceByIdAsync(TenantId.SYS_TENANT_ID, gatewayId);
        return Futures.transform(gatewayFuture, gateway -> {
            deviceCreationLock.lock();
            try {
                Device device = deviceService.findDeviceByTenantIdAndName(gateway.getTenantId(), requestMsg.getDeviceName());
                if (device == null) {
                    device = new Device();
                    device.setTenantId(gateway.getTenantId());
                    device.setName(requestMsg.getDeviceName());
                    device.setType(requestMsg.getDeviceType());
                    device.setCustomerId(gateway.getCustomerId());
                    device = deviceService.saveDevice(device);
                    relationService.saveRelationAsync(TenantId.SYS_TENANT_ID, new EntityRelation(gateway.getId(), device.getId(), "Created"));
                    deviceStateService.onDeviceAdded(device);
                }
                return TransportApiResponseMsg.newBuilder()
                        .setGetOrCreateDeviceResponseMsg(GetOrCreateDeviceFromGatewayResponseMsg.newBuilder().setDeviceInfo(getDeviceInfoProto(device)).build()).build();
            } catch (JsonProcessingException e) {
                log.warn("[{}] Failed to lookup device by gateway id and name", gatewayId, requestMsg.getDeviceName(), e);
                throw new RuntimeException(e);
            } finally {
                deviceCreationLock.unlock();
            }
        }, dbCallbackExecutorService);
    }


    private ListenableFuture<TransportApiResponseMsg> getDeviceInfo(DeviceId deviceId, DeviceCredentials credentials) {
        return Futures.transform(deviceService.findDeviceByIdAsync(TenantId.SYS_TENANT_ID, deviceId), device -> {
            if (device == null) {
                log.trace("[{}] Failed to lookup device by id", deviceId);
                return getEmptyTransportApiResponse();
            }
            try {
                ValidateDeviceCredentialsResponseMsg.Builder builder = ValidateDeviceCredentialsResponseMsg.newBuilder();
                builder.setDeviceInfo(getDeviceInfoProto(device));
                if(!StringUtils.isEmpty(credentials.getCredentialsValue())){
                    builder.setCredentialsBody(credentials.getCredentialsValue());
                }
                return TransportApiResponseMsg.newBuilder()
                        .setValidateTokenResponseMsg(builder.build()).build();
            } catch (JsonProcessingException e) {
                log.warn("[{}] Failed to lookup device by id", deviceId, e);
                return getEmptyTransportApiResponse();
            }
        });
    }

    private DeviceInfoProto getDeviceInfoProto(Device device) throws JsonProcessingException {
        return DeviceInfoProto.newBuilder()
                .setTenantIdMSB(device.getTenantId().getId().getMostSignificantBits())
                .setTenantIdLSB(device.getTenantId().getId().getLeastSignificantBits())
                .setDeviceIdMSB(device.getId().getId().getMostSignificantBits())
                .setDeviceIdLSB(device.getId().getId().getLeastSignificantBits())
                .setDeviceName(device.getName())
                .setDeviceType(device.getType())
                .setAdditionalInfo(mapper.writeValueAsString(device.getAdditionalInfo()))
                .build();
    }

    private ListenableFuture<TransportApiResponseMsg> getEmptyTransportApiResponseFuture() {
        return Futures.immediateFuture(getEmptyTransportApiResponse());
    }

    private TransportApiResponseMsg getEmptyTransportApiResponse() {
        return TransportApiResponseMsg.newBuilder()
                .setValidateTokenResponseMsg(ValidateDeviceCredentialsResponseMsg.getDefaultInstance()).build();
    }
}
