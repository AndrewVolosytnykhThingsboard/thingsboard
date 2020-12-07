/*
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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

import addEntityGroupTemplate from './add-entity-group.tpl.html';
import entityGroupCard from './entity-group-card.tpl.html';
import addEntityGroupsToEdgeTemplate from "./add-entity-groups-to-edge.tpl.html";

/* eslint-enable import/no-unresolved, import/default */

/*@ngInject*/
export function EntityGroupCardController() {

    var vm = this;

    vm.isPublic = function() {
        if (vm.item && vm.item.additionalInfo && vm.item.additionalInfo.isPublic) {
            return true;
        }
        return false;
    }

}


/*@ngInject*/
export function EntityGroupsController($rootScope, $scope, $state, $document, $mdDialog, $filter,
                                       utils, tbDialogs, entityGroupService, customerService, $stateParams,
                                      $q, $translate, types, securityTypes, userPermissionsService) {

    var vm = this;

    vm.customerId = $stateParams.customerId;
    vm.edgeId = $stateParams.edgeId;
    vm.entityGroup = $stateParams.entityGroup;
    if ((vm.customerId || vm.edgeId) && $stateParams.childGroupType) {
        if ($stateParams.targetGroupType) {
            vm.groupType = $stateParams.targetGroupType;
        } else {
            vm.groupType = $stateParams.childGroupType;
        }
    } else {
        vm.groupType = $stateParams.groupType;
    }

    vm.types = types;

    vm.groupResource = securityTypes.groupResourceByGroupType[vm.groupType];

    var entityGroupActionsList = [
        {
            onAction: function ($event, item) {
                makePublic($event, item);
            },
            name: function() { return $translate.instant('action.share') },
            details: function() { return $translate.instant('entity-group.make-public') },
            icon: "share",
            isEnabled: function(item) {
                return securityTypes.publicGroupTypes[vm.groupType]
                       && item
                       && (!item.additionalInfo || !item.additionalInfo.isPublic)
                       && userPermissionsService.isDirectlyOwnedGroup(item)
                       && userPermissionsService.hasEntityGroupPermission(securityTypes.operation.write, item);
            }
        },
        {
            onAction: function ($event, item) {
                makePrivate($event, item);
            },
            name: function() { return $translate.instant('action.make-private') },
            details: function() { return $translate.instant('entity-group.make-private') },
            icon: "reply",
            isEnabled: function(item) {
                return securityTypes.publicGroupTypes[vm.groupType]
                       && item
                       && item.additionalInfo && item.additionalInfo.isPublic
                       && userPermissionsService.isDirectlyOwnedGroup(item)
                       && userPermissionsService.hasEntityGroupPermission(securityTypes.operation.write, item);
            }
        },
        {
            onAction: function ($event, item) {
                vm.grid.openItem($event, item);
            },
            name: function() { return $translate.instant('entity-group.details') },
            details: function() { return $translate.instant('entity-group.entity-group-details') },
            icon: "edit"
        },
        {
            onAction: function ($event, item) {
                vm.grid.deleteItem($event, item);
            },
            name: function() { return $translate.instant('action.delete') },
            details: function() { return $translate.instant('entity-group.delete') },
            icon: "delete",
            isEnabled: function(entityGroup) {
                return !entityGroup.groupAll && userPermissionsService.hasEntityGroupPermission(securityTypes.operation.delete, entityGroup);
            }
        }

    ];

    vm.actionSources = {
        'actionCellButton': {
            name: 'widget-action.action-cell-button',
            multiple: true
        },
        'rowClick': {
            name: 'widget-action.row-click',
            multiple: false
        }
    };

    vm.entityGroupGridConfig = {

        resource: vm.groupResource,

        refreshParamsFunc: null,

        deleteItemTitleFunc: deleteEntityGroupTitle,
        deleteItemContentFunc: deleteEntityGroupText,
        deleteItemsTitleFunc: deleteEntityGroupsTitle,
        deleteItemsActionTitleFunc: deleteEntityGroupsActionTitle,
        deleteItemsContentFunc: deleteEntityGroupsText,

        fetchItemsFunc: fetchEntityGroups,
        saveItemFunc: saveEntityGroup,
        deleteItemFunc: deleteEntityGroup,

        clickItemFunc: openEntityGroup,

        getItemTitleFunc: getEntityGroupTitle,

        itemCardController: 'EntityGroupCardController',
        itemCardTemplateUrl: entityGroupCard,
        parentCtl: vm,

        actionsList: entityGroupActionsList,

        onGridInited: gridInited,

        addItemTemplateUrl: addEntityGroupTemplate,

        addItemText: function() { return $translate.instant('entity-group.add-entity-group-text') },
        noItemsText: function() { return $translate.instant('entity-group.no-entity-groups-text') },
        itemDetailsText: function() { return $translate.instant('entity-group.entity-group-details') },
        isDetailsReadOnly: function(entityGroup) {
            return !userPermissionsService.hasEntityGroupPermission(securityTypes.operation.write, entityGroup);
        },
        isSelectionEnabled: function(entityGroup) {
            return !entityGroup.groupAll && userPermissionsService.hasEntityGroupPermission(securityTypes.operation.delete, entityGroup);
        }
    };

    vm.makePublic = makePublic;
    vm.makePrivate = makePrivate;

    if (angular.isDefined($stateParams.items) && $stateParams.items !== null) {
        vm.deviceGridConfig.items = $stateParams.items;
    }

    if (angular.isDefined($stateParams.topIndex) && $stateParams.topIndex > 0) {
        vm.deviceGridConfig.topIndex = $stateParams.topIndex;
    }

    if ($stateParams.hierarchyView) {
        $stateParams.hierarchyCallbacks.reloadData = () => {
            reload();
        };
    }

    initController();

    function initController() {
        if (vm.edgeId) {
            var fetchEntityGroupsFunction = null;
            var deleteEntityGroupFunction = null;
            fetchEntityGroupsFunction = function (pageLink) {
                var deferred = $q.defer();
                entityGroupService.getEdgeEntityGroups(vm.edgeId, vm.groupType).then(
                    function success(entityGroups) {
                        utils.filterSearchTextEntities(entityGroups, 'name', pageLink, deferred);
                    },
                    function fail() {
                        deferred.reject();
                    }
                );
                return deferred.promise;
            };

            deleteEntityGroupFunction = function (entityGroupId) {
                return entityGroupService.unassignEntityGroupFromEdge(vm.edgeId, entityGroupId, vm.groupType);
            };

            entityGroupActionsList = [];
            entityGroupActionsList.push(
                {
                    onAction: function ($event, item) {
                        unassignFromEdge($event, item, vm.edgeId);
                    },
                    name: function() { return $translate.instant('action.unassign') },
                    details: function() { return $translate.instant('edge.unassign-from-edge') },
                    icon: "assignment_return",
                    isEnabled: function (item) {
                        return !item.edgeGroupAll;
                    }
                }
            );

            // ruleChainGroupActionsList.push(
            //     {
            //         onAction: function ($event, items) {
            //             unassignRuleChainsFromEdge($event, items, edgeId);
            //         },
            //         name: function() { return $translate.instant('rulechain.unassign-rulechains') },
            //         details: function(selectedCount) {
            //             return $translate.instant('rulechain.unassign-rulechains-from-edge-action-title', {count: selectedCount}, "messageformat");
            //         },
            //         icon: "assignment_return"
            //     }
            // );

            vm.entityGroupGridConfig.addItemAction = {
                onAction: function ($event) {
                    addEntityGroupsToEdge($event);
                },
                name: function() { return $translate.instant('entity-group.assign-entity-groups') },
                details: function() { return $translate.instant('entity-group.assign-new-entity-group') },
                icon: "add"
            }
            vm.entityGroupGridConfig.addItemActions = [];
            vm.entityGroupGridConfig.fetchItemsFunc = fetchEntityGroupsFunction;
            vm.entityGroupGridConfig.deleteItemFunc = deleteEntityGroupFunction;
            vm.entityGroupGridConfig.actionsList = entityGroupActionsList;
        }
    }

    function deleteEntityGroupTitle(entityGroup) {
        return $translate.instant('entity-group.delete-entity-group-title', {entityGroupName: entityGroup.name});
    }

    function deleteEntityGroupText() {
        return $translate.instant('entity-group.delete-entity-group-text');
    }

    function deleteEntityGroupsTitle(selectedCount) {
        if (vm.edgeId) {
            return $translate.instant('entity-group.unassign-entity-groups-from-edge-title', {count: selectedCount}, 'messageformat');
        } else {
            return $translate.instant('entity-group.delete-entity-groups-title', {count: selectedCount}, 'messageformat');
        }
    }

    function deleteEntityGroupsActionTitle(selectedCount) {
        if (vm.edgeId) {
            return $translate.instant('entity-group.unassign-entity-groups-action-title', {count: selectedCount}, 'messageformat');
        } else {
            return $translate.instant('entity-group.delete-entity-groups-action-title', {count: selectedCount}, 'messageformat');
        }
    }

    function deleteEntityGroupsText () {
        if (vm.edgeId) {
            return $translate.instant('entity-group.unassign-entity-groups-from-edge-text');
        } else {
            return $translate.instant('entity-group.delete-entity-groups-text');
        }
    }

    function gridInited(grid) {
        vm.grid = grid;
        if ($stateParams.hierarchyView && $stateParams.hierarchyCallbacks.viewLoaded) {
            $stateParams.hierarchyCallbacks.viewLoaded();
        }
    }

    function getEntityGroupTitle(entityGroup) {
        return entityGroup ? utils.customTranslation(entityGroup.name, entityGroup.name) : '';
    }

    function fetchEntityGroups(pageLink) {
        var deferred = $q.defer();
        var fetchPromise;
        var fetchPromiseTenant;
        if (vm.customerId) {
            fetchPromise = entityGroupService.getEntityGroupsByOwnerId(types.entityType.customer, vm.customerId, vm.groupType);
            if (vm.edgeId) {
                fetchPromiseTenant = entityGroupService.getEntityGroups(vm.groupType);
            }
        } else {
            fetchPromise = entityGroupService.getEntityGroups(vm.groupType);
        }
        if (vm.edgeId && vm.customerId) {
            $q.all([fetchPromise, fetchPromiseTenant]).then(
                function success(entityGroups) {
                    utils.filterSearchTextEntities(entityGroups[0].concat(entityGroups[1]), 'name', pageLink, deferred);
                },
                function fail() {
                    deferred.reject();
                }
            );
        } else {
            fetchPromise.then(
                function success(entityGroups) {
                    utils.filterSearchTextEntities(entityGroups, 'name', pageLink, deferred);
                },
                function fail() {
                    deferred.reject();
                }
            );
        }
        return deferred.promise;
    }

    function saveEntityGroup(entityGroup) {
        var deferred = $q.defer();
        entityGroup.type = vm.groupType;
        if (vm.customerId) {
            entityGroup.ownerId = {
                entityType: types.entityType.customer,
                id: vm.customerId
            };
        }
        entityGroupService.saveEntityGroup(entityGroup).then(
            function success(entityGroup) {
                deferred.resolve(entityGroup);
                if (!vm.customerId) {
                    $rootScope.$broadcast(vm.groupType + 'changed');
                }
                if ($stateParams.hierarchyView && $stateParams.hierarchyCallbacks.refreshEntityGroups) {
                    $stateParams.hierarchyCallbacks.refreshEntityGroups($stateParams.internalId);
                }
            },
            function fail() {
                deferred.reject();
            }
        );
        return deferred.promise;
    }

    function deleteEntityGroup(entityGroupId) {
        var deferred = $q.defer();
        entityGroupService.deleteEntityGroup(entityGroupId).then(
            function success() {
                deferred.resolve();
                if (!vm.customerId) {
                    $rootScope.$broadcast(vm.groupType + 'changed');
                }
                if ($stateParams.hierarchyView && $stateParams.hierarchyCallbacks.refreshEntityGroups) {
                    $stateParams.hierarchyCallbacks.refreshEntityGroups($stateParams.internalId);
                }
            },
            function fail() {
                deferred.reject();
            }
        );
        return deferred.promise;
    }

    function openEntityGroup($event, entityGroup) {
        if ($event) {
            $event.stopPropagation();
        }
        if ($stateParams.hierarchyView && $stateParams.hierarchyCallbacks.groupSelected) {
            $stateParams.hierarchyCallbacks.groupSelected($stateParams.nodeId, entityGroup.id.id);
        } else {
            var targetStatePrefix = 'home.';
            if (vm.edgeId && !vm.customerId) {
                targetStatePrefix = 'home.edgeGroups.edgeGroup.';
            } else if (vm.customerId && !vm.edgeId) {
                targetStatePrefix = 'home.customerGroups.customerGroup.';
            } else if (vm.edgeId && vm.customerId) {
                targetStatePrefix = 'home.customerGroups.customerGroup.edgeGroups.edgeGroup.';
            }
            var targetState;
            if (entityGroup.type == types.entityType.asset) {
                targetState = 'assetGroups.assetGroup';
            } else if (entityGroup.type == types.entityType.device) {
                targetState = 'deviceGroups.deviceGroup';
            } else if (entityGroup.type == types.entityType.customer) {
                targetState = 'customerGroups.customerGroup';
            } else if (entityGroup.type == types.entityType.user) {
                targetState = 'userGroups.userGroup';
            } else if (entityGroup.type == types.entityType.entityView) {
                targetState = 'entityViewGroups.entityViewGroup';
            } else if (entityGroup.type == types.entityType.edge) {
                targetState = 'edgeGroups.edgeGroup';
            } else if (entityGroup.type == types.entityType.dashboard) {
                targetState = 'dashboardGroups.dashboardGroup';
            } else if (entityGroup.type == types.entityType.rulechain) {
                targetState = 'edge.rulechains';
            }
            if (targetState) {
                targetState = targetStatePrefix + targetState;
                if (vm.edgeId || vm.customerId) {
                    if ($stateParams.childGroupType === types.entityType.edge && $stateParams.targetGroupType) {
                        $state.go(targetState, {edgeChildEntityGroupId: entityGroup.id.id, childEntityGroupId: $stateParams.childEntityGroupId, entityGroup: entityGroup});
                    } else {
                        $state.go(targetState, {childEntityGroupId: entityGroup.id.id});
                    }
                } else {
                    $state.go(targetState, {entityGroupId: entityGroup.id.id});
                }
            }
        }
    }

    function makePublic($event, entityGroup) {
        tbDialogs.makeEntityGroupPublic($event, entityGroup).then(
            () => {
                vm.grid.refreshList();
            }
        );
    }

    function makePrivate($event, entityGroup) {
        tbDialogs.makeEntityGroupPrivate($event, entityGroup).then(
            () => {
                vm.grid.refreshList();
            }
        );
    }

    function reload() {
        vm.customerId = $stateParams.customerId;
        vm.edgeId = $stateParams.edgeId;
        if ((vm.customerId || vm.edgeId) && $stateParams.childGroupType) {
            vm.groupType = $stateParams.childGroupType;
        } else {
            vm.groupType = $stateParams.groupType;
        }

        vm.types = types;

        vm.groupResource = securityTypes.groupResourceByGroupType[vm.groupType];
        vm.entityGroupGridConfig.resource = vm.groupResource;

        if (vm.grid) {
            vm.grid.reInit();
        }
        if ($stateParams.hierarchyCallbacks.viewLoaded) {
            $stateParams.hierarchyCallbacks.viewLoaded();
        }
    }

    function addEntityGroupsToEdge($event) {
        if ($event) {
            $event.stopPropagation();
        }
        var pageSize = 10;
        fetchEntityGroups({limit: pageSize, textSearch: ''}).then(
            function success(_entityGroups) {
                var entityGroups = {
                    pageSize: pageSize,
                    data: $filter('filter')(_entityGroups.data, {edgeGroupAll: false, groupAll: false}),
                    nextPageLink: _entityGroups.nextPageLink,
                    selections: {},
                    selectedCount: 0,
                    hasNext: _entityGroups.hasNext,
                    pending: false
                };
                if (entityGroups.hasNext) {
                    entityGroups.nextPageLink.limit = pageSize;
                }
                $mdDialog.show({
                    controller: 'AddEntityGroupsToEdgeController',
                    controllerAs: 'vm',
                    templateUrl: addEntityGroupsToEdgeTemplate,
                    locals: {edgeId: vm.edgeId, entityGroups: entityGroups, groupType: vm.groupType},
                    parent: angular.element($document[0].body),
                    fullscreen: true,
                    targetEvent: $event
                }).then(function () {
                    vm.grid.refreshList();
                }, function () {
                });
            },
            function fail() {
            });
    }

    function unassignFromEdge($event, entityGroup, edgeId) {
        if ($event) {
            $event.stopPropagation();
        }
        var title = $translate.instant('entity-group.unassign-entity-group-from-edge-title', {entityGroupTitle: entityGroup.name});
        var content = $translate.instant('entity-group.unassign-entity-group-from-edge-text');
        var label = $translate.instant('entity-group.unassign-entity-group-from-edge');
        var confirm = $mdDialog.confirm()
            .targetEvent($event)
            .title(title)
            .htmlContent(content)
            .ariaLabel(label)
            .cancel($translate.instant('action.no'))
            .ok($translate.instant('action.yes'));
        $mdDialog.show(confirm).then(function () {
            entityGroupService.unassignEntityGroupFromEdge(edgeId, entityGroup.id.id, vm.groupType).then(function success() {
                vm.grid.refreshList();
            });
        });
    }
}
