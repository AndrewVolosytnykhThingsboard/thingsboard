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
package org.thingsboard.server.dao.sql.edge;

import com.google.common.util.concurrent.ListenableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.EntitySubtype;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.UUIDConverter;
import org.thingsboard.server.common.data.Edge;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.edge.EdgeDao;
import org.thingsboard.server.dao.model.sql.EdgeEntity;
import org.thingsboard.server.dao.sql.JpaAbstractSearchTextDao;
import org.thingsboard.server.dao.util.SqlDao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.thingsboard.server.common.data.UUIDConverter.fromTimeUUID;
import static org.thingsboard.server.common.data.UUIDConverter.fromTimeUUIDs;
import static org.thingsboard.server.dao.model.ModelConstants.NULL_UUID_STR;

@Component
@SqlDao
public class JpaEdgeDao extends JpaAbstractSearchTextDao<EdgeEntity, Edge> implements EdgeDao {

    @Autowired
    private EdgeRepository edgeRepository;

    @Override
    protected Class<EdgeEntity> getEntityClass() {
        return EdgeEntity.class;
    }

    @Override
    protected CrudRepository<EdgeEntity, String> getCrudRepository() {
        return edgeRepository;
    }

    @Override
    public List<Edge> findEdgesByTenantId(UUID tenantId, TextPageLink pageLink) {
        return DaoUtil.convertDataList(
                edgeRepository.findByTenantId(
                        fromTimeUUID(tenantId),
                        Objects.toString(pageLink.getTextSearch(), ""),
                        pageLink.getIdOffset() == null ? NULL_UUID_STR : fromTimeUUID(pageLink.getIdOffset()),
                        PageRequest.of(0, pageLink.getLimit())));
    }

    @Override
    public ListenableFuture<List<Edge>> findEdgesByTenantIdAndIdsAsync(UUID tenantId, List<UUID> edgeIds) {
        return service.submit(() -> DaoUtil.convertDataList(edgeRepository.findEdgesByTenantIdAndIdIn(UUIDConverter.fromTimeUUID(tenantId), fromTimeUUIDs(edgeIds))));
    }

    @Override
    public List<Edge> findEdgesByTenantIdAndCustomerId(UUID tenantId, UUID customerId, TextPageLink pageLink) {
        return DaoUtil.convertDataList(
                edgeRepository.findByTenantIdAndCustomerId(
                        fromTimeUUID(tenantId),
                        fromTimeUUID(customerId),
                        Objects.toString(pageLink.getTextSearch(), ""),
                        pageLink.getIdOffset() == null ? NULL_UUID_STR : fromTimeUUID(pageLink.getIdOffset()),
                        PageRequest.of(0, pageLink.getLimit())));
    }

    @Override
    public ListenableFuture<List<Edge>> findEdgesByTenantIdCustomerIdAndIdsAsync(UUID tenantId, UUID customerId, List<UUID> edgeIds) {
        return service.submit(() -> DaoUtil.convertDataList(
                edgeRepository.findEdgesByTenantIdAndCustomerIdAndIdIn(fromTimeUUID(tenantId), fromTimeUUID(customerId), fromTimeUUIDs(edgeIds))));
    }

    @Override
    public Optional<Edge> findEdgeByTenantIdAndName(UUID tenantId, String name) {
        Edge edge = DaoUtil.getData(edgeRepository.findByTenantIdAndName(fromTimeUUID(tenantId), name));
        return Optional.ofNullable(edge);
    }

    @Override
    public List<Edge> findEdgesByTenantIdAndType(UUID tenantId, String type, TextPageLink pageLink) {
        return DaoUtil.convertDataList(
                edgeRepository.findByTenantIdAndType(
                        fromTimeUUID(tenantId),
                        type,
                        Objects.toString(pageLink.getTextSearch(), ""),
                        pageLink.getIdOffset() == null ? NULL_UUID_STR : fromTimeUUID(pageLink.getIdOffset()),
                        PageRequest.of(0, pageLink.getLimit())));
    }

    @Override
    public List<Edge> findEdgesByTenantIdAndCustomerIdAndType(UUID tenantId, UUID customerId, String type, TextPageLink pageLink) {
        return DaoUtil.convertDataList(
                edgeRepository.findByTenantIdAndCustomerIdAndType(
                        fromTimeUUID(tenantId),
                        fromTimeUUID(customerId),
                        type,
                        Objects.toString(pageLink.getTextSearch(), ""),
                        pageLink.getIdOffset() == null ? NULL_UUID_STR : fromTimeUUID(pageLink.getIdOffset()),
                        PageRequest.of(0, pageLink.getLimit())));
    }

    @Override
    public ListenableFuture<List<EntitySubtype>> findTenantEdgeTypesAsync(UUID tenantId) {
        return service.submit(() -> convertTenantEdgeTypesToDto(tenantId, edgeRepository.findTenantEdgeTypes(fromTimeUUID(tenantId))));
    }

    @Override
    public Optional<Edge> findByRoutingKey(UUID tenantId, String routingKey) {
        Edge edge = DaoUtil.getData(edgeRepository.findByRoutingKey(routingKey));
        return Optional.ofNullable(edge);
    }

    private List<EntitySubtype> convertTenantEdgeTypesToDto(UUID tenantId, List<String> types) {
        List<EntitySubtype> list = Collections.emptyList();
        if (types != null && !types.isEmpty()) {
            list = new ArrayList<>();
            for (String type : types) {
                list.add(new EntitySubtype(new TenantId(tenantId), EntityType.EDGE, type));
            }
        }
        return list;
    }

}
