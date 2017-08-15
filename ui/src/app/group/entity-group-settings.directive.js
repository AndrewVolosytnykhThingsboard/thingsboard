/*
 * Copyright © 2016-2017 The Thingsboard Authors
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

/* eslint-disable import/no-unresolved, import/default */

import entityGroupSettingsTemplate from './entity-group-settings.tpl.html';

/* eslint-enable import/no-unresolved, import/default */

/*@ngInject*/
export default function EntityGroupSettings() {
    return {
        restrict: "E",
        scope: true,
        bindToController: {
            settings: '=',
            entityType: '=',
            isEdit: '=',
            theForm: '='
        },
        controller: EntityGroupSettingsController,
        controllerAs: 'vm',
        templateUrl: entityGroupSettingsTemplate
    };
}

/*@ngInject*/
function EntityGroupSettingsController($scope, utils, types) {

    var vm = this;
    vm.types = types;

    $scope.$watch('vm.settings', (newVal) => {
       if (newVal) {
           setDefaults();
       }
    });

    function setDefaults() {
        vm.settings = utils.groupSettingsDefaults(vm.entityType, vm.settings);
    }
}