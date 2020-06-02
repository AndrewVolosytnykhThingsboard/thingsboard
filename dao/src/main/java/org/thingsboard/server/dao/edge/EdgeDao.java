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
package org.thingsboard.server.dao.edge;

import com.google.common.util.concurrent.ListenableFuture;
import org.thingsboard.server.common.data.EntitySubtype;
import org.thingsboard.server.common.data.Edge;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.dao.Dao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The Interface EdgeDao.
 *
 */
public interface EdgeDao extends Dao<Edge> {

    /**
     * Save or update edge object
     *
     * @param edge the edge object
     * @return saved edge object
     */
    Edge save(TenantId tenantId, Edge edge);

    /**
     * Find edges by tenantId and page link.
     *
     * @param tenantId the tenantId
     * @param pageLink the page link
     * @return the list of edge objects
     */
    List<Edge> findEdgesByTenantId(UUID tenantId, TextPageLink pageLink);

    /**
     * Find edges by tenantId, type and page link.
     *
     * @param tenantId the tenantId
     * @param type the type
     * @param pageLink the page link
     * @return the list of edge objects
     */
    List<Edge> findEdgesByTenantIdAndType(UUID tenantId, String type, TextPageLink pageLink);

    /**
     * Find edges by tenantId and edges Ids.
     *
     * @param tenantId the tenantId
     * @param edgeIds the edge Ids
     * @return the list of edge objects
     */
    ListenableFuture<List<Edge>> findEdgesByTenantIdAndIdsAsync(UUID tenantId, List<UUID> edgeIds);

    /**
     * Find edges by tenantId, customerId and page link.
     *
     * @param tenantId the tenantId
     * @param customerId the customerId
     * @param pageLink the page link
     * @return the list of edge objects
     */
    List<Edge> findEdgesByTenantIdAndCustomerId(UUID tenantId, UUID customerId, TextPageLink pageLink);

    /**
     * Find edges by tenantId, customerId, type and page link.
     *
     * @param tenantId the tenantId
     * @param customerId the customerId
     * @param type the type
     * @param pageLink the page link
     * @return the list of edge objects
     */
    List<Edge> findEdgesByTenantIdAndCustomerIdAndType(UUID tenantId, UUID customerId, String type, TextPageLink pageLink);


    /**
     * Find edges by tenantId, customerId and edges Ids.
     *
     * @param tenantId the tenantId
     * @param customerId the customerId
     * @param edgeIds the edge Ids
     * @return the list of edge objects
     */
    ListenableFuture<List<Edge>> findEdgesByTenantIdCustomerIdAndIdsAsync(UUID tenantId, UUID customerId, List<UUID> edgeIds);

    /**
     * Find edges by tenantId and edge name.
     *
     * @param tenantId the tenantId
     * @param name the edge name
     * @return the optional edge object
     */
    Optional<Edge> findEdgeByTenantIdAndName(UUID tenantId, String name);

    /**
     * Find tenants edge types.
     *
     * @return the list of tenant edge type objects
     */
    ListenableFuture<List<EntitySubtype>> findTenantEdgeTypesAsync(UUID tenantId);

    /**
     * Find edge by routing Key.
     *
     * @param routingKey the edge routingKey
     * @return the optional edge object
     */
    Optional<Edge> findByRoutingKey(UUID tenantId, String routingKey);

    /**
     * Find edges by tenantId and ruleChainId.
     *
     * @param tenantId the tenantId
     * @param ruleChainId the ruleChainId
     * @return the list of rule chain objects
     */
    ListenableFuture<List<Edge>> findEdgesByTenantIdAndRuleChainId(UUID tenantId, UUID ruleChainId);

    /**
     * Find edges by tenantId and dashboardId.
     *
     * @param tenantId the tenantId
     * @param dashboardId the dashboardId
     * @return the list of rule chain objects
     */
    ListenableFuture<List<Edge>> findEdgesByTenantIdAndDashboardId(UUID tenantId, UUID dashboardId);

    /**
     * Find edges by tenantId and schedulerEventId.
     *
     * @param tenantId the tenantId
     * @param schedulerEventId the schedulerEventId
     * @return the list of rule chain objects
     */
    ListenableFuture<List<Edge>> findEdgesByTenantIdAndSchedulerEventId(UUID tenantId, UUID schedulerEventId);
}
