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
package org.thingsboard.server.dao.role;

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
import org.thingsboard.server.common.data.Role;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.model.EntitySubtypeEntity;
import org.thingsboard.server.dao.model.nosql.RoleEntity;
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
import static org.thingsboard.server.dao.model.ModelConstants.ENTITY_SUBTYPE_COLUMN_FAMILY_NAME;
import static org.thingsboard.server.dao.model.ModelConstants.ENTITY_SUBTYPE_ENTITY_TYPE_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ENTITY_SUBTYPE_TENANT_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ROLE_BY_TENANT_AND_NAME;
import static org.thingsboard.server.dao.model.ModelConstants.ROLE_BY_TENANT_AND_SEARCH_TEXT_CF;
import static org.thingsboard.server.dao.model.ModelConstants.ROLE_BY_TENANT_BY_TYPE_AND_SEARCH_TEXT_CF;
import static org.thingsboard.server.dao.model.ModelConstants.ROLE_NAME_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ROLE_TABLE_FAMILY_NAME;
import static org.thingsboard.server.dao.model.ModelConstants.ROLE_TENANT_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ROLE_TYPE_PROPERTY;

@Component
@Slf4j
@NoSqlDao
public class CassandraRoleDao extends CassandraAbstractSearchTextDao<RoleEntity, Role> implements RoleDao {

    @Override
    protected Class<RoleEntity> getColumnFamilyClass() {
        return RoleEntity.class;
    }

    @Override
    protected String getColumnFamilyName() {
        return ROLE_TABLE_FAMILY_NAME;
    }

    @Override
    public Role save(TenantId tenantId ,Role role) {
        Role savedRole = super.save(role.getTenantId(), role);
        EntitySubtype entitySubtype = new EntitySubtype(savedRole.getTenantId(), EntityType.ROLE, savedRole.getType());
        EntitySubtypeEntity entitySubtypeEntity = new EntitySubtypeEntity(entitySubtype);
        Statement saveStatement = cluster.getMapper(EntitySubtypeEntity.class).saveQuery(entitySubtypeEntity);
        executeWrite(savedRole.getTenantId(), saveStatement);
        return savedRole;
    }

    @Override
    public List<Role> findRolesByTenantId(UUID tenantId, TextPageLink pageLink) {
        log.debug("Try to find roles by tenantId [{}] and pageLink [{}]", tenantId, pageLink);
        List<RoleEntity> roleEntities =
                findPageWithTextSearch(new TenantId(tenantId), ROLE_BY_TENANT_AND_SEARCH_TEXT_CF,
                        Collections.singletonList(eq(ROLE_TENANT_ID_PROPERTY, tenantId)), pageLink);
        log.trace("Found roles [{}] by tenantId [{}] and pageLink [{}]",
                roleEntities, tenantId, pageLink);
        return DaoUtil.convertDataList(roleEntities);
    }

    @Override
    public List<Role> findRolesByTenantIdAndType(UUID tenantId, String type, TextPageLink pageLink) {
        log.debug("Try to find roles by tenantId [{}], type [{}] and pageLink [{}]", tenantId, type, pageLink);
        List<RoleEntity> roleEntities =
                findPageWithTextSearch(new TenantId(tenantId), ROLE_BY_TENANT_BY_TYPE_AND_SEARCH_TEXT_CF,
                        Arrays.asList(eq(ROLE_TYPE_PROPERTY, type),
                                eq(ROLE_TENANT_ID_PROPERTY, tenantId)), pageLink);
        log.trace("Found roles [{}] by tenantId [{}], type [{}] and pageLink [{}]",
                roleEntities, tenantId, type, pageLink);
        return DaoUtil.convertDataList(roleEntities);
    }

    @Override
    public Optional<Role> findRoleByTenantIdAndName(UUID tenantId, String name) {
        Select.Where query = select().from(ROLE_BY_TENANT_AND_NAME).where();
        query.and(eq(ROLE_TENANT_ID_PROPERTY, tenantId));
        query.and(eq(ROLE_NAME_PROPERTY, name));
        return Optional.ofNullable(DaoUtil.getData(findOneByStatement(new TenantId(tenantId), query)));
    }

    @Override
    public ListenableFuture<List<EntitySubtype>> findTenantRoleTypesAsync(UUID tenantId) {
        Select select = select().from(ENTITY_SUBTYPE_COLUMN_FAMILY_NAME);
        Select.Where query = select.where();
        query.and(eq(ENTITY_SUBTYPE_TENANT_ID_PROPERTY, tenantId));
        query.and(eq(ENTITY_SUBTYPE_ENTITY_TYPE_PROPERTY, EntityType.ROLE));
        query.setConsistencyLevel(cluster.getDefaultReadConsistencyLevel());
        ResultSetFuture resultSetFuture = executeAsyncRead(new TenantId(tenantId), query);
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
