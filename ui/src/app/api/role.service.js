/*
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
import thingsboardTypes from '../common/types.constant';

export default angular.module('thingsboard.api.role', [thingsboardTypes])
    .factory('roleService', RoleService)
    .name;

/*@ngInject*/
function RoleService($http, $q, $window, userService, attributeService, types) {

    var service = {
        deleteRole: deleteRole,
        getRole: getRole,
        getRoles: getRoles,
        saveRole: saveRole,
        getRoleAttributes: getRoleAttributes,
        subscribeForRoleAttributes: subscribeForRoleAttributes,
        unsubscribeForRoleAttributes: unsubscribeForRoleAttributes,
        findByQuery: findByQuery,
        getRoleTypes: getRoleTypes,
        getGroupPermissions: getGroupPermissions,
        saveGroupPermission: saveGroupPermission,
        deleteGroupPermission: deleteGroupPermission,
        deleteGroupPermissions: deleteGroupPermissions
    }

    return service;

    function getRoles(pageLink, config, type) {
        var deferred = $q.defer();
        var url = '/api/tenant/roles?limit=' + pageLink.limit;
        if (angular.isDefined(pageLink.textSearch)) {
            url += '&textSearch=' + pageLink.textSearch;
        }
        if (angular.isDefined(pageLink.idOffset)) {
            url += '&idOffset=' + pageLink.idOffset;
        }
        if (angular.isDefined(pageLink.textOffset)) {
            url += '&textOffset=' + pageLink.textOffset;
        }
        if (angular.isDefined(type) && type.length) {
            url += '&type=' + type;
        }
        $http.get(url, config).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function getRole(roleId, ignoreErrors, config) {
        var deferred = $q.defer();
        var url = '/api/role/' + roleId;
        if (!config) {
            config = {};
        }
        config = Object.assign(config, { ignoreErrors: ignoreErrors });
        $http.get(url, config).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail(response) {
            deferred.reject(response.data);
        });
        return deferred.promise;
    }

    function saveRole(role) {
        var deferred = $q.defer();
        var url = '/api/role';

        $http.post(url, role).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function deleteRole(roleId) {
        var deferred = $q.defer();
        var url = '/api/role/' + roleId;
        $http.delete(url).then(function success() {
            deferred.resolve();
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function getRoleAttributes(roleId, attributeScope, query, successCallback, config) {
        return attributeService.getEntityAttributes(types.entityType.role, roleId, attributeScope, query, successCallback, config);
    }

    function subscribeForRoleAttributes(roleId, attributeScope) {
        return attributeService.subscribeForEntityAttributes(types.entityType.role, roleId, attributeScope);
    }

    function unsubscribeForRoleAttributes(subscriptionId) {
        attributeService.unsubscribeForEntityAttributes(subscriptionId);
    }

    function findByQuery(query, ignoreErrors, config) {
        var deferred = $q.defer();
        var url = '/api/roles';
        if (!config) {
            config = {};
        }
        config = Object.assign(config, { ignoreErrors: ignoreErrors });
        $http.post(url, query, config).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function getRoleTypes(config) {
        var deferred = $q.defer();
        var url = '/api/role/types';
        $http.get(url, config).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function saveGroupPermission(groupPermission) {
        var deferred = $q.defer();
        var url = '/api/groupPermission';

        $http.post(url, groupPermission).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function deleteGroupPermissions(groupPermissionIds) {
        var deferred = $q.defer();
        var tasks = [];
        for (var i = 0; i < groupPermissionIds.length; i++) {
            var groupPermissionId = groupPermissionIds[i];
            if (groupPermissionId) {
                tasks.push(deleteGroupPermission(groupPermissionId));
            }
        }
        if (tasks.length) {
            $q.all(tasks).then(
                () => {
                    deferred.resolve();
                },
                () => {
                    deferred.reject();
                });
        }
        return deferred.promise;
    }

    function deleteGroupPermission(groupPermissionId) {
        var deferred = $q.defer();
        var url = '/api/groupPermission/' + groupPermissionId;
        $http.delete(url).then(function success() {
            deferred.resolve();
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }


    function getGroupPermissions(userGroupId, pageLink, fetchOriginator, ascOrder, config) {
        var deferred = $q.defer();
        var url = '/api/groupPermissions/' + userGroupId + '?limit=' + pageLink.limit;

        if (angular.isDefined(pageLink.startTime) && pageLink.startTime != null) {
            url += '&startTime=' + pageLink.startTime;
        }
        if (angular.isDefined(pageLink.endTime) && pageLink.endTime != null) {
            url += '&endTime=' + pageLink.endTime;
        }
        if (angular.isDefined(pageLink.idOffset) && pageLink.idOffset != null) {
            url += '&offset=' + pageLink.idOffset;
        }
        if (angular.isDefined(ascOrder) && ascOrder != null) {
            url += '&ascOrder=' + (ascOrder ? 'true' : 'false');
        }
        $http.get(url, config).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

}
