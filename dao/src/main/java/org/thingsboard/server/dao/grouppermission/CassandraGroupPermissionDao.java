/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 * <p>
 * Copyright © 2016-2018 ThingsBoard, Inc. All Rights Reserved.
 * <p>
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * <p>
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 * <p>
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 * <p>
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
package org.thingsboard.server.dao.grouppermission;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.GroupPermission;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.dao.model.nosql.GroupPermissionEntity;
import org.thingsboard.server.dao.nosql.CassandraAbstractSearchTimeDao;
import org.thingsboard.server.dao.util.NoSqlDao;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static org.thingsboard.server.dao.model.ModelConstants.GROUP_PERMISSION_TABLE_FAMILY_NAME;

@Component
@Slf4j
@NoSqlDao
public class CassandraGroupPermissionDao extends CassandraAbstractSearchTimeDao<GroupPermissionEntity, GroupPermission> implements GroupPermissionDao {

    @Override
    protected Class<GroupPermissionEntity> getColumnFamilyClass() {
        return GroupPermissionEntity.class;
    }

    @Override
    protected String getColumnFamilyName() {
        return GROUP_PERMISSION_TABLE_FAMILY_NAME;
    }

    @Override
    public List<GroupPermission> findGroupPermissionsByTenantId(UUID tenantId, TimePageLink pageLink) {
        log.trace("Try to find group permissions by tenant [{}] and pageLink [{}]", tenantId, pageLink);
        List<GroupPermissionEntity> entities = findPageWithTimeSearch(
                new TenantId(tenantId),
                "group_permission_by_tenant_id",
                Arrays.asList(eq(ModelConstants.GROUP_PERMISSION_TENANT_ID_PROPERTY, tenantId)),
                pageLink);
        log.trace("Found group permissions by tenant [{}] and pageLink [{}]", tenantId, pageLink);
        return DaoUtil.convertDataList(entities);
    }

    @Override
    public List<GroupPermission> findGroupPermissionsByTenantIdAndUserGroupId(UUID tenantId, UUID userGroupId, TimePageLink pageLink) {
        log.trace("Try to find group permissions by tenant [{}], userGroupId [{}] and pageLink [{}]", tenantId, userGroupId, pageLink);
        List<GroupPermissionEntity> entities = findPageWithTimeSearch(
                new TenantId(tenantId),
                "group_permission_by_user_group_id",
                Arrays.asList(eq(ModelConstants.GROUP_PERMISSION_TENANT_ID_PROPERTY, tenantId),
                        eq(ModelConstants.GROUP_PERMISSION_USER_GROUP_ID_PROPERTY, userGroupId)),
                pageLink);
        log.trace("Found group permissions by tenant [{}], userGroupId [{}] and pageLink [{}]", tenantId, userGroupId, pageLink);
        return DaoUtil.convertDataList(entities);
    }
}
