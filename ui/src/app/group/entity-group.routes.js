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
/* eslint-disable import/no-unresolved, import/default */

import entityGroupsTemplate from './entity-groups.tpl.html';
import entityGroupTemplate from './entity-group.tpl.html';

/* eslint-enable import/no-unresolved, import/default */

/*@ngInject*/
export default function EntityGroupRoutes($stateProvider, types) {
    $stateProvider
        .state('home.customerGroups', {
            url: '/customerGroups',
            params: {'groupType': types.entityType.customer, 'topIndex': 0},
            module: 'private',
            auth: ['TENANT_ADMIN', 'CUSTOMER_USER'],
            views: {
                "content@home": {
                    templateUrl: entityGroupsTemplate,
                    controller: 'EntityGroupsController',
                    controllerAs: 'vm'
                }
            },
            data: {
                searchEnabled: true,
                pageTitle: 'entity-group.customer-groups'
            },
            ncyBreadcrumb: {
                label: '{"icon": "supervisor_account", "label": "entity-group.customer-groups"}'
            }
        })
        .state('home.customerGroups.customerGroup', {
            url: '/:entityGroupId',
            reloadOnSearch: false,
            module: 'private',
            auth: ['TENANT_ADMIN', 'CUSTOMER_USER'],
            views: {
                "content@home": {
                    templateUrl: entityGroupTemplate,
                    controller: 'EntityGroupController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                entityGroup:
                /*@ngInject*/
                    function($stateParams, $q, entityGroupService, customerGroupConfig) {
                        return constructGroupConfig($stateParams, $q, entityGroupService, customerGroupConfig);
                    }
            },
            data: {
                searchEnabled: false,
                pageTitle: 'entity-group.customer-group'
            },
            ncyBreadcrumb: {
                label: '{"icon": "supervisor_account", "label": "{{ vm.entityGroup.name }}", "translate": "false"}'
            }
        })
        .state('home.assetGroups', {
            url: '/assetGroups',
            params: {'groupType': types.entityType.asset, 'topIndex': 0},
            module: 'private',
            auth: ['TENANT_ADMIN', 'CUSTOMER_USER'],
            views: {
                "content@home": {
                    templateUrl: entityGroupsTemplate,
                    controller: 'EntityGroupsController',
                    controllerAs: 'vm'
                }
            },
            data: {
                searchEnabled: true,
                pageTitle: 'entity-group.asset-groups'
            },
            ncyBreadcrumb: {
                label: '{"icon": "domain", "label": "entity-group.asset-groups"}'
            }
        })
        .state('home.assetGroups.assetGroup', {
            url: '/:entityGroupId',
            reloadOnSearch: false,
            module: 'private',
            auth: ['TENANT_ADMIN', 'CUSTOMER_USER'],
            views: {
                "content@home": {
                    templateUrl: entityGroupTemplate,
                    controller: 'EntityGroupController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                entityGroup:
                /*@ngInject*/
                    function($stateParams, $q, entityGroupService, assetGroupConfig) {
                        return constructGroupConfig($stateParams, $q, entityGroupService, assetGroupConfig);
                    }
            },
            data: {
                searchEnabled: false,
                pageTitle: 'entity-group.asset-group'
            },
            ncyBreadcrumb: {
                label: '{"icon": "domain", "label": "{{ vm.entityGroup.name }}", "translate": "false"}'
            }
        })
        .state('home.deviceGroups', {
            url: '/deviceGroups',
            params: {'groupType': types.entityType.device, 'topIndex': 0},
            module: 'private',
            auth: ['TENANT_ADMIN', 'CUSTOMER_USER'],
            views: {
                "content@home": {
                    templateUrl: entityGroupsTemplate,
                    controller: 'EntityGroupsController',
                    controllerAs: 'vm'
                }
            },
            data: {
                searchEnabled: true,
                pageTitle: 'entity-group.device-groups'
            },
            ncyBreadcrumb: {
                label: '{"icon": "devices_other", "label": "entity-group.device-groups"}'
            }
        })
        .state('home.deviceGroups.deviceGroup', {
            url: '/:entityGroupId',
            reloadOnSearch: false,
            module: 'private',
            auth: ['TENANT_ADMIN', 'CUSTOMER_USER'],
            views: {
                "content@home": {
                    templateUrl: entityGroupTemplate,
                    controller: 'EntityGroupController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                entityGroup:
                    /*@ngInject*/
                    function($stateParams, $q, entityGroupService, deviceGroupConfig) {
                        return constructGroupConfig($stateParams, $q, entityGroupService, deviceGroupConfig);
                    }
            },
            data: {
                searchEnabled: false,
                pageTitle: 'entity-group.device-group'
            },
            ncyBreadcrumb: {
                label: '{"icon": "devices_other", "label": "{{ vm.entityGroup.name }}", "translate": "false"}'
            }
        })
        .state('home.userGroups', {
            url: '/userGroups',
            params: {'groupType': types.entityType.user, 'topIndex': 0},
            module: 'private',
            auth: ['TENANT_ADMIN', 'CUSTOMER_USER'],
            views: {
                "content@home": {
                    templateUrl: entityGroupsTemplate,
                    controller: 'EntityGroupsController',
                    controllerAs: 'vm'
                }
            },
            data: {
                searchEnabled: true,
                pageTitle: 'entity-group.user-groups'
            },
            ncyBreadcrumb: {
                label: '{"icon": "supervisor_account", "label": "entity-group.user-groups"}'
            }
        })
        .state('home.userGroups.userGroup', {
            url: '/:entityGroupId',
            reloadOnSearch: false,
            module: 'private',
            auth: ['TENANT_ADMIN', 'CUSTOMER_USER'],
            views: {
                "content@home": {
                    templateUrl: entityGroupTemplate,
                    controller: 'EntityGroupController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                entityGroup:
                /*@ngInject*/
                    function($stateParams, $q, entityGroupService, userGroupConfig) {
                        return constructGroupConfig($stateParams, $q, entityGroupService, userGroupConfig);
                    }
            },
            data: {
                searchEnabled: false,
                pageTitle: 'entity-group.user-group'
            },
            ncyBreadcrumb: {
                label: '{"icon": "supervisor_account", "label": "{{ vm.entityGroup.name }}", "translate": "false"}'
            }
        }).state('home.entityViewGroups', {
            url: '/entityViewGroups',
            params: {'groupType': types.entityType.entityView, 'topIndex': 0},
            module: 'private',
            auth: ['TENANT_ADMIN', 'CUSTOMER_USER'],
            views: {
                "content@home": {
                    templateUrl: entityGroupsTemplate,
                    controller: 'EntityGroupsController',
                    controllerAs: 'vm'
                }
            },
            data: {
                searchEnabled: true,
                pageTitle: 'entity-group.entity-view-groups'
            },
            ncyBreadcrumb: {
                label: '{"icon": "view_quilt", "label": "entity-group.entity-view-groups"}'
            }
        })
        .state('home.entityViewGroups.entityViewGroup', {
            url: '/:entityGroupId',
            reloadOnSearch: false,
            module: 'private',
            auth: ['TENANT_ADMIN', 'CUSTOMER_USER'],
            views: {
                "content@home": {
                    templateUrl: entityGroupTemplate,
                    controller: 'EntityGroupController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                entityGroup:
                /*@ngInject*/
                    function($stateParams, $q, entityGroupService, entityViewGroupConfig) {
                        return constructGroupConfig($stateParams, $q, entityGroupService, entityViewGroupConfig);
                    }
            },
            data: {
                searchEnabled: false,
                pageTitle: 'entity-group.entity-view-group'
            },
            ncyBreadcrumb: {
                label: '{"icon": "view_quilt", "label": "{{ vm.entityGroup.name }}", "translate": "false"}'
            }
        }).state('home.dashboardGroups', {
            url: '/dashboardGroups',
            params: {'groupType': types.entityType.dashboard, 'topIndex': 0},
            module: 'private',
            auth: ['TENANT_ADMIN', 'CUSTOMER_USER'],
            views: {
                "content@home": {
                    templateUrl: entityGroupsTemplate,
                    controller: 'EntityGroupsController',
                    controllerAs: 'vm'
                }
            },
            data: {
                searchEnabled: true,
                pageTitle: 'entity-group.dashboard-groups'
            },
            ncyBreadcrumb: {
                label: '{"icon": "dashboard", "label": "entity-group.dashboard-groups"}'
            }
        })
        .state('home.dashboardGroups.dashboardGroup', {
            url: '/:entityGroupId',
            reloadOnSearch: false,
            module: 'private',
            auth: ['TENANT_ADMIN', 'CUSTOMER_USER'],
            views: {
                "content@home": {
                    templateUrl: entityGroupTemplate,
                    controller: 'EntityGroupController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                entityGroup:
                /*@ngInject*/
                    function($stateParams, $q, entityGroupService, dashboardGroupConfig) {
                        return constructGroupConfig($stateParams, $q, entityGroupService, dashboardGroupConfig);
                    }
            },
            data: {
                searchEnabled: false,
                pageTitle: 'entity-group.dashboard-group'
            },
            ncyBreadcrumb: {
                label: '{"icon": "dashboard", "label": "{{ vm.entityGroup.name }}", "translate": "false"}'
            }
        });


    function constructGroupConfig($stateParams, $q, entityGroupService, entityGroupConfigFactory) {
        var deferred = $q.defer();
        entityGroupService.getEntityGroup($stateParams.entityGroupId).then(
            (entityGroup) => {
                entityGroupConfigFactory.createConfig($stateParams, entityGroup).then(
                    (entityGroupConfig) => {
                        entityGroup.entityGroupConfig = entityGroupConfig;
                        deferred.resolve(entityGroup);
                    },
                    () => {
                        deferred.reject();
                    }
                );
            },
            () => {
                deferred.reject();
            }
        );
        return deferred.promise;
    }
}
