/**
 * Copyright © 2016-2021 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.dao.grouppermission;

import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.permission.GroupPermission;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.dao.Dao;

import java.util.List;
import java.util.UUID;

public interface GroupPermissionDao extends Dao<GroupPermission> {

    GroupPermission save(TenantId tenantId, GroupPermission groupPermission);

    PageData<GroupPermission> findGroupPermissionsByTenantId(UUID tenantId, PageLink pageLink);

    PageData<GroupPermission> findGroupPermissionsByTenantIdAndUserGroupId(UUID tenantId, UUID userGroupId, PageLink pageLink);

    PageData<GroupPermission> findGroupPermissionsByTenantIdAndUserGroupIdAndRoleId(UUID tenantId, UUID userGroupId, UUID roleId, PageLink pageLink);

    PageData<GroupPermission> findGroupPermissionsByTenantIdAndEntityGroupIdAndUserGroupIdAndRoleId(UUID tenantId, UUID entityGroupId, UUID userGroupId, UUID roleId, PageLink pageLink);

    PageData<GroupPermission> findGroupPermissionsByTenantIdAndEntityGroupId(UUID tenantId, UUID entityGroupId, PageLink pageLink);

    PageData<GroupPermission> findGroupPermissionsByTenantIdAndRoleId(UUID tenantId, UUID roleId, PageLink pageLink);

}
