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
package org.thingsboard.server.dao.entityview;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.Select;
import com.datastax.driver.mapping.Result;
import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.EntitySubtype;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.model.EntitySubtypeEntity;
import org.thingsboard.server.dao.model.nosql.EntityViewEntity;
import org.thingsboard.server.dao.nosql.CassandraAbstractSearchTextDao;
import org.thingsboard.server.dao.util.NoSqlDao;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.thingsboard.server.dao.model.ModelConstants.CUSTOMER_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.DEVICE_TYPE_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ENTITY_ID_COLUMN;
import static org.thingsboard.server.dao.model.ModelConstants.ENTITY_SUBTYPE_COLUMN_FAMILY_NAME;
import static org.thingsboard.server.dao.model.ModelConstants.ENTITY_SUBTYPE_ENTITY_TYPE_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ENTITY_SUBTYPE_TENANT_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ENTITY_VIEW_BY_TENANT_AND_CUSTOMER_AND_TYPE_CF;
import static org.thingsboard.server.dao.model.ModelConstants.ENTITY_VIEW_BY_TENANT_AND_CUSTOMER_CF;
import static org.thingsboard.server.dao.model.ModelConstants.ENTITY_VIEW_BY_TENANT_AND_ENTITY_ID_CF;
import static org.thingsboard.server.dao.model.ModelConstants.ENTITY_VIEW_BY_TENANT_AND_NAME;
import static org.thingsboard.server.dao.model.ModelConstants.ENTITY_VIEW_BY_TENANT_AND_SEARCH_TEXT_CF;
import static org.thingsboard.server.dao.model.ModelConstants.ENTITY_VIEW_BY_TENANT_BY_TYPE_AND_SEARCH_TEXT_CF;
import static org.thingsboard.server.dao.model.ModelConstants.ENTITY_VIEW_NAME_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ENTITY_VIEW_TABLE_FAMILY_NAME;
import static org.thingsboard.server.dao.model.ModelConstants.ENTITY_VIEW_TENANT_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ENTITY_VIEW_TYPE_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.TENANT_ID_PROPERTY;

/**
 * Created by Victor Basanets on 9/06/2017.
 */
@Component
@Slf4j
@NoSqlDao
public class CassandraEntityViewDao extends CassandraAbstractSearchTextDao<EntityViewEntity, EntityView> implements EntityViewDao {

    @Override
    protected Class<EntityViewEntity> getColumnFamilyClass() {
        return EntityViewEntity.class;
    }

    @Override
    protected String getColumnFamilyName() {
        return ENTITY_VIEW_TABLE_FAMILY_NAME;
    }

    @Override
    public EntityView save(EntityView domain) {
        EntityView savedEntityView = super.save(domain);
        EntitySubtype entitySubtype = new EntitySubtype(savedEntityView.getTenantId(), EntityType.ENTITY_VIEW,
                savedEntityView.getId().getEntityType().toString());
        EntitySubtypeEntity entitySubtypeEntity = new EntitySubtypeEntity(entitySubtype);
        Statement saveStatement = cluster.getMapper(EntitySubtypeEntity.class).saveQuery(entitySubtypeEntity);
        executeWrite(saveStatement);
        return savedEntityView;
    }

    @Override
    public List<EntityView> findEntityViewsByTenantId(UUID tenantId, TextPageLink pageLink) {
        log.debug("Try to find entity views by tenantId [{}] and pageLink [{}]", tenantId, pageLink);
        List<EntityViewEntity> entityViewEntities =
                findPageWithTextSearch(ENTITY_VIEW_BY_TENANT_AND_SEARCH_TEXT_CF,
                Collections.singletonList(eq(TENANT_ID_PROPERTY, tenantId)), pageLink);
        log.trace("Found entity views [{}] by tenantId [{}] and pageLink [{}]",
                entityViewEntities, tenantId, pageLink);
        return DaoUtil.convertDataList(entityViewEntities);
    }

    @Override
    public List<EntityView> findEntityViewsByTenantIdAndType(UUID tenantId, String type, TextPageLink pageLink) {
        log.debug("Try to find entity views by tenantId [{}], type [{}] and pageLink [{}]", tenantId, type, pageLink);
        List<EntityViewEntity> entityViewEntities =
                findPageWithTextSearch(ENTITY_VIEW_BY_TENANT_BY_TYPE_AND_SEARCH_TEXT_CF,
                        Arrays.asList(eq(ENTITY_VIEW_TYPE_PROPERTY, type),
                                eq(TENANT_ID_PROPERTY, tenantId)), pageLink);
        log.trace("Found entity views [{}] by tenantId [{}], type [{}] and pageLink [{}]",
                entityViewEntities, tenantId, type, pageLink);
        return DaoUtil.convertDataList(entityViewEntities);
    }

    @Override
    public Optional<EntityView> findEntityViewByTenantIdAndName(UUID tenantId, String name) {
        Select.Where query = select().from(ENTITY_VIEW_BY_TENANT_AND_NAME).where();
        query.and(eq(ENTITY_VIEW_TENANT_ID_PROPERTY, tenantId));
        query.and(eq(ENTITY_VIEW_NAME_PROPERTY, name));
        return Optional.ofNullable(DaoUtil.getData(findOneByStatement(query)));
    }

    @Override
    public List<EntityView> findEntityViewsByTenantIdAndCustomerId(UUID tenantId, UUID customerId, TextPageLink pageLink) {
        log.debug("Try to find entity views by tenantId [{}], customerId[{}] and pageLink [{}]",
                tenantId, customerId, pageLink);
        List<EntityViewEntity> entityViewEntities = findPageWithTextSearch(
                ENTITY_VIEW_BY_TENANT_AND_CUSTOMER_CF,
                Arrays.asList(eq(CUSTOMER_ID_PROPERTY, customerId), eq(TENANT_ID_PROPERTY, tenantId)),
                pageLink);
        log.trace("Found find entity views [{}] by tenantId [{}], customerId [{}] and pageLink [{}]",
                entityViewEntities, tenantId, customerId, pageLink);
        return DaoUtil.convertDataList(entityViewEntities);
    }

    @Override
    public List<EntityView> findEntityViewsByTenantIdAndCustomerIdAndType(UUID tenantId, UUID customerId, String type, TextPageLink pageLink) {
        log.debug("Try to find entity views by tenantId [{}], customerId[{}], type [{}] and pageLink [{}]",
                tenantId, customerId, type, pageLink);
        List<EntityViewEntity> entityViewEntities = findPageWithTextSearch(
                ENTITY_VIEW_BY_TENANT_AND_CUSTOMER_AND_TYPE_CF,
                Arrays.asList(eq(DEVICE_TYPE_PROPERTY, type), eq(CUSTOMER_ID_PROPERTY, customerId), eq(TENANT_ID_PROPERTY, tenantId)),
                pageLink);
        log.trace("Found find entity views [{}] by tenantId [{}], customerId [{}], type [{}] and pageLink [{}]",
                entityViewEntities, tenantId, customerId, type, pageLink);
        return DaoUtil.convertDataList(entityViewEntities);
    }

    @Override
    public ListenableFuture<List<EntityView>> findEntityViewsByTenantIdAndEntityIdAsync(UUID tenantId, UUID entityId) {
        log.debug("Try to find entity views by tenantId [{}] and entityId [{}]", tenantId, entityId);
        Select.Where query = select().from(ENTITY_VIEW_BY_TENANT_AND_ENTITY_ID_CF).where();
        query.and(eq(TENANT_ID_PROPERTY, tenantId));
        query.and(eq(ENTITY_ID_COLUMN, entityId));
        return findListByStatementAsync(query);
    }

    @Override
    public ListenableFuture<List<EntitySubtype>> findTenantEntityViewTypesAsync(UUID tenantId) {
        Select select = select().from(ENTITY_SUBTYPE_COLUMN_FAMILY_NAME);
        Select.Where query = select.where();
        query.and(eq(ENTITY_SUBTYPE_TENANT_ID_PROPERTY, tenantId));
        query.and(eq(ENTITY_SUBTYPE_ENTITY_TYPE_PROPERTY, EntityType.ENTITY_VIEW));
        query.setConsistencyLevel(cluster.getDefaultReadConsistencyLevel());
        ResultSetFuture resultSetFuture = executeAsyncRead(query);
        return Futures.transform(resultSetFuture, new Function<ResultSet, List<EntitySubtype>>() {
            @Nullable
            @Override
            public List<EntitySubtype> apply(@Nullable ResultSet resultSet) {
                Result<EntitySubtypeEntity> result = cluster.getMapper(EntitySubtypeEntity.class).map(resultSet);
                if (result != null) {
                    List<EntitySubtype> entitySubtypes = new ArrayList<>();
                    result.all().forEach((entitySubtypeEntity) ->
                            entitySubtypes.add(entitySubtypeEntity.toEntitySubtype())
                    );
                    return entitySubtypes;
                } else {
                    return Collections.emptyList();
                }
            }
        });
    }
}
