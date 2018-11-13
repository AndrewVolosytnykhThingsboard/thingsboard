/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2018 ThingsBoard, Inc. All Rights Reserved.
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

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.thingsboard.server.actors.service.ActorService;
import org.thingsboard.server.common.transport.SessionMsgProcessor;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.event.EventService;
import org.thingsboard.server.dao.relation.RelationService;
import org.thingsboard.server.service.cluster.discovery.DiscoveryService;
import org.thingsboard.server.service.integration.downlink.DownlinkService;

/**
 * Created by ashvayka on 05.12.17.
 */
@Component
@Data
public class IntegrationContext {

    private volatile boolean isClosed = false;

    @EventListener
    public void handleContextClosed (ContextClosedEvent event) {
        isClosed = true;
    }

    @Lazy
    @Autowired
    private PlatformIntegrationService integrationService;

    @Lazy
    @Autowired
    private SessionMsgProcessor sessionMsgProcessor;

    @Lazy
    @Autowired
    private DeviceService deviceService;

    @Lazy
    @Autowired
    private RelationService relationService;

    @Lazy
    @Autowired
    private EventService eventService;

    @Lazy
    @Autowired
    private DiscoveryService discoveryService;

    @Lazy
    @Autowired
    private ActorService actorService;

    @Lazy
    @Autowired
    private ConverterContext converterContext;

    @Lazy
    @Autowired
    private DownlinkService downlinkService;

}
