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

import eventErrorDialogTemplate from './event-content-dialog.tpl.html';

import eventRowLcEventTemplate from './event-row-lc-event.tpl.html';
import eventRowStatsTemplate from './event-row-stats.tpl.html';
import eventRowRawDataTemplate from './event-row-raw-data.tpl.html';
import eventRowErrorTemplate from './event-row-error.tpl.html';
import eventRowDebugConverterTemplate from './event-row-debug-converter.tpl.html';
import eventRowDebugIntegrationTemplate from './event-row-debug-integration.tpl.html';
import eventRowDebugRuleNodeTemplate from './event-row-debug-rulenode.tpl.html';
import eventRowEdgeEventTemplate from './event-row-edge-event.tpl.html';

/* eslint-enable import/no-unresolved, import/default */

/*@ngInject*/
export default function EventRowDirective($compile, $templateCache, $mdDialog, $document, $translate,
                                          types, toast, entityService, ruleChainService) {

    var linker = function (scope, element, attrs) {

        var getTemplate = function(eventType) {
            var template = '';
            switch(eventType) {
                case types.eventType.lcEvent.value:
                    template = eventRowLcEventTemplate;
                    break;
                case types.eventType.stats.value:
                    template = eventRowStatsTemplate;
                    break;
                case types.eventType.error.value:
                    template = eventRowErrorTemplate;
                    break;
                case types.debugEventType.debugConverter.value:
                    template = eventRowDebugConverterTemplate;
                    break;
                case types.debugEventType.debugIntegration.value:
                    template = eventRowDebugIntegrationTemplate;
                    break;
                case types.debugEventType.debugRuleNode.value:
                    template = eventRowDebugRuleNodeTemplate;
                    break;
                case types.debugEventType.debugRuleChain.value:
                    template = eventRowDebugRuleNodeTemplate;
                    break;
                case types.eventType.rawData.value:
                    template = eventRowRawDataTemplate;
                    break;
                case types.eventType.edgeEvent.value:
                    template = eventRowEdgeEventTemplate;
                    break;
            }
            return $templateCache.get(template);
        }

        scope.loadTemplate = function() {
            element.html(getTemplate(attrs.eventType));
            $compile(element.contents())(scope);
        }

        attrs.$observe('eventType', function() {
            scope.loadTemplate();
        });

        scope.types = types;

        scope.event = attrs.event;

        scope.showContent = function($event, content, title, contentType) {
            var onShowingCallback = {
                onShowing: function(){}
            }
            if (!contentType) {
                contentType = null;
            }
            $mdDialog.show({
                controller: 'EventContentDialogController',
                controllerAs: 'vm',
                templateUrl: eventErrorDialogTemplate,
                locals: {content: content, title: title, contentType: contentType, showingCallback: onShowingCallback},
                parent: angular.element($document[0].body),
                fullscreen: true,
                targetEvent: $event,
                multiple: true,
                onShowing: function(scope, element) {
                    onShowingCallback.onShowing(scope, element);
                }
            });
        }

        scope.showEdgeEntityContent = function($event, title, contentType) {
            var onShowingCallback = {
                onShowing: function(){}
            };
            if (!contentType) {
                contentType = null;
            }
            var content = '';
            switch(scope.event.edgeEventType) {
                case types.edgeEventType.relation:
                case types.edgeEventType.whiteLabeling:
                case types.edgeEventType.loginWhiteLabeling:
                case types.edgeEventType.customTranslation:
                    content = angular.toJson(scope.event.entityBody);
                    showDialog();
                    break;
                case types.edgeEventType.ruleChainMetaData:
                    content = ruleChainService.getRuleChainMetaData(scope.event.entityId, {ignoreErrors: true}).then(
                        function success(info) {
                            showDialog();
                            return angular.toJson(info);
                        }, function fail() {
                            showError();
                        });
                    break;
                default:
                    content = entityService.getEntity(scope.event.edgeEventType, scope.event.entityId, {ignoreErrors: true}).then(
                        function success(info) {
                            showDialog();
                            return angular.toJson(info);
                        }, function fail() {
                            showError();
                        });
                    break;
            }
            function showDialog() {
                $mdDialog.show({
                    controller: 'EventContentDialogController',
                    controllerAs: 'vm',
                    templateUrl: eventErrorDialogTemplate,
                    locals: {content: content, title: title, contentType: contentType, showingCallback: onShowingCallback},
                    parent: angular.element($document[0].body),
                    fullscreen: true,
                    targetEvent: $event,
                    multiple: true,
                    onShowing: function(scope, element) {
                        onShowingCallback.onShowing(scope, element);
                    }
                });
            }
            function showError() {
                toast.showError($translate.instant('edge.load-entity-error'));
            }
        }

        scope.checkEdgeEventType = function (edgeEventType) {
            return !(edgeEventType === types.edgeEventType.widgetType ||
                     edgeEventType === types.edgeEventType.adminSettings ||
                     edgeEventType === types.edgeEventType.widgetsBundle );
        }

        scope.checkTooltip = function($event) {
            var el = $event.target;
            var $el = angular.element(el);
            if(el.offsetWidth < el.scrollWidth && !$el.attr('title')){
                $el.attr('title', $el.text());
            }
        }

        $compile(element.contents())(scope);

        scope.updateStatus = function(eventCreatedTime) {
            if (scope.queueStartTs) {
                var status;
                if (eventCreatedTime < scope.queueStartTs) {
                    status = $translate.instant('edge.success');
                    scope.isPending = false;
                } else {
                    status = $translate.instant('edge.failed');
                    scope.isPending = true;
                }
                return status;
            }
        }
    }

    return {
        restrict: "A",
        replace: false,
        link: linker,
        scope: false
    };
}
