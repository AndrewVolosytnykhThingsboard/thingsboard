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
export default angular.module('thingsboard.api.integration', [])
    .factory('integrationService', IntegrationService)
    .name;

/*@ngInject*/
function IntegrationService($http, $q) {

    var service = {
        getIntegrations: getIntegrations,
        getIntegrationsByIds: getIntegrationsByIds,
        getIntegration: getIntegration,
        deleteIntegration: deleteIntegration,
        saveIntegration: saveIntegration,
        getIntegrationHttpEndpointLink: getIntegrationHttpEndpointLink
    };

    return service;

    function getIntegrations(pageLink, config) {
        var deferred = $q.defer();
        var url = '/api/integrations?limit=' + pageLink.limit;
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

    function getIntegrationsByIds(integrationIds, config) {
        var deferred = $q.defer();
        var ids = '';
        for (var i=0;i<integrationIds.length;i++) {
            if (i>0) {
                ids += ',';
            }
            ids += integrationIds[i];
        }
        var url = '/api/integrations?integrationIds=' + ids;
        $http.get(url, config).then(function success(response) {
            var entities = response.data;
            entities.sort(function (entity1, entity2) {
                var id1 =  entity1.id.id;
                var id2 =  entity2.id.id;
                var index1 = integrationIds.indexOf(id1);
                var index2 = integrationIds.indexOf(id2);
                return index1 - index2;
            });
            deferred.resolve(entities);
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function getIntegration(integrationId, config) {
        var deferred = $q.defer();
        var url = '/api/integration/' + integrationId;
        $http.get(url, config).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail(response) {
            deferred.reject(response.data);
        });
        return deferred.promise;
    }

    function saveIntegration(integration) {
        var deferred = $q.defer();
        var url = '/api/integration';
        $http.post(url, integration).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail(response) {
            deferred.reject(response.data);
        });
        return deferred.promise;
    }

    function deleteIntegration(integrationId) {
        var deferred = $q.defer();
        var url = '/api/integration/' + integrationId;
        $http.delete(url).then(function success() {
            deferred.resolve();
        }, function fail(response) {
            deferred.reject(response.data);
        });
        return deferred.promise;
    }

    function getIntegrationHttpEndpointLink(configuration, integrationType, routingKey) {
        var url = configuration.baseUrl;
        var type = integrationType ? integrationType.toLowerCase() : '';
        var key = routingKey ? routingKey : '';
        url += `/api/v1/integrations/${type}/${key}`;
        return url;
    }

}