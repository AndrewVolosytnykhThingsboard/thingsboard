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
package org.thingsboard.server.dao.model.nosql;

import com.datastax.driver.core.utils.UUIDs;
import com.datastax.driver.mapping.annotations.ClusteringColumn;
import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Table;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import org.thingsboard.server.common.data.Edge;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.model.SearchTextEntity;
import org.thingsboard.server.dao.model.type.JsonCodec;

import java.util.UUID;

import static org.thingsboard.server.dao.model.ModelConstants.EDGE_ADDITIONAL_INFO_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_COLUMN_FAMILY_NAME;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_CONFIGURATION_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_CUSTOMER_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_LABEL_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_NAME_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_ROOT_RULE_CHAIN_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_ROUTING_KEY_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_SECRET_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_TENANT_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_TYPE_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.SEARCH_TEXT_PROPERTY;

@Data
@Table(name = EDGE_COLUMN_FAMILY_NAME)
public class EdgeEntity implements SearchTextEntity<Edge> {

    @PartitionKey
    @Column(name = ID_PROPERTY)
    private UUID id;

    @ClusteringColumn
    @Column(name = EDGE_TENANT_ID_PROPERTY)
    private UUID tenantId;

    @ClusteringColumn
    @Column(name = EDGE_CUSTOMER_ID_PROPERTY)
    private UUID customerId;

    @Column(name = EDGE_ROOT_RULE_CHAIN_ID_PROPERTY)
    private UUID rootRuleChainId;

    @Column(name = EDGE_TYPE_PROPERTY)
    private String type;

    @Column(name = EDGE_NAME_PROPERTY)
    private String name;

    @Column(name = EDGE_LABEL_PROPERTY)
    private String label;

    @Column(name = SEARCH_TEXT_PROPERTY)
    private String searchText;

    @Column(name = EDGE_ROUTING_KEY_PROPERTY)
    private String routingKey;

    @Column(name = EDGE_SECRET_PROPERTY)
    private String secret;

    @Column(name = EDGE_CONFIGURATION_PROPERTY, codec = JsonCodec.class)
    private JsonNode configuration;

    @Column(name = EDGE_ADDITIONAL_INFO_PROPERTY, codec = JsonCodec.class)
    private JsonNode additionalInfo;

    public EdgeEntity() {
        super();
    }

    @Override
    public UUID getUuid() {
        return getId();
    }

    @Override
    public void setUuid(UUID id) {
        this.id = id;
    }

    public EdgeEntity(Edge edge) {
        if (edge.getId() != null) {
            this.id = edge.getId().getId();
        }
        if (edge.getTenantId() != null) {
            this.tenantId = edge.getTenantId().getId();
        }
        if (edge.getCustomerId() != null) {
            this.customerId = edge.getCustomerId().getId();
        }
        if (edge.getRootRuleChainId() != null) {
            this.rootRuleChainId = edge.getRootRuleChainId().getId();
        }
        this.type = edge.getType();
        this.name = edge.getName();
        this.label = edge.getLabel();
        this.routingKey = edge.getRoutingKey();
        this.secret = edge.getSecret();
        this.configuration = edge.getConfiguration();
        this.additionalInfo = edge.getAdditionalInfo();
    }

    @Override
    public String getSearchTextSource() {
        return getName();
    }

    @Override
    public Edge toData() {
        Edge edge = new Edge(new EdgeId(id));
        edge.setCreatedTime(UUIDs.unixTimestamp(id));
        if (tenantId != null) {
            edge.setTenantId(new TenantId(tenantId));
        }
        if (customerId != null) {
            edge.setCustomerId(new CustomerId(customerId));
        }
        if (rootRuleChainId != null) {
            edge.setRootRuleChainId(new RuleChainId(rootRuleChainId));
        }
        edge.setType(type);
        edge.setName(name);
        edge.setLabel(label);
        edge.setRoutingKey(routingKey);
        edge.setSecret(secret);
        edge.setConfiguration(configuration);
        edge.setAdditionalInfo(additionalInfo);
        return edge;
    }
}
