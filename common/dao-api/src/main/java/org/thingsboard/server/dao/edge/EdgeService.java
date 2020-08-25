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
import org.thingsboard.server.common.data.Edge;
import org.thingsboard.server.common.data.EntitySubtype;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.ShortEntityView;
import org.thingsboard.server.common.data.edge.EdgeSearchQuery;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.SchedulerEventId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.page.TimePageData;
import org.thingsboard.server.common.data.page.TimePageLink;

import java.util.List;
import java.util.Optional;

public interface EdgeService {

    Edge findEdgeById(TenantId tenantId, EdgeId edgeId);

    ListenableFuture<Edge> findEdgeByIdAsync(TenantId tenantId, EdgeId edgeId);

    Edge findEdgeByTenantIdAndName(TenantId tenantId, String name);

    Optional<Edge> findEdgeByRoutingKey(TenantId tenantId, String routingKey);

    Edge saveEdge(Edge edge);

    Edge assignEdgeToCustomer(TenantId tenantId, EdgeId edgeId, CustomerId customerId);

    Edge unassignEdgeFromCustomer(TenantId tenantId, EdgeId edgeId);

    void deleteEdge(TenantId tenantId, EdgeId edgeId);

    TextPageData<Edge> findEdgesByTenantId(TenantId tenantId, TextPageLink pageLink);

    TextPageData<Edge> findEdgesByTenantIdAndType(TenantId tenantId, String type, TextPageLink pageLink);

    ListenableFuture<List<Edge>> findEdgesByTenantIdAndIdsAsync(TenantId tenantId, List<EdgeId> edgeIds);

    void deleteEdgesByTenantId(TenantId tenantId);

    TextPageData<Edge> findEdgesByTenantIdAndCustomerId(TenantId tenantId, CustomerId customerId, TextPageLink pageLink);

    TextPageData<Edge> findEdgesByTenantIdAndCustomerIdAndType(TenantId tenantId, CustomerId customerId, String type, TextPageLink pageLink);

    ListenableFuture<List<Edge>> findEdgesByTenantIdCustomerIdAndIdsAsync(TenantId tenantId, CustomerId customerId, List<EdgeId> edgeIds);

    void unassignCustomerEdges(TenantId tenantId, CustomerId customerId);

    ListenableFuture<List<Edge>> findEdgesByQuery(TenantId tenantId, EdgeSearchQuery query);

    ListenableFuture<List<EntitySubtype>> findEdgeTypesByTenantId(TenantId tenantId);

    ShortEntityView findGroupEdge(TenantId tenantId, EntityGroupId entityGroupId, EntityId entityId);

    ListenableFuture<TimePageData<ShortEntityView>> findEdgesByEntityGroupId(TenantId tenantId, EntityGroupId entityGroupId, TimePageLink pageLink);

    void assignDefaultRuleChainsToEdge(TenantId tenantId, EdgeId edgeId);

    ListenableFuture<List<Edge>> findEdgesByTenantIdAndRuleChainId(TenantId tenantId, RuleChainId ruleChainId);

    ListenableFuture<List<Edge>> findEdgesByTenantIdAndSchedulerEventId(TenantId tenantId, SchedulerEventId schedulerEventId);

    ListenableFuture<List<Edge>> findEdgesByTenantIdAndEntityGroupId(TenantId tenantId, EntityGroupId entityGroupId, EntityType groupType);

    ListenableFuture<List<Edge>> findEdgesByTenantIdAndDashboardId(TenantId tenantId, DashboardId dashboardId);

    ListenableFuture<List<EdgeId>> findRelatedEdgeIdsByEntityId(TenantId tenantId, EntityId entityId, String groupTypeStr);
}


                

                

                        

                        

                                