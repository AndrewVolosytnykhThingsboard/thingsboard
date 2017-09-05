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

import componentTemplate from './component.tpl.html';

/* eslint-enable import/no-unresolved, import/default */

/*@ngInject*/
export default function ComponentDirective($compile, $templateCache, $document, $mdDialog, componentDialogService, componentDescriptorService) {

    var linker = function (scope, element) {

        var template = $templateCache.get(componentTemplate);

        element.html(template);

        scope.componentTypeName = '';

        scope.loadComponentTypeName = function () {
            componentDescriptorService.getComponentDescriptorByClazz(scope.component.clazz).then(
                function success(component) {
                    scope.componentTypeName = component.name;
                },
                function fail() {}
            );
        }

        scope.$watch('component', function(newVal) {
                if (newVal) {
                    scope.loadComponentTypeName();
                }
            }
        );

        scope.openComponent = function($event) {
            componentDialogService.openComponentDialog($event, false,
                scope.readOnly, scope.title, scope.type, scope.pluginClazz,
                angular.copy(scope.component)).then(
                    function success(component) {
                        scope.component = component;
                    },
                    function fail() {}
            );
        }

        $compile(element.contents())(scope);
    }

    return {
        restrict: "E",
        link: linker,
        scope: {
            component: '=',
            type: '=',
            pluginClazz: '=',
            title: '@',
            readOnly: '=',
            onRemoveComponent: '&'
        }
    };
}
