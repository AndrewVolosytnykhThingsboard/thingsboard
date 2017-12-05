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

import './home-links.scss';

/*@ngInject*/
export default function HomeLinksController($scope, $mdMedia, menu) {

    var vm = this;

    vm.sectionColspan = sectionColspan;

    $scope.$watch(function() { return $mdMedia('lg'); }, function() {
        updateColumnCount();
    });

    $scope.$watch(function() { return $mdMedia('gt-lg'); }, function() {
        updateColumnCount();
    });

    updateColumnCount();

    vm.model = menu.getHomeSections();

    function updateColumnCount() {
        vm.cols = 2;
        if ($mdMedia('lg')) {
            vm.cols = 3;
        }
        if ($mdMedia('gt-lg')) {
            vm.cols = 4;
        }
    }

    function sectionColspan(section) {
        var colspan = vm.cols;
        if (section && section.places && section.places.length <= colspan) {
            colspan = section.places.length;
        }
        return colspan;
    }

}
