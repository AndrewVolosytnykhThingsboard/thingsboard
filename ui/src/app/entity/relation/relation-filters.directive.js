/*
 * Thingsboard OÜ ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2018 Thingsboard OÜ. All Rights Reserved.
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
import './relation-filters.scss';

/* eslint-disable import/no-unresolved, import/default */

import relationFiltersTemplate from './relation-filters.tpl.html';

/* eslint-enable import/no-unresolved, import/default */

/*@ngInject*/
export default function RelationFilters($compile, $templateCache) {

    return {
        restrict: "E",
        require: "^ngModel",
        scope: {
            allowedEntityTypes: '=?'
        },
        link: linker
    };

    function linker( scope, element, attrs, ngModelCtrl ) {

        var template = $templateCache.get(relationFiltersTemplate);
        element.html(template);

        scope.relationFilters = [];

        scope.addFilter = addFilter;
        scope.removeFilter = removeFilter;

        ngModelCtrl.$render = function () {
            if (ngModelCtrl.$viewValue) {
                var value = ngModelCtrl.$viewValue;
                scope.relationFilters.length = 0;
                value.forEach(function (filter) {
                    scope.relationFilters.push(filter);
                });
            }
            scope.$watch('relationFilters', function (newVal, prevVal) {
                if (!angular.equals(newVal, prevVal)) {
                    updateValue();
                }
            }, true);
        }

        function addFilter() {
            var filter = {
                relationType: null,
                entityTypes: []
            };
            scope.relationFilters.push(filter);
        }

        function removeFilter($event, filter) {
            var index = scope.relationFilters.indexOf(filter);
            if (index > -1) {
                scope.relationFilters.splice(index, 1);
            }
        }

        function updateValue() {
            var value = [];
            scope.relationFilters.forEach(function (filter) {
                value.push(filter);
            });
            ngModelCtrl.$setViewValue(value);
        }
        $compile(element.contents())(scope);
    }
}
