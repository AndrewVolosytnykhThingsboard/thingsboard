/*
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2019 ThingsBoard, Inc. All Rights Reserved.
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
import './datasource-func.scss';

import 'md-color-picker';
import tinycolor from 'tinycolor2';
import $ from 'jquery';
import thingsboardTypes from '../common/types.constant';
import thingsboardUtils from '../common/utils.service';
import thingsboardDatakeyConfigDialog from './datakey-config-dialog.controller';

/* eslint-disable import/no-unresolved, import/default */

import datasourceFuncTemplate from './datasource-func.tpl.html';
import datakeyConfigDialogTemplate from './datakey-config-dialog.tpl.html';

/* eslint-enable import/no-unresolved, import/default */

/* eslint-disable angular/angularelement */

export default angular.module('thingsboard.directives.datasourceFunc', [thingsboardTypes, thingsboardUtils, thingsboardDatakeyConfigDialog])
    .directive('tbDatasourceFunc', DatasourceFunc)
    .name;

/*@ngInject*/
function DatasourceFunc($compile, $templateCache, $mdDialog, $window, $document, $mdColorPicker, types, utils) {

    var linker = function (scope, element, attrs, ngModelCtrl) {
        var template = $templateCache.get(datasourceFuncTemplate);
        element.html(template);

        scope.ngModelCtrl = ngModelCtrl;
        scope.types = types;

        scope.functionTypes = utils.getPredefinedFunctionsList();
        scope.alarmFields = [];
        for (var alarmField in types.alarmFields) {
            scope.alarmFields.push(alarmField);
        }

        scope.selectedDataKey = null;
        scope.dataKeySearchText = null;

        scope.selectedAlarmDataKey = null;
        scope.alarmDataKeySearchText = null;

        scope.updateValidity = function () {
            if (ngModelCtrl.$viewValue) {
                var value = ngModelCtrl.$viewValue;
                var dataValid = angular.isDefined(value) && value != null;
                ngModelCtrl.$setValidity('deviceData', dataValid);
                if (dataValid) {
                    if (scope.optDataKeys) {
                        ngModelCtrl.$setValidity('datasourceKeys', true);
                    } else {
                        ngModelCtrl.$setValidity('datasourceKeys',
                            angular.isDefined(value.dataKeys) &&
                            value.dataKeys != null &&
                            value.dataKeys.length > 0);
                    }
                }
            }
        };

        scope.$watch('funcDataKeys', function () {
            updateDataKeys();
        }, true);

        scope.$watch('alarmDataKeys', function () {
            updateDataKeys();
        }, true);

        function updateDataKeys() {
            if (ngModelCtrl.$viewValue) {
                var dataKeys = [];
                dataKeys = dataKeys.concat(scope.funcDataKeys);
                dataKeys = dataKeys.concat(scope.alarmDataKeys);
                if (ngModelCtrl.$viewValue.dataKeys != dataKeys)
                {
                   ngModelCtrl.$setDirty();
                   ngModelCtrl.$viewValue.dataKeys = dataKeys;
                }
                scope.updateValidity();
            }
        }

        scope.$watch('datasourceName', function () {
            if (ngModelCtrl.$viewValue) {
                ngModelCtrl.$viewValue.name = scope.datasourceName;
                scope.updateValidity();
            }
        });

        ngModelCtrl.$render = function () {
            if (ngModelCtrl.$viewValue) {
                var funcDataKeys = [];
                var alarmDataKeys = [];
                if (ngModelCtrl.$viewValue.dataKeys) {
                    for (var d=0;d<ngModelCtrl.$viewValue.dataKeys.length;d++) {
                        var dataKey = ngModelCtrl.$viewValue.dataKeys[d];
                        if (dataKey.type === types.dataKeyType.function) {
                            funcDataKeys.push(dataKey);
                        } else if (dataKey.type === types.dataKeyType.alarm) {
                            alarmDataKeys.push(dataKey);
                        }
                    }
                }
                scope.funcDataKeys = funcDataKeys;
                scope.alarmDataKeys = alarmDataKeys;
                scope.datasourceName = ngModelCtrl.$viewValue.name;
            }
        };

        scope.transformFuncDataKeyChip = function (chip) {
            if (scope.maxDataKeys > 0 && ngModelCtrl.$viewValue.dataKeys.length >= scope.maxDataKeys ) {
                return null;
            } else {
                return scope.generateDataKey({chip: chip, type: types.dataKeyType.function});
            }
        };

        scope.transformAlarmDataKeyChip = function (chip) {
            return scope.generateDataKey({chip: chip, type: types.dataKeyType.alarm});
        };

        scope.showColorPicker = function (event, dataKey) {
            $mdColorPicker.show({
                value: dataKey.color,
                defaultValue: '#fff',
                random: tinycolor.random(),
                clickOutsideToClose: false,
                hasBackdrop: false,
                multiple: true,
                preserveScope: false,

                mdColorAlphaChannel: true,
                mdColorSpectrum: true,
                mdColorSliders: true,
                mdColorGenericPalette: false,
                mdColorMaterialPalette: true,
                mdColorHistory: false,
                mdColorDefaultTab: 2,

                $event: event

            }).then(function (color) {
                dataKey.color = color;
                ngModelCtrl.$setDirty();
            });
        }

        scope.editDataKey = function (event, dataKey, index) {

            $mdDialog.show({
                controller: 'DatakeyConfigDialogController',
                controllerAs: 'vm',
                templateUrl: datakeyConfigDialogTemplate,
                locals: {
                    dataKey: angular.copy(dataKey),
                    dataKeySettingsSchema: scope.datakeySettingsSchema,
                    entityAlias: null,
                    aliasController: null
                },
                parent: angular.element($document[0].body),
                fullscreen: true,
                targetEvent: event,
                multiple: true,
                onComplete: function () {
                    var w = angular.element($window);
                    w.triggerHandler('resize');
                }
            }).then(function (dataKey) {
                if (dataKey.type === types.dataKeyType.function) {
                    scope.funcDataKeys[index] = dataKey;
                } else if (dataKey.type === types.dataKeyType.alarm) {
                    scope.alarmDataKeys[index] = dataKey;
                }
                ngModelCtrl.$setDirty();
            }, function () {
            });
        };

        scope.textIsNotEmpty = function(text) {
            return (text && text != null && text.length > 0) ? true : false;
        }

        scope.dataKeysSearch = function (dataKeySearchText) {
            var targetKeys = scope.widgetType == types.widgetType.alarm.value ? scope.alarmFields : scope.functionTypes;
            var dataKeys = dataKeySearchText ? targetKeys.filter(
                scope.createFilterForDataKey(dataKeySearchText)) : targetKeys;
            return dataKeys;
        };

        scope.createFilterForDataKey = function (query) {
            var lowercaseQuery = angular.lowercase(query);
            return function filterFn(dataKey) {
                return (angular.lowercase(dataKey).indexOf(lowercaseQuery) === 0);
            };
        };

        scope.createKey = function (event, chipsId) {
            var chipsChild = $(chipsId, element)[0].firstElementChild;
            var el = angular.element(chipsChild);
            var chipBuffer = el.scope().$mdChipsCtrl.getChipBuffer();
            event.preventDefault();
            event.stopPropagation();
            el.scope().$mdChipsCtrl.appendChip(chipBuffer.trim());
            el.scope().$mdChipsCtrl.resetChipBuffer();
        }

        $compile(element.contents())(scope);
    }

    return {
        restrict: "E",
        require: "^ngModel",
        scope: {
            widgetType: '=',
            maxDataKeys: '=',
            optDataKeys: '=',
            generateDataKey: '&',
            datakeySettingsSchema: '='
        },
        link: linker
    };
}

/* eslint-enable angular/angularelement */
