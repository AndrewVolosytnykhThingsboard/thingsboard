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
package org.thingsboard.server.service.security.permission;

import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.permission.GroupPermission;
import org.thingsboard.server.common.data.permission.MergedUserPermissions;
import org.thingsboard.server.common.data.role.Role;


public interface UserPermissionsService {

    MergedUserPermissions getMergedPermissions(User user, boolean isPublic) throws ThingsboardException;

    void onRoleUpdated(Role role) throws ThingsboardException;

    void onGroupPermissionUpdated(GroupPermission groupPermission) throws ThingsboardException;

    void onGroupPermissionDeleted(GroupPermission groupPermission) throws ThingsboardException;

    void onUserUpdatedOrRemoved(User user) throws ThingsboardException;

}
