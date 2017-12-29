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
export default angular.module('thingsboard.api.dashboard', [])
    .factory('dashboardService', DashboardService).name;

/*@ngInject*/
function DashboardService($rootScope, $http, $q, $location, customerService) {

    var stDiffPromise;

    $rootScope.dadshboardServiceStateChangeStartHandle = $rootScope.$on('$stateChangeStart', function () {
        stDiffPromise = undefined;
    });


    var service = {
        assignDashboardToCustomer: assignDashboardToCustomer,
        getCustomerDashboards: getCustomerDashboards,
        getServerTimeDiff: getServerTimeDiff,
        getDashboard: getDashboard,
        getDashboardInfo: getDashboardInfo,
        getTenantDashboardsByTenantId: getTenantDashboardsByTenantId,
        getTenantDashboards: getTenantDashboards,
        deleteDashboard: deleteDashboard,
        saveDashboard: saveDashboard,
        unassignDashboardFromCustomer: unassignDashboardFromCustomer,
        makeDashboardPublic: makeDashboardPublic,
        getPublicDashboardLink: getPublicDashboardLink
    }

    return service;

    function getTenantDashboardsByTenantId(tenantId, pageLink, config) {
        var deferred = $q.defer();
        var url = '/api/tenant/' + tenantId + '/dashboards?limit=' + pageLink.limit;
        if (angular.isDefined(pageLink.textSearch)) {
            url += '&textSearch=' + pageLink.textSearch;
        }
        if (angular.isDefined(pageLink.idOffset)) {
            url += '&idOffset=' + pageLink.idOffset;
        }
        if (angular.isDefined(pageLink.textOffset)) {
            url += '&textOffset=' + pageLink.textOffset;
        }
        $http.get(url, config).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function getTenantDashboards(pageLink, applyCustomersInfo, config) {
        var deferred = $q.defer();
        var url = '/api/tenant/dashboards?limit=' + pageLink.limit;
        if (angular.isDefined(pageLink.textSearch)) {
            url += '&textSearch=' + pageLink.textSearch;
        }
        if (angular.isDefined(pageLink.idOffset)) {
            url += '&idOffset=' + pageLink.idOffset;
        }
        if (angular.isDefined(pageLink.textOffset)) {
            url += '&textOffset=' + pageLink.textOffset;
        }
        $http.get(url, config).then(function success(response) {
            if (applyCustomersInfo) {
                customerService.applyAssignedCustomersInfo(response.data.data).then(
                    function success(data) {
                        response.data.data = data;
                        deferred.resolve(response.data);
                    },
                    function fail() {
                        deferred.reject();
                    }
                );
            } else {
                deferred.resolve(response.data);
            }
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function getCustomerDashboards(customerId, pageLink, applyCustomersInfo, config) {
        var deferred = $q.defer();
        var url = '/api/customer/' + customerId + '/dashboards?limit=' + pageLink.limit;
        if (angular.isDefined(pageLink.textSearch)) {
            url += '&textSearch=' + pageLink.textSearch;
        }
        if (angular.isDefined(pageLink.idOffset)) {
            url += '&idOffset=' + pageLink.idOffset;
        }
        if (angular.isDefined(pageLink.textOffset)) {
            url += '&textOffset=' + pageLink.textOffset;
        }
        $http.get(url, config).then(function success(response) {
            if (applyCustomersInfo) {
                customerService.applyAssignedCustomerInfo(response.data.data, customerId).then(
                    function success(data) {
                        response.data.data = data;
                        deferred.resolve(response.data);
                    },
                    function fail() {
                        deferred.reject();
                    }
                );
            } else {
                deferred.resolve(response.data);
            }
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function getServerTimeDiff() {
        if (stDiffPromise) {
            return stDiffPromise;
        } else {
            var deferred = $q.defer();
            stDiffPromise = deferred.promise;
            var url = '/api/dashboard/serverTime';
            var ct1 = Date.now();
            $http.get(url, {ignoreLoading: true}).then(function success(response) {
                var ct2 = Date.now();
                var st = response.data;
                var stDiff = Math.ceil(st - (ct1 + ct2) / 2);
                deferred.resolve(stDiff);
            }, function fail() {
                deferred.reject();
            });
        }
        return stDiffPromise;
    }

    function getDashboard(dashboardId) {
        var deferred = $q.defer();
        var url = '/api/dashboard/' + dashboardId;
        $http.get(url, null).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function getDashboardInfo(dashboardId, config) {
        var deferred = $q.defer();
        var url = '/api/dashboard/info/' + dashboardId;
        $http.get(url, config).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function saveDashboard(dashboard) {
        var deferred = $q.defer();
        var url = '/api/dashboard';
        $http.post(url, dashboard).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function deleteDashboard(dashboardId) {
        var deferred = $q.defer();
        var url = '/api/dashboard/' + dashboardId;
        $http.delete(url).then(function success() {
            deferred.resolve();
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function assignDashboardToCustomer(customerId, dashboardId) {
        var deferred = $q.defer();
        var url = '/api/customer/' + customerId + '/dashboard/' + dashboardId;
        $http.post(url, null).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function unassignDashboardFromCustomer(dashboardId) {
        var deferred = $q.defer();
        var url = '/api/customer/dashboard/' + dashboardId;
        $http.delete(url).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function makeDashboardPublic(dashboardId) {
        var deferred = $q.defer();
        var url = '/api/customer/public/dashboard/' + dashboardId;
        $http.post(url, null).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function getPublicDashboardLink(dashboard) {
        var url = $location.protocol() + '://' + $location.host();
        var port = $location.port();
        if (port != 80 && port != 443) {
            url += ":" + port;
        }
        url += "/dashboards/" + dashboard.id.id + "?publicId=" + dashboard.customerId.id;
        return url;
    }

}
