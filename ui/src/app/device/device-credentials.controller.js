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
/*@ngInject*/
export default function ManageDeviceCredentialsController(deviceService, $scope, $mdDialog, deviceId, isReadOnly) {

    var vm = this;

    vm.credentialsTypes = [
        {
            name: 'Access token',
            value: 'ACCESS_TOKEN'
        },
        {
            name: 'X.509 Certificate',
            value: 'X509_CERTIFICATE'
        }
    ];

    vm.deviceCredentials = {};
    vm.isReadOnly = isReadOnly;

    vm.valid = valid;
    vm.cancel = cancel;
    vm.save = save;
    vm.clear = clear;

    loadDeviceCredentials();

    function loadDeviceCredentials() {
        deviceService.getDeviceCredentials(deviceId).then(function success(deviceCredentials) {
            vm.deviceCredentials = deviceCredentials;
        });
    }

    function cancel() {
        $mdDialog.cancel();
    }

    function valid() {
        return vm.deviceCredentials &&
               (vm.deviceCredentials.credentialsType === 'ACCESS_TOKEN'
                   && vm.deviceCredentials.credentialsId
                   && vm.deviceCredentials.credentialsId.length > 0
                   || vm.deviceCredentials.credentialsType === 'X509_CERTIFICATE'
                   && vm.deviceCredentials.credentialsValue
                   && vm.deviceCredentials.credentialsValue.length > 0);
    }

    function clear() {
        vm.deviceCredentials.credentialsId = null;
        vm.deviceCredentials.credentialsValue = null;
    }

    function save() {
        deviceService.saveDeviceCredentials(vm.deviceCredentials).then(function success(deviceCredentials) {
            vm.deviceCredentials = deviceCredentials;
            $scope.theForm.$setPristine();
            $mdDialog.hide();
        });
    }
}
