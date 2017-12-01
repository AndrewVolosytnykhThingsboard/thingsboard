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
import thingsboardApiUser from '../api/user.service';

export default angular.module('thingsboard.menu', [thingsboardApiUser])
    .factory('menu', Menu)
    .name;

/*@ngInject*/
function Menu(userService, $state, $rootScope, $q, types, entityGroupService) {

    var authority = '';
    var sections = [];
    var homeSections = [];
    var isMenuReady = false;
    var menuReadyTasks = [];

    var customerGroups = {
        name: 'entity-group.customer-groups',
        type: 'toggle',
        state: 'home.customerGroups',
        height: '0px',
        icon: 'supervisor_account',
        pages: []
    };

    var assetGroups = {
        name: 'entity-group.asset-groups',
        type: 'toggle',
        state: 'home.assetGroups',
        height: '0px',
        icon: 'domain',
        pages: []
    };

    var deviceGroups = {
        name: 'entity-group.device-groups',
        type: 'toggle',
        state: 'home.deviceGroups',
        height: '0px',
        icon: 'devices_other',
        pages: []
    };

    var service = {
        getHomeSections: getHomeSections,
        getSections: getSections,
        sectionHeight: sectionHeight,
        sectionActive: sectionActive
    };

    if (userService.isUserLoaded() === true) {
        buildMenu();
    }

    service.authenticatedHandle = $rootScope.$on('authenticated', function () {
        buildMenu();
    });

    return service;

    function getSections() {
        var deferred = $q.defer();
        if (isMenuReady) {
            deferred.resolve(sections);
        } else {
            menuReadyTasks.push(
                () => {
                    deferred.resolve(sections);
                }
            );
        }
        return deferred.promise;
    }

    function getHomeSections() {
        var deferred = $q.defer();
        if (isMenuReady) {
            deferred.resolve(homeSections);
        } else {
            menuReadyTasks.push(
                () => {
                    deferred.resolve(homeSections);
                }
            );
        }
        return deferred.promise;
    }

    function buildMenu() {
        isMenuReady = false;
        var user = userService.getCurrentUser();
        if (user) {
            if (authority !== user.authority) {
                sections = [];
                authority = user.authority;
                if (authority === 'SYS_ADMIN') {
                    sections = [
                        {
                            name: 'home.home',
                            type: 'link',
                            state: 'home.links',
                            icon: 'home'
                        },
                        {
                            name: 'plugin.plugins',
                            type: 'link',
                            state: 'home.plugins',
                            icon: 'extension'
                        },
                        {
                            name: 'rule.rules',
                            type: 'link',
                            state: 'home.rules',
                            icon: 'settings_ethernet'
                        },
                        {
                            name: 'tenant.tenants',
                            type: 'link',
                            state: 'home.tenants',
                            icon: 'supervisor_account'
                        },
                        {
                            name: 'widget.widget-library',
                            type: 'link',
                            state: 'home.widgets-bundles',
                            icon: 'now_widgets'
                        },
                        {
                            name: 'admin.system-settings',
                            type: 'toggle',
                            state: 'home.settings',
                            height: '120px',
                            icon: 'settings',
                            pages: [
                                {
                                    name: 'admin.outgoing-mail',
                                    type: 'link',
                                    state: 'home.settings.outgoing-mail',
                                    icon: 'mail'
                                },
                                {
                                    name: 'admin.mail-templates',
                                    type: 'link',
                                    state: 'home.settings.mail-template',
                                    icon: 'format_shapes'
                                },
                                {
                                    name: 'white-labeling.white-labeling',
                                    type: 'link',
                                    state: 'home.settings.whiteLabel',
                                    icon: 'format_paint'
                                }
                            ]
                        }];
                    homeSections =
                        [{
                            name: 'rule-plugin.management',
                            places: [
                                {
                                    name: 'plugin.plugins',
                                    icon: 'extension',
                                    state: 'home.plugins'
                                },
                                {
                                    name: 'rule.rules',
                                    icon: 'settings_ethernet',
                                    state: 'home.rules'
                                }
                            ]
                        },
                        {
                            name: 'tenant.management',
                            places: [
                                {
                                    name: 'tenant.tenants',
                                    icon: 'supervisor_account',
                                    state: 'home.tenants'
                                }
                            ]
                        },
                            {
                                name: 'widget.management',
                                places: [
                                    {
                                        name: 'widget.widget-library',
                                        icon: 'now_widgets',
                                        state: 'home.widgets-bundles'
                                    }
                                ]
                            },
                            {
                                name: 'admin.system-settings',
                                places: [
                                    {
                                        name: 'admin.outgoing-mail',
                                        icon: 'mail',
                                        state: 'home.settings.outgoing-mail'
                                    },
                                    {
                                        name: 'admin.mail-templates',
                                        icon: 'format_shapes',
                                        state: 'home.settings.mail-template'
                                    },
                                    {
                                        name: 'white-labeling.white-labeling',
                                        icon: 'format_paint',
                                        state: 'home.settings.whiteLabel'
                                    }
                                ]
                            }];
                } else if (authority === 'TENANT_ADMIN') {
                    sections = [
                        {
                            name: 'home.home',
                            type: 'link',
                            state: 'home.links',
                            icon: 'home'
                        },
                        {
                            name: 'plugin.plugins',
                            type: 'link',
                            state: 'home.plugins',
                            icon: 'extension'
                        },
                        {
                            name: 'rule.rules',
                            type: 'link',
                            state: 'home.rules',
                            icon: 'settings_ethernet'
                        },
                        customerGroups,
                        assetGroups,
                        deviceGroups,
                        {
                            name: 'widget.widget-library',
                            type: 'link',
                            state: 'home.widgets-bundles',
                            icon: 'now_widgets'
                        },
                        {
                            name: 'dashboard.dashboards',
                            type: 'link',
                            state: 'home.dashboards',
                            icon: 'dashboards'
                        },
                        {
                            name: 'admin.system-settings',
                            type: 'toggle',
                            state: 'home.settings',
                            height: '120px',
                            icon: 'settings',
                            pages: [
                                {
                                    name: 'admin.outgoing-mail',
                                    type: 'link',
                                    state: 'home.settings.outgoing-mail',
                                    icon: 'mail'
                                },
                                {
                                    name: 'admin.mail-templates',
                                    type: 'link',
                                    state: 'home.settings.mail-template',
                                    icon: 'format_shapes'
                                },
                                {
                                    name: 'white-labeling.white-labeling',
                                    type: 'link',
                                    state: 'home.settings.whiteLabel',
                                    icon: 'format_paint'
                                }
                            ]
                        }];

                    homeSections =
                        [{
                            name: 'rule-plugin.management',
                            places: [
                                {
                                    name: 'plugin.plugins',
                                    icon: 'extension',
                                    state: 'home.plugins'
                                },
                                {
                                    name: 'rule.rules',
                                    icon: 'settings_ethernet',
                                    state: 'home.rules'
                                }
                            ]
                        },
                        {
                            name: 'customer.management',
                            places: [
                                {
                                    name: 'customer.customers',
                                    icon: 'supervisor_account',
                                    //state: 'home.customers',
                                    state: 'home.customerGroups'
                                }
                            ]
                        },
                            {
                                name: 'asset.management',
                                places: [
                                    {
                                        name: 'asset.assets',
                                        icon: 'domain',
                                        //state: 'home.assets'
                                        state: 'home.assetGroups'
                                    }
                                ]
                            },
                            {
                                name: 'device.management',
                                places: [
                                    {
                                        name: 'device.devices',
                                        icon: 'devices_other',
                                        //state: 'home.devices',
                                        state: 'home.deviceGroups'
                                    }
                                ]
                            },
                            {
                                name: 'dashboard.management',
                                places: [
                                    {
                                        name: 'widget.widget-library',
                                        icon: 'now_widgets',
                                        state: 'home.widgets-bundles'
                                    },
                                    {
                                        name: 'dashboard.dashboards',
                                        icon: 'dashboard',
                                        state: 'home.dashboards'
                                    }
                                ]
                            },
                            {
                                name: 'admin.system-settings',
                                places: [
                                    {
                                        name: 'admin.outgoing-mail',
                                        icon: 'mail',
                                        state: 'home.settings.outgoing-mail'
                                    },
                                    {
                                        name: 'admin.mail-templates',
                                        icon: 'format_shapes',
                                        state: 'home.settings.mail-template'
                                    },
                                    {
                                        name: 'white-labeling.white-labeling',
                                        icon: 'format_paint',
                                        state: 'home.settings.whiteLabel'
                                    }
                                ]
                            }];
                } else if (authority === 'CUSTOMER_USER') {
                    sections = [
                        {
                            name: 'home.home',
                            type: 'link',
                            state: 'home.links',
                            icon: 'home'
                        },
                        {
                            name: 'asset.assets',
                            type: 'link',
                            state: 'home.assets',
                            icon: 'domain'
                        },
                        {
                            name: 'device.devices',
                            type: 'link',
                            state: 'home.devices',
                            icon: 'devices_other'
                        },
                        {
                            name: 'dashboard.dashboards',
                            type: 'link',
                            state: 'home.dashboards',
                            icon: 'dashboard'
                        },
                        {
                            name: 'admin.system-settings',
                            type: 'toggle',
                            state: 'home.settings',
                            height: '40px',
                            icon: 'settings',
                            pages: [
                                {
                                    name: 'white-labeling.white-labeling',
                                    type: 'link',
                                    state: 'home.settings.whiteLabel',
                                    icon: 'format_paint'
                                }
                            ]
                        }];

                    homeSections =
                        [{
                            name: 'asset.view-assets',
                            places: [
                                {
                                    name: 'asset.assets',
                                    icon: 'domain',
                                    state: 'home.assets'
                                }
                            ]
                        },
                        {
                            name: 'device.view-devices',
                            places: [
                                {
                                    name: 'device.devices',
                                    icon: 'devices_other',
                                    state: 'home.devices'
                                }
                            ]
                        },
                            {
                                name: 'dashboard.view-dashboards',
                                places: [
                                    {
                                        name: 'dashboard.dashboards',
                                        icon: 'dashboard',
                                        state: 'home.dashboards'
                                    }
                                ]
                            },
                            {
                                name: 'admin.system-settings',
                                places: [
                                    {
                                        name: 'white-labeling.white-labeling',
                                        icon: 'format_paint',
                                        state: 'home.settings.whiteLabel'
                                    }
                                ]
                            }];
                }
            }
            if (authority === 'TENANT_ADMIN') {
                reloadGroups().then(() => {
                    onMenuReady();
                });
            } else {
                onMenuReady();
            }
        }
    }

    function onMenuReady() {
        isMenuReady = true;
        if (menuReadyTasks.length) {
            for (var i=0;i<menuReadyTasks.length;i++) {
                menuReadyTasks[i]();
            }
            menuReadyTasks.length = 0;
        }
    }

    function reloadGroups() {
        var tasks = [];
        tasks.push(loadGroups(customerGroups, types.entityType.customer, 'home.customerGroups.customerGroup', 'supervisor_account'));
        tasks.push(loadGroups(assetGroups, types.entityType.asset, 'home.assetGroups.assetGroup', 'domain'));
        tasks.push(loadGroups(deviceGroups, types.entityType.device, 'home.deviceGroups.deviceGroup', 'devices_other'));
        return $q.all(tasks);
    }

    function loadGroups(section, groupType, groupState, icon) {
        var deferred = $q.defer();
        entityGroupService.getTenantEntityGroups(groupType).then(
            function success(entityGroups) {
                var pages = [];
                entityGroups.forEach(function(entityGroup) {
                    var page = {
                        name: entityGroup.name,
                        type: 'link',
                        state: groupState + '({entityGroupId:\''+entityGroup.id.id+'\'})',
                        ignoreTranslate: true,
                        icon: icon
                    };
                    pages.push(page);
                });
                section.height = (40 * pages.length) + 'px';
                section.pages = pages;
                deferred.resolve();
            }
        );
        if (service[groupType + 'changeHandle']) {
            service[groupType + 'changeHandle']();
        }
        service[groupType + 'changeHandle'] = $rootScope.$on(groupType + 'changed', function () {
            loadGroups(section, groupType, groupState, icon);
        });
        return deferred.promise;
    }

    function sectionHeight(section) {
        if ($state.includes(section.state)) {
            return section.height;
        } else {
            return '0px';
        }
    }

    function sectionActive(section) {
        return $state.includes(section.state);
    }

}
