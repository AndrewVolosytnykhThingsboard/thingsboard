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

/*@ngInject*/
export default function DeviceGroupConfig($q, $translate, tbDialogs, utils, userService, deviceService) {

    var service = {
        createConfig: createConfig
    }

    return service;

    function createConfig(params, entityGroup) {
        var deferred = $q.defer();

        var authority = userService.getAuthority();

        var entityScope = 'tenant';
        if (authority === 'CUSTOMER_USER') {
            entityScope = 'customer_user';
        }

        var groupConfig = {

            entityScope: entityScope,

            tableTitle: entityGroup.name + ': ' + $translate.instant('device.devices'),

            loadEntity: (entityId) => {return deviceService.getDevice(entityId)},
            saveEntity: (entity) => {return deviceService.saveDevice(entity)},
            deleteEntity: (entityId) => {return deviceService.deleteDevice(entityId)},

            addEnabled: () => {
                return true;
            },

            detailsReadOnly: () => {
                return false;
            },
            deleteEnabled: () => {
                return true;
            },
            entitiesDeleteEnabled: () => {
                return true;
            },
            deleteEntityTitle: (entity) => {
                return $translate.instant('device.delete-device-title', {deviceName: entity.name});
            },
            deleteEntityContent: (/*entity*/) => {
                return $translate.instant('device.delete-device-text');
            },
            deleteEntitiesTitle: (count) => {
                return $translate.instant('device.delete-devices-title', {count: count}, 'messageformat');
            },
            deleteEntitiesContent: (/*count*/) => {
                return $translate.instant('device.delete-devices-text');
            }
        };

        groupConfig.onManageCredentials = (event, entity) => {
            var isReadOnly = entityScope == 'customer_user' ? true : false;
            tbDialogs.manageDeviceCredentials(event, entity, isReadOnly);
        };

        groupConfig.onAssignToCustomer = (event, entity) => {
            tbDialogs.assignDevicesToCustomer(event, [entity.id.id]).then(
                () => { groupConfig.onEntityUpdated(entity.id.id, true); }
            );
        };

        groupConfig.onUnassignFromCustomer = (event, entity, isPublic) => {
            tbDialogs.unassignDeviceFromCustomer(event, entity, isPublic).then(
                () => { groupConfig.onEntityUpdated(entity.id.id, true); }
            );
        };

        groupConfig.onMakePublic = (event, entity) => {
            tbDialogs.makeDevicePublic(event, entity).then(
                () => { groupConfig.onEntityUpdated(entity.id.id, true); }
            );
        };

        groupConfig.actionCellDescriptors = [
            {
                name: $translate.instant(entityScope == 'tenant' ? 'device.manage-credentials' : 'device.view-credentials'),
                icon: 'security',
                isEnabled: () => {
                    return true;
                },
                onAction: ($event, entity) => {
                    var isReadOnly = entityScope == 'customer_user' ? true : false;
                    tbDialogs.manageDeviceCredentials($event, entity, isReadOnly);
                }
            }
        ];

        groupConfig.groupActionDescriptors = [
            {
                name: $translate.instant('device.assign-devices'),
                icon: "assignment_ind",
                isEnabled: () => {
                    return true;
                },
                onAction: (event, entities) => {
                    var deviceIds = [];
                    entities.forEach((entity) => {
                        deviceIds.push(entity.id.id);
                    });
                    tbDialogs.assignDevicesToCustomer(event, deviceIds).then(
                        () => { groupConfig.onEntitiesUpdated(deviceIds, true); }
                    );
                },
            },
            {
                name: $translate.instant('device.unassign-devices'),
                icon: "assignment_return",
                isEnabled: () => {
                    return true;
                },
                onAction: (event, entities) => {
                    var deviceIds = [];
                    entities.forEach((entity) => {
                        deviceIds.push(entity.id.id);
                    });
                    tbDialogs.unassignDevicesFromCustomer(event, deviceIds).then(
                        () => { groupConfig.onEntitiesUpdated(deviceIds, true); }
                    );
                },
            }
        ];

        utils.groupConfigDefaults(groupConfig);

        deferred.resolve(groupConfig);
        return deferred.promise;
    }

}