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

import org.thingsboard.server.common.data.TenantEntity;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.permission.Resource;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public interface PermissionChecker<I extends EntityId, T extends TenantEntity> {

    default boolean hasPermission(SecurityUser user, Resource resource, Operation operation) throws ThingsboardException {
        return false;
    }

    default boolean hasPermission(SecurityUser user, Operation operation, I entityId, T entity) throws ThingsboardException {
        return false;
    }

    default boolean hasPermission(SecurityUser user, Operation operation, I entityId, T entity, EntityGroupId entityGroupId) throws ThingsboardException {
        return false;
    }

    default boolean hasEntityGroupPermission(SecurityUser user, Operation operation, EntityGroup entityGroup) throws ThingsboardException {
        return false;
    }

    public class GenericPermissionChecker<I extends EntityId, T extends TenantEntity> implements PermissionChecker<I, T> {

        private final Set<Operation> allowedOperations;

        public GenericPermissionChecker(Operation... operations) {
            allowedOperations = new HashSet<Operation>(Arrays.asList(operations));
        }

        @Override
        public boolean hasPermission(SecurityUser user, Resource resource, Operation operation) {
            return allowedOperations.contains(Operation.ALL) || allowedOperations.contains(operation);
        }

        @Override
        public boolean hasPermission(SecurityUser user, Operation operation, I entityId, T entity) {
            return allowedOperations.contains(Operation.ALL) || allowedOperations.contains(operation);
        }

        @Override
        public boolean hasPermission(SecurityUser user, Operation operation, I entityId, T entity, EntityGroupId entityGroupId) {
            return allowedOperations.contains(Operation.ALL) || allowedOperations.contains(operation);
        }

        @Override
        public boolean hasEntityGroupPermission(SecurityUser user, Operation operation, EntityGroup entityGroup) {
            return allowedOperations.contains(Operation.ALL) || allowedOperations.contains(operation);
        }

    }

    public static PermissionChecker denyAllPermissionChecker = new PermissionChecker() {
    };

    public static PermissionChecker allowAllPermissionChecker = new PermissionChecker() {

        @Override
        public boolean hasPermission(SecurityUser user, Resource resource, Operation operation) {
            return true;
        }

        @Override
        public boolean hasPermission(SecurityUser user, Operation operation, EntityId entityId, TenantEntity entity) {
            return true;
        }

        @Override
        public boolean hasPermission(SecurityUser user, Operation operation, EntityId entityId, TenantEntity entity, EntityGroupId entityGroupId) {
            return true;
        }

        @Override
        public boolean hasEntityGroupPermission(SecurityUser user, Operation operation, EntityGroup entityGroup) {
            return true;
        }

    };


}
