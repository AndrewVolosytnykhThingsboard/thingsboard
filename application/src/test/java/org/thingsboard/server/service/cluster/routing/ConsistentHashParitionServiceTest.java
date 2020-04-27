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
package org.thingsboard.server.service.cluster.routing;

import com.datastax.driver.core.utils.UUIDs;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.queue.discovery.ConsistentHashPartitionService;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.queue.discovery.TbServiceInfoProvider;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.queue.discovery.TenantRoutingInfoService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class ConsistentHashParitionServiceTest {

    public static final int ITERATIONS = 1000000;
    private ConsistentHashPartitionService clusterRoutingService;

    private TbServiceInfoProvider discoveryService;
    private TenantRoutingInfoService routingInfoService;
    private ApplicationEventPublisher applicationEventPublisher;

    private String hashFunctionName = "murmur3_128";
    private Integer virtualNodesSize = 16;


    @Before
    public void setup() throws Exception {
        discoveryService = mock(TbServiceInfoProvider.class);
        applicationEventPublisher = mock(ApplicationEventPublisher.class);
        routingInfoService = mock(TenantRoutingInfoService.class);
        clusterRoutingService = new ConsistentHashPartitionService(discoveryService, routingInfoService, applicationEventPublisher);
        ReflectionTestUtils.setField(clusterRoutingService, "coreTopic", "tb.core");
        ReflectionTestUtils.setField(clusterRoutingService, "corePartitions", 3);
        ReflectionTestUtils.setField(clusterRoutingService, "ruleEngineTopic", "tb.rule-engine");
        ReflectionTestUtils.setField(clusterRoutingService, "ruleEnginePartitions", 100);

        ReflectionTestUtils.setField(clusterRoutingService, "hashFunctionName", hashFunctionName);
        ReflectionTestUtils.setField(clusterRoutingService, "virtualNodesSize", virtualNodesSize);
        TransportProtos.ServiceInfo currentServer = TransportProtos.ServiceInfo.newBuilder()
                .setServiceId("100.96.1.1")
                .addAllServiceTypes(Collections.singletonList(ServiceType.TB_CORE.name()))
                .build();
//        when(discoveryService.getServiceInfo()).thenReturn(currentServer);
        List<TransportProtos.ServiceInfo> otherServers = new ArrayList<>();
        for (int i = 1; i < 30; i++) {
            otherServers.add(TransportProtos.ServiceInfo.newBuilder()
                    .setServiceId("100.96." + i * 2 + "." + i)
                    .addAllServiceTypes(Collections.singletonList(ServiceType.TB_CORE.name()))
                    .build());
        }
        clusterRoutingService.init();
        clusterRoutingService.recalculatePartitions(currentServer, otherServers);
    }

    @Test
    public void testDispersionOnMillionDevices() {
        List<DeviceId> devices = new ArrayList<>();
        for (int i = 0; i < ITERATIONS; i++) {
            devices.add(new DeviceId(UUIDs.timeBased()));
        }
        testDevicesDispersion(devices);
    }

    private void testDevicesDispersion(List<DeviceId> devices) {
        long start = System.currentTimeMillis();
        Map<Integer, Integer> map = new HashMap<>();
        for (DeviceId deviceId : devices) {
            TopicPartitionInfo address = clusterRoutingService.resolve(ServiceType.TB_CORE, TenantId.SYS_TENANT_ID, deviceId);
            Integer partition = address.getPartition().get();
            map.put(partition, map.getOrDefault(partition, 0) + 1);
        }

        List<Map.Entry<Integer, Integer>> data = map.entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getValue)).collect(Collectors.toList());
        long end = System.currentTimeMillis();
        double diff = (data.get(data.size() - 1).getValue() - data.get(0).getValue());
        System.out.println("Size: " + virtualNodesSize + " Time: " + (end - start) + " Diff: " + diff + "(" + String.format("%f", (diff / ITERATIONS) * 100.0) + "%)");

        for (Map.Entry<Integer, Integer> entry : data) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }

    }

}