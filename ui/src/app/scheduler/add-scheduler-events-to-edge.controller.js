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
/*@ngInject*/
export default function AddSchedulerEventsToEdgeController(schedulerEventService, $mdDialog, $q, $filter, edgeId, schedulerEvents) {

    var vm = this;

    vm.schedulerEvents = schedulerEvents;
    vm.searchText = '';

    vm.assign = assign;
    vm.cancel = cancel;
    vm.hasData = hasData;
    vm.noData = noData;
    vm.searchSchdulerEventTextUpdated = searchSchdulerEventTextUpdated;
    vm.toggleSchdulerEventSelection = toggleSchdulerEventSelection;
    vm.defaultEventType = null;

    vm.theSchedulerEvents = schedulerEvents.data;

    // vm.theSchedulerEvents = {
    //     {
    //     getItemAtIndex: function (index) {
    //         if (index > vm.schedulerEvents.data.length) {
    //             vm.theSchedulerEvents.fetchMoreItems_(index);
    //             return null;
    //         }
    //         return vm.schedulerEvents.data[index];
    //     },
    //
    //     getLength: function () {
    //         return vm.schedulerEvents.data.length;
    //     },
    //
    //     fetchMoreItems_: function () {
    //         if (!vm.schedulerEvents.pending) {
    //             vm.schedulerEvents.pending = true;
    //             schedulerEventService.getSchedulerEvents(vm.defaultEventType, false).then(
    //                 function success(schedulerEvents) {
    //                     vm.schedulerEvents = schedulerEvents;
    //                     vm.schedulerEvents.nextPageLink = schedulerEvents.nextPageLink;
    //                     vm.schedulerEvents.hasNext = schedulerEvents.hasNext;
    //                     if (vm.schedulerEvents.hasNext) {
    //                         vm.schedulerEvents.nextPageLink.limit = vm.schedulerEvents.pageSize;
    //                     }
    //                     vm.schedulerEvents.pending = false;
    //                 },
    //                 function fail() {
    //                     vm.schedulerEvents.hasNext = false;
    //                     vm.schedulerEvents.pending = false;
    //                 });
    //         }
    //     }
    // }

    function cancel () {
        $mdDialog.cancel();
    }

    function assign () {
        var tasks = [];
        for (var schedulerEventId in vm.schedulerEvents.selections) {
            tasks.push(schedulerEventService.updateSchedulerEdgeGroups(edgeId, schedulerEventId));
        }
        $q.all(tasks).then(function () {
            $mdDialog.hide();
        });
    }

    function noData () {
        return vm.schedulerEvents.data.length === 0 && !vm.schedulerEvents.hasNext;
    }

    function hasData () {
        return vm.schedulerEvents.data.length > 0;
    }

    function toggleSchdulerEventSelection ($event, schedulerEvent) {
        $event.stopPropagation();
        var selected = angular.isDefined(schedulerEvent.selected) && schedulerEvent.selected;
        schedulerEvent.selected = !selected;
        if (schedulerEvent.selected) {
            vm.schedulerEvents.selections[schedulerEvent.id.id] = true;
            vm.schedulerEvents.selectedCount++;
        } else {
            delete vm.schedulerEvents.selections[schedulerEvent.id.id];
            vm.schedulerEvents.selectedCount--;
        }
    }

    function searchSchdulerEventTextUpdated () {
        vm.schedulerEvents = {
            data: [],
            selections: {},
            selectedCount: 0
        };
    }
}
