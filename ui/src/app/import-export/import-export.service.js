/*
 * Thingsboard OÜ ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2017 Thingsboard OÜ. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of Thingsboard OÜ and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Thingsboard OÜ
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

import importDialogTemplate from './import-dialog.tpl.html';
import entityAliasesTemplate from '../entity/alias/entity-aliases.tpl.html';

/* eslint-enable import/no-unresolved, import/default */


/* eslint-disable no-undef, angular/window-service, angular/document-service */

/*@ngInject*/
export default function ImportExport($log, $translate, $q, $mdDialog, $document, itembuffer, utils, types, dashboardUtils,
                                     entityService, dashboardService, pluginService, ruleService, widgetService, toast) {


    const JSON_TYPE = {
        mimeType: 'text/json',
        extension: 'json'
    };
    const CSV_TYPE = {
        mimeType: 'attachament/csv',
        extension: 'csv'
    };

    const XLS_TYPE = {
        mimeType: 'application/vnd.ms-excel',
        extension: 'xls'
    };

    const TEMPLATE_XLS = `
        <html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns="http://www.w3.org/TR/REC-html40">
        <meta http-equiv="content-type" content="application/vnd.ms-excel; charset=UTF-8"/>
        <head><!--[if gte mso 9]><xml>
        <x:ExcelWorkbook><x:ExcelWorksheets><x:ExcelWorksheet><x:Name>{title}</x:Name><x:WorksheetOptions><x:DisplayGridlines/></x:WorksheetOptions></x:ExcelWorksheet></x:ExcelWorksheets></x:ExcelWorkbook></xml>
        <![endif]--></head>
        <body>{table}</body></html>`;

    var service = {
        exportDashboard: exportDashboard,
        importDashboard: importDashboard,
        exportWidget: exportWidget,
        importWidget: importWidget,
        exportPlugin: exportPlugin,
        importPlugin: importPlugin,
        exportRule: exportRule,
        importRule: importRule,
        exportWidgetType: exportWidgetType,
        importWidgetType: importWidgetType,
        exportWidgetsBundle: exportWidgetsBundle,
        importWidgetsBundle: importWidgetsBundle,
        exportCsv: exportCsv,
        exportXls: exportXls
    }

    return service;

    // Widgets bundle functions

    function exportWidgetsBundle(widgetsBundleId) {
        widgetService.getWidgetsBundle(widgetsBundleId).then(
            function success(widgetsBundle) {
                var bundleAlias = widgetsBundle.alias;
                var isSystem = widgetsBundle.tenantId.id === types.id.nullUid;
                widgetService.getBundleWidgetTypes(bundleAlias, isSystem).then(
                    function success (widgetTypes) {
                        prepareExport(widgetsBundle);
                        var widgetsBundleItem = {
                           widgetsBundle:  prepareExport(widgetsBundle),
                           widgetTypes: []
                        };
                        for (var t in widgetTypes) {
                            var widgetType = widgetTypes[t];
                            if (angular.isDefined(widgetType.bundleAlias)) {
                                delete widgetType.bundleAlias;
                            }
                            widgetsBundleItem.widgetTypes.push(prepareExport(widgetType));
                        }
                        var name = widgetsBundle.title;
                        name = name.toLowerCase().replace(/\W/g,"_");
                        exportToPc(widgetsBundleItem, name);
                    },
                    function fail (rejection) {
                        var message = rejection;
                        if (!message) {
                            message = $translate.instant('error.unknown-error');
                        }
                        toast.showError($translate.instant('widgets-bundle.export-failed-error', {error: message}));
                    }
                );
            },
            function fail(rejection) {
                var message = rejection;
                if (!message) {
                    message = $translate.instant('error.unknown-error');
                }
                toast.showError($translate.instant('widgets-bundle.export-failed-error', {error: message}));
            }
        );
    }

    function importNextWidgetType(widgetTypes, bundleAlias, index, deferred) {
        if (!widgetTypes || widgetTypes.length <= index) {
            deferred.resolve();
        } else {
            var widgetType = widgetTypes[index];
            widgetType.bundleAlias = bundleAlias;
            widgetService.saveImportedWidgetType(widgetType).then(
                function success() {
                    index++;
                    importNextWidgetType(widgetTypes, bundleAlias, index, deferred);
                },
                function fail() {
                    deferred.reject();
                }
            );

        }
    }

    function importWidgetsBundle($event) {
        var deferred = $q.defer();
        openImportDialog($event, 'widgets-bundle.import', 'widgets-bundle.widgets-bundle-file').then(
            function success(widgetsBundleItem) {
                if (!validateImportedWidgetsBundle(widgetsBundleItem)) {
                    toast.showError($translate.instant('widgets-bundle.invalid-widgets-bundle-file-error'));
                    deferred.reject();
                } else {
                    var widgetsBundle = widgetsBundleItem.widgetsBundle;
                    widgetService.saveWidgetsBundle(widgetsBundle).then(
                        function success(savedWidgetsBundle) {
                            var bundleAlias = savedWidgetsBundle.alias;
                            var widgetTypes = widgetsBundleItem.widgetTypes;
                            importNextWidgetType(widgetTypes, bundleAlias, 0, deferred);
                        },
                        function fail() {
                            deferred.reject();
                        }
                    );
                }
            },
            function fail() {
                deferred.reject();
            }
        );
        return deferred.promise;
    }

    function validateImportedWidgetsBundle(widgetsBundleItem) {
        if (angular.isUndefined(widgetsBundleItem.widgetsBundle)) {
            return false;
        }
        if (angular.isUndefined(widgetsBundleItem.widgetTypes)) {
            return false;
        }
        var widgetsBundle = widgetsBundleItem.widgetsBundle;
        if (angular.isUndefined(widgetsBundle.title)) {
            return false;
        }
        var widgetTypes = widgetsBundleItem.widgetTypes;
        for (var t in widgetTypes) {
            var widgetType = widgetTypes[t];
            if (!validateImportedWidgetType(widgetType)) {
                return false;
            }
        }

        return true;
    }

    // Widget type functions

    function exportWidgetType(widgetTypeId) {
        widgetService.getWidgetTypeById(widgetTypeId).then(
            function success(widgetType) {
                if (angular.isDefined(widgetType.bundleAlias)) {
                    delete widgetType.bundleAlias;
                }
                var name = widgetType.name;
                name = name.toLowerCase().replace(/\W/g,"_");
                exportToPc(prepareExport(widgetType), name);
            },
            function fail(rejection) {
                var message = rejection;
                if (!message) {
                    message = $translate.instant('error.unknown-error');
                }
                toast.showError($translate.instant('widget-type.export-failed-error', {error: message}));
            }
        );
    }

    function importWidgetType($event, bundleAlias) {
        var deferred = $q.defer();
        openImportDialog($event, 'widget-type.import', 'widget-type.widget-type-file').then(
            function success(widgetType) {
                if (!validateImportedWidgetType(widgetType)) {
                    toast.showError($translate.instant('widget-type.invalid-widget-type-file-error'));
                    deferred.reject();
                } else {
                    widgetType.bundleAlias = bundleAlias;
                    widgetService.saveImportedWidgetType(widgetType).then(
                        function success() {
                            deferred.resolve();
                        },
                        function fail() {
                            deferred.reject();
                        }
                    );
                }
            },
            function fail() {
                deferred.reject();
            }
        );
        return deferred.promise;
    }

    function validateImportedWidgetType(widgetType) {
        if (angular.isUndefined(widgetType.name)
            || angular.isUndefined(widgetType.descriptor))
        {
            return false;
        }
        return true;
    }

    // Rule functions

    function exportRule(ruleId) {
        ruleService.getRule(ruleId).then(
            function success(rule) {
                var name = rule.name;
                name = name.toLowerCase().replace(/\W/g,"_");
                exportToPc(prepareExport(rule), name);
            },
            function fail(rejection) {
                var message = rejection;
                if (!message) {
                    message = $translate.instant('error.unknown-error');
                }
                toast.showError($translate.instant('rule.export-failed-error', {error: message}));
            }
        );
    }

    function importRule($event) {
        var deferred = $q.defer();
        openImportDialog($event, 'rule.import', 'rule.rule-file').then(
            function success(rule) {
                if (!validateImportedRule(rule)) {
                    toast.showError($translate.instant('rule.invalid-rule-file-error'));
                    deferred.reject();
                } else {
                    rule.state = 'SUSPENDED';
                    ruleService.saveRule(rule).then(
                        function success() {
                            deferred.resolve();
                        },
                        function fail() {
                            deferred.reject();
                        }
                    );
                }
            },
            function fail() {
                deferred.reject();
            }
        );
        return deferred.promise;
    }

    function validateImportedRule(rule) {
        if (angular.isUndefined(rule.name)
            || angular.isUndefined(rule.pluginToken)
            || angular.isUndefined(rule.filters)
            || angular.isUndefined(rule.action))
        {
            return false;
        }
        return true;
    }

    // Plugin functions

    function exportPlugin(pluginId) {
        pluginService.getPlugin(pluginId).then(
            function success(plugin) {
                if (!plugin.configuration || plugin.configuration === null) {
                    plugin.configuration = {};
                }
                var name = plugin.name;
                name = name.toLowerCase().replace(/\W/g,"_");
                exportToPc(prepareExport(plugin), name);
            },
            function fail(rejection) {
                var message = rejection;
                if (!message) {
                    message = $translate.instant('error.unknown-error');
                }
                toast.showError($translate.instant('plugin.export-failed-error', {error: message}));
            }
        );
    }

    function importPlugin($event) {
        var deferred = $q.defer();
        openImportDialog($event, 'plugin.import', 'plugin.plugin-file').then(
            function success(plugin) {
                if (!validateImportedPlugin(plugin)) {
                    toast.showError($translate.instant('plugin.invalid-plugin-file-error'));
                    deferred.reject();
                } else {
                    plugin.state = 'SUSPENDED';
                    pluginService.savePlugin(plugin).then(
                        function success() {
                            deferred.resolve();
                        },
                        function fail() {
                            deferred.reject();
                        }
                    );
                }
            },
            function fail() {
                deferred.reject();
            }
        );
        return deferred.promise;
    }

    function validateImportedPlugin(plugin) {
        if (angular.isUndefined(plugin.name)
            || angular.isUndefined(plugin.clazz)
            || angular.isUndefined(plugin.apiToken)
            || angular.isUndefined(plugin.configuration))
        {
            return false;
        }
        return true;
    }

    // Widget functions

    function exportWidget(dashboard, sourceState, sourceLayout, widget) {
        var widgetItem = itembuffer.prepareWidgetItem(dashboard, sourceState, sourceLayout, widget);
        var name = widgetItem.widget.config.title;
        name = name.toLowerCase().replace(/\W/g,"_");
        exportToPc(prepareExport(widgetItem), name);
    }

    function prepareAliasesInfo(aliasesInfo) {
        var datasourceAliases = aliasesInfo.datasourceAliases;
        var targetDeviceAliases = aliasesInfo.targetDeviceAliases;
        var datasourceIndex;
        if (datasourceAliases || targetDeviceAliases) {
            if (datasourceAliases) {
                for (datasourceIndex in datasourceAliases) {
                    datasourceAliases[datasourceIndex] = prepareEntityAlias(datasourceAliases[datasourceIndex]);
                }
            }
            if (targetDeviceAliases) {
                for (datasourceIndex in targetDeviceAliases) {
                    targetDeviceAliases[datasourceIndex] = prepareEntityAlias(targetDeviceAliases[datasourceIndex]);
                }
            }
        }
        return aliasesInfo;
    }

    function prepareEntityAlias(aliasInfo) {
        var alias;
        var filter;
        if (aliasInfo.deviceId) {
            alias = aliasInfo.aliasName;
            filter = {
                type: types.aliasFilterType.entityList.value,
                entityType: types.entityType.device,
                entityList: [aliasInfo.deviceId],
                resolveMultiple: false
            };
        } else if (aliasInfo.deviceFilter) {
            alias = aliasInfo.aliasName;
            filter = {
                type: aliasInfo.deviceFilter.useFilter ? types.aliasFilterType.entityName.value : types.aliasFilterType.entityList.value,
                entityType: types.entityType.device,
                resolveMultiple: false
            }
            if (filter.type == types.aliasFilterType.entityList.value) {
                filter.entityList = aliasInfo.deviceFilter.deviceList
            } else {
                filter.entityNameFilter = aliasInfo.deviceFilter.deviceNameFilter;
            }
        } else if (aliasInfo.entityFilter) {
            alias = aliasInfo.aliasName;
            filter = {
                type: aliasInfo.entityFilter.useFilter ? types.aliasFilterType.entityName.value : types.aliasFilterType.entityList.value,
                entityType: aliasInfo.entityType,
                resolveMultiple: false
            }
            if (filter.type == types.aliasFilterType.entityList.value) {
                filter.entityList = aliasInfo.entityFilter.entityList;
            } else {
                filter.entityNameFilter = aliasInfo.entityFilter.entityNameFilter;
            }
        } else {
            alias = aliasInfo.alias;
            filter = aliasInfo.filter;
        }
        return {
            alias: alias,
            filter: filter
        };
    }

    function importWidget($event, dashboard, targetState, targetLayoutFunction, onAliasesUpdateFunction) {
        var deferred = $q.defer();
        openImportDialog($event, 'dashboard.import-widget', 'dashboard.widget-file').then(
            function success(widgetItem) {
                if (!validateImportedWidget(widgetItem)) {
                    toast.showError($translate.instant('dashboard.invalid-widget-file-error'));
                    deferred.reject();
                } else {
                    var widget = widgetItem.widget;
                    widget = dashboardUtils.validateAndUpdateWidget(widget);
                    var aliasesInfo = prepareAliasesInfo(widgetItem.aliasesInfo);
                    var originalColumns = widgetItem.originalColumns;
                    var originalSize = widgetItem.originalSize;

                    var datasourceAliases = aliasesInfo.datasourceAliases;
                    var targetDeviceAliases = aliasesInfo.targetDeviceAliases;
                    if (datasourceAliases || targetDeviceAliases) {
                        var entityAliases = {};
                        var datasourceAliasesMap = {};
                        var targetDeviceAliasesMap = {};
                        var aliasId;
                        var datasourceIndex;
                        if (datasourceAliases) {
                            for (datasourceIndex in datasourceAliases) {
                                aliasId = utils.guid();
                                datasourceAliasesMap[aliasId] = datasourceIndex;
                                entityAliases[aliasId] = datasourceAliases[datasourceIndex];
                                entityAliases[aliasId].id = aliasId;
                            }
                        }
                        if (targetDeviceAliases) {
                            for (datasourceIndex in targetDeviceAliases) {
                                aliasId = utils.guid();
                                targetDeviceAliasesMap[aliasId] = datasourceIndex;
                                entityAliases[aliasId] = targetDeviceAliases[datasourceIndex];
                                entityAliases[aliasId].id = aliasId;
                            }
                        }

                        var aliasIds = Object.keys(entityAliases);
                        if (aliasIds.length > 0) {
                            processEntityAliases(entityAliases, aliasIds).then(
                                function(missingEntityAliases) {
                                    if (Object.keys(missingEntityAliases).length > 0) {
                                        editMissingAliases($event, [ widget ],
                                              true, 'dashboard.widget-import-missing-aliases-title', missingEntityAliases).then(
                                            function success(updatedEntityAliases) {
                                                for (var aliasId in updatedEntityAliases) {
                                                    var entityAlias = updatedEntityAliases[aliasId];
                                                    var datasourceIndex;
                                                    if (datasourceAliasesMap[aliasId]) {
                                                        datasourceIndex = datasourceAliasesMap[aliasId];
                                                        datasourceAliases[datasourceIndex] = entityAlias;
                                                    } else if (targetDeviceAliasesMap[aliasId]) {
                                                        datasourceIndex = targetDeviceAliasesMap[aliasId];
                                                        targetDeviceAliases[datasourceIndex] = entityAlias;
                                                    }
                                                }
                                                addImportedWidget(dashboard, targetState, targetLayoutFunction, $event, widget,
                                                    aliasesInfo, onAliasesUpdateFunction, originalColumns, originalSize, deferred);
                                            },
                                            function fail() {
                                                deferred.reject();
                                            }
                                        );
                                    } else {
                                        addImportedWidget(dashboard, targetState, targetLayoutFunction, $event, widget,
                                            aliasesInfo, onAliasesUpdateFunction, originalColumns, originalSize, deferred);
                                    }
                                }
                            );
                        } else {
                            addImportedWidget(dashboard, targetState, targetLayoutFunction, $event, widget,
                                aliasesInfo, onAliasesUpdateFunction, originalColumns, originalSize, deferred);
                        }
                    } else {
                        addImportedWidget(dashboard, targetState, targetLayoutFunction, $event, widget,
                            aliasesInfo, onAliasesUpdateFunction, originalColumns, originalSize, deferred);
                    }
                }
            },
            function fail() {
                deferred.reject();
            }
        );
        return deferred.promise;
    }

    function validateImportedWidget(widgetItem) {
        if (angular.isUndefined(widgetItem.widget)
            || angular.isUndefined(widgetItem.aliasesInfo)
            || angular.isUndefined(widgetItem.originalColumns)) {
            return false;
        }
        var widget = widgetItem.widget;
        if (angular.isUndefined(widget.isSystemType) ||
            angular.isUndefined(widget.bundleAlias) ||
            angular.isUndefined(widget.typeAlias) ||
            angular.isUndefined(widget.type)) {
            return false;
        }
        return true;
    }

    function addImportedWidget(dashboard, targetState, targetLayoutFunction, event, widget,
                               aliasesInfo, onAliasesUpdateFunction, originalColumns, originalSize, deferred) {
        targetLayoutFunction(event).then(
            function success(targetLayout) {
                itembuffer.addWidgetToDashboard(dashboard, targetState, targetLayout, widget,
                    aliasesInfo, onAliasesUpdateFunction, originalColumns, originalSize, -1, -1).then(
                        function() {
                            deferred.resolve(
                                {
                                    widget: widget,
                                    layoutId: targetLayout
                                }
                            );
                        }
                );
            },
            function fail() {
                deferred.reject();
            }
        );
    }

    // Dashboard functions

    function exportDashboard(dashboardId) {
        dashboardService.getDashboard(dashboardId).then(
            function success(dashboard) {
                var name = dashboard.title;
                name = name.toLowerCase().replace(/\W/g,"_");
                exportToPc(prepareExport(dashboard), name);
            },
            function fail(rejection) {
                var message = rejection;
                if (!message) {
                    message = $translate.instant('error.unknown-error');
                }
                toast.showError($translate.instant('dashboard.export-failed-error', {error: message}));
            }
        );
    }

    function importDashboard($event) {
        var deferred = $q.defer();
        openImportDialog($event, 'dashboard.import', 'dashboard.dashboard-file').then(
            function success(dashboard) {
                if (!validateImportedDashboard(dashboard)) {
                    toast.showError($translate.instant('dashboard.invalid-dashboard-file-error'));
                    deferred.reject();
                } else {
                    dashboard = dashboardUtils.validateAndUpdateDashboard(dashboard);
                    var entityAliases = dashboard.configuration.entityAliases;
                    if (entityAliases) {
                        var aliasIds = Object.keys( entityAliases );
                        if (aliasIds.length > 0) {
                            processEntityAliases(entityAliases, aliasIds).then(
                                function(missingEntityAliases) {
                                    if (Object.keys( missingEntityAliases ).length > 0) {
                                        editMissingAliases($event, dashboard.configuration.widgets,
                                                false, 'dashboard.dashboard-import-missing-aliases-title', missingEntityAliases).then(
                                            function success(updatedEntityAliases) {
                                                for (var aliasId in updatedEntityAliases) {
                                                    entityAliases[aliasId] = updatedEntityAliases[aliasId];
                                                }
                                                saveImportedDashboard(dashboard, deferred);
                                            },
                                            function fail() {
                                                deferred.reject();
                                            }
                                        );
                                    } else {
                                        saveImportedDashboard(dashboard, deferred);
                                    }
                                }
                            )
                        } else {
                            saveImportedDashboard(dashboard, deferred);
                        }
                    } else {
                        saveImportedDashboard(dashboard, deferred);
                    }
                }
            },
            function fail() {
                deferred.reject();
            }
        );
        return deferred.promise;
    }

    function saveImportedDashboard(dashboard, deferred) {
        dashboardService.saveDashboard(dashboard).then(
            function success() {
                deferred.resolve();
            },
            function fail() {
                deferred.reject();
            }
        )
    }

    function validateImportedDashboard(dashboard) {
        if (angular.isUndefined(dashboard.title) || angular.isUndefined(dashboard.configuration)) {
            return false;
        }
        return true;
    }

    function processEntityAliases(entityAliases, aliasIds) {
        var deferred = $q.defer();
        var missingEntityAliases = {};
        var index = -1;
        checkNextEntityAliasOrComplete(index, aliasIds, entityAliases, missingEntityAliases, deferred);
        return deferred.promise;
    }

    function checkNextEntityAliasOrComplete(index, aliasIds, entityAliases, missingEntityAliases, deferred) {
        index++;
        if (index == aliasIds.length) {
            deferred.resolve(missingEntityAliases);
        } else {
            checkEntityAlias(index, aliasIds, entityAliases, missingEntityAliases, deferred);
        }
    }

    function checkEntityAlias(index, aliasIds, entityAliases, missingEntityAliases, deferred) {
        var aliasId = aliasIds[index];
        var entityAlias = entityAliases[aliasId];
        entityService.checkEntityAlias(entityAlias).then(
            function(result) {
                if (result) {
                    checkNextEntityAliasOrComplete(index, aliasIds, entityAliases, missingEntityAliases, deferred);
                } else {
                    var missingEntityAlias = angular.copy(entityAlias);
                    missingEntityAlias.filter = null;
                    missingEntityAliases[aliasId] = missingEntityAlias;
                    checkNextEntityAliasOrComplete(index, aliasIds, entityAliases, missingEntityAliases, deferred);
                }
            }
        );
    }

    function editMissingAliases($event, widgets, isSingleWidget, customTitle, missingEntityAliases) {
        var deferred = $q.defer();
        $mdDialog.show({
            controller: 'EntityAliasesController',
            controllerAs: 'vm',
            templateUrl: entityAliasesTemplate,
            locals: {
                config: {
                    entityAliases: missingEntityAliases,
                    widgets: widgets,
                    isSingleWidget: isSingleWidget,
                    customTitle: customTitle,
                    disableAdd: true
                }
            },
            parent: angular.element($document[0].body),
            skipHide: true,
            fullscreen: true,
            targetEvent: $event
        }).then(function (updatedEntityAliases) {
            deferred.resolve(updatedEntityAliases);
        }, function () {
            deferred.reject();
        });
        return deferred.promise;
    }

    // Common functions

    function prepareExport(data) {
        var exportedData = angular.copy(data);
        if (angular.isDefined(exportedData.id)) {
            delete exportedData.id;
        }
        if (angular.isDefined(exportedData.createdTime)) {
            delete exportedData.createdTime;
        }
        if (angular.isDefined(exportedData.tenantId)) {
            delete exportedData.tenantId;
        }
        if (angular.isDefined(exportedData.customerId)) {
            delete exportedData.customerId;
        }
        return exportedData;
    }

    function openImportDialog($event, importTitle, importFileLabel) {
        var deferred = $q.defer();
        $mdDialog.show({
            controller: 'ImportDialogController',
            controllerAs: 'vm',
            templateUrl: importDialogTemplate,
            locals: {
                importTitle: importTitle,
                importFileLabel: importFileLabel
            },
            parent: angular.element($document[0].body),
            skipHide: true,
            fullscreen: true,
            targetEvent: $event
        }).then(function (importData) {
            deferred.resolve(importData);
        }, function () {
            deferred.reject();
        });
        return deferred.promise;
    }

    function exportToPc(data, filename) {
        if (!data) {
            $log.error('No data');
            return;
        }
        exportJson(data, filename);
    }

    function exportJson(data, filename) {
        if (angular.isObject(data)) {
            data = angular.toJson(data, 2);
        }
        downloadFile(data, filename, JSON_TYPE);
    }

    function exportCsv(data, filename) {
        var colsHead;
        var colsData;
        if (data && data.length) {
            colsHead = Object.keys(data[0]).map(key => [key]).join(';');
            colsData = data.map(obj => [ // obj === row
                Object.keys(obj).map(col => [
                    obj[col]
                ]).join(';')
            ]).join('\n');
        } else {
            colsHead = '';
            colsData = '';
        }
        var csvData = `${colsHead}\n${colsData}`;
        downloadFile(csvData, filename, CSV_TYPE);
    }

    function exportXls(data, filename) {
        var colsHead;
        var colsData;
        if (data && data.length) {
            colsHead = `<tr>${Object.keys(data[0]).map(key => `<td><b>${key}</b></td>`).join('')}</tr>`;
            colsData = data.map(obj => [`<tr>
                ${Object.keys(obj).map(col => `<td>${obj[col] ? obj[col] : ''}</td>`).join('')}
            </tr>`])
                .join('');
        } else {
            colsHead = '';
            colsData = '';
        }
        var tableData = `<table>${colsHead}${colsData}</table>`.trim();
        var parameters = { title: filename, table: tableData };
        var xlsData = TEMPLATE_XLS.replace(/{(\w+)}/g, (x, y) => parameters[y]);
        downloadFile(xlsData, filename, XLS_TYPE);
    }

    function downloadFile(data, filename, fileType) {
        if (!filename) {
            filename = 'download';
        }
        filename += '.' + fileType.extension;
        var blob = new Blob([data], {type: fileType.mimeType});
        // FOR IE:
        if (window.navigator && window.navigator.msSaveOrOpenBlob) {
            window.navigator.msSaveOrOpenBlob(blob, filename);
        } else {
            var e = document.createEvent('MouseEvents'),
                a = document.createElement('a');
            a.download = filename;
            a.href = window.URL.createObjectURL(blob);
            a.dataset.downloadurl = [fileType.mimeType, a.download, a.href].join(':');
            e.initEvent('click', true, false, window,
                0, 0, 0, 0, 0, false, false, false, false, 0, null);
            a.dispatchEvent(e);
        }
    }

}

/* eslint-enable no-undef, angular/window-service, angular/document-service */
