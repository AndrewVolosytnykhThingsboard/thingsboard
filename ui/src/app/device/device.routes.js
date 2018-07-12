/*
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2018 ThingsBoard, Inc.. All Rights Reserved.
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

import devicesTemplate from './devices.tpl.html';

/* eslint-enable import/no-unresolved, import/default */

/*@ngInject*/
export default function DeviceRoutes($stateProvider, types) {
    $stateProvider
        .state('home.devices', {
            url: '/devices',
            params: {'topIndex': 0},
            module: 'private',
            auth: ['TENANT_ADMIN', 'CUSTOMER_USER'],
            views: {
                "content@home": {
                    templateUrl: devicesTemplate,
                    controller: 'DeviceController',
                    controllerAs: 'vm'
                }
            },
            data: {
                devicesType: 'tenant',
                searchEnabled: true,
                searchByEntitySubtype: true,
                searchEntityType: types.entityType.device,
                pageTitle: 'device.devices'
            },
            ncyBreadcrumb: {
                label: '{"icon": "devices_other", "label": "device.devices"}'
            }
        })
        .state('home.customers.devices', {
            url: '/:customerId/devices',
            params: {'topIndex': 0},
            module: 'private',
            auth: ['TENANT_ADMIN'],
            views: {
                "content@home": {
                    templateUrl: devicesTemplate,
                    controllerAs: 'vm',
                    controller: 'DeviceController'
                }
            },
            data: {
                devicesType: 'customer',
                searchEnabled: true,
                searchByEntitySubtype: true,
                searchEntityType: types.entityType.device,
                pageTitle: 'customer.devices'
            },
            ncyBreadcrumb: {
                label: '{"icon": "devices_other", "label": "{{ vm.customerDevicesTitle }}", "translate": "false"}'
            }
        })
        .state('home.customerGroups.customerGroup.devices', {
            url: '/:customerId/devices',
            params: {'topIndex': 0},
            module: 'private',
            auth: ['TENANT_ADMIN'],
            views: {
                "content@home": {
                    templateUrl: devicesTemplate,
                    controllerAs: 'vm',
                    controller:
                    /*@ngInject*/
                        function($scope, $stateParams, $controller, entityGroup) {
                            var ctrl = $controller('DeviceController as vm',{$scope: $scope, $stateParams: $stateParams});
                            ctrl.entityGroup = entityGroup;
                            return ctrl;
                    }
                }
            },
            data: {
                devicesType: 'customer',
                searchEnabled: true,
                searchByEntitySubtype: true,
                searchEntityType: types.entityType.device,
                pageTitle: 'customer.devices'
            },
            ncyBreadcrumb: {
                label: '{"icon": "devices_other", "label": "{{ vm.customerDevicesTitle }}", "translate": "false"}'
            }
        });


}
