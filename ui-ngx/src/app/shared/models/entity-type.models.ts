///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
///
/// NOTICE: All information contained herein is, and remains
/// the property of ThingsBoard, Inc. and its suppliers,
/// if any.  The intellectual and technical concepts contained
/// herein are proprietary to ThingsBoard, Inc.
/// and its suppliers and may be covered by U.S. and Foreign Patents,
/// patents in process, and are protected by trade secret or copyright law.
///
/// Dissemination of this information or reproduction of this material is strictly forbidden
/// unless prior written permission is obtained from COMPANY.
///
/// Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
/// managers or contractors who have executed Confidentiality and Non-disclosure agreements
/// explicitly covering such access.
///
/// The copyright notice above does not evidence any actual or intended publication
/// or disclosure  of  this source code, which includes
/// information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
/// ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
/// OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
/// THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
/// AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
/// THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
/// DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
/// OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
///

import { TenantId } from './id/tenant-id';
import { BaseData, HasId } from '@shared/models/base-data';

///
/// Copyright © 2016-2019 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

export enum EntityType {
  TENANT = 'TENANT',
  CUSTOMER = 'CUSTOMER',
  USER = 'USER',
  DASHBOARD = 'DASHBOARD',
  ASSET = 'ASSET',
  DEVICE = 'DEVICE',
  ALARM = 'ALARM',
  ENTITY_GROUP = 'ENTITY_GROUP',
  CONVERTER = 'CONVERTER',
  INTEGRATION = 'INTEGRATION',
  RULE_CHAIN = 'RULE_CHAIN',
  RULE_NODE = 'RULE_NODE',
  SCHEDULER_EVENT = 'SCHEDULER_EVENT',
  BLOB_ENTITY = 'BLOB_ENTITY',
  ENTITY_VIEW = 'ENTITY_VIEW',
  WIDGETS_BUNDLE = 'WIDGETS_BUNDLE',
  WIDGET_TYPE = 'WIDGET_TYPE',
  ROLE = 'ROLE',
  GROUP_PERMISSION = 'GROUP_PERMISSION'
}

export enum AliasEntityType {
  CURRENT_CUSTOMER = 'CURRENT_CUSTOMER'
}

export interface EntityTypeTranslation {
  type?: string;
  typePlural?: string;
  list?: string;
  nameStartsWith?: string;
  details?: string;
  add?: string;
  noEntities?: string;
  selectedEntities?: string;
  search?: string;
}

export interface EntityTypeResource<T> {
  helpLinkId: string;
  helpLinkIdForEntity?(entity: T): string;
}

export const entityTypeTranslations = new Map<EntityType | AliasEntityType, EntityTypeTranslation>(
  [
    [
      EntityType.TENANT,
      {
        type: 'entity.type-tenant',
        typePlural: 'entity.type-tenants',
        list: 'entity.list-of-tenants',
        nameStartsWith: 'entity.tenant-name-starts-with',
        details: 'tenant.tenant-details',
        add: 'tenant.add',
        noEntities: 'tenant.no-tenants-text',
        search: 'tenant.search',
        selectedEntities: 'tenant.selected-tenants'
      }
    ],
    [
      EntityType.CUSTOMER,
      {
        type: 'entity.type-customer',
        typePlural: 'entity.type-customers',
        list: 'entity.list-of-customers',
        nameStartsWith: 'entity.customer-name-starts-with',
        details: 'customer.customer-details',
        add: 'customer.add',
        noEntities: 'customer.no-customers-text',
        search: 'customer.search',
        selectedEntities: 'customer.selected-customers'
      }
    ],
    [
      EntityType.USER,
      {
        type: 'entity.type-user',
        typePlural: 'entity.type-users',
        list: 'entity.list-of-users',
        nameStartsWith: 'entity.user-name-starts-with',
        details: 'user.user-details',
        add: 'user.add',
        noEntities: 'user.no-users-text',
        search: 'user.search',
        selectedEntities: 'user.selected-users'
      }
    ],
    [
      EntityType.DEVICE,
      {
        type: 'entity.type-device',
        typePlural: 'entity.type-devices',
        list: 'entity.list-of-devices',
        nameStartsWith: 'entity.device-name-starts-with',
        details: 'device.device-details',
        add: 'device.add',
        noEntities: 'device.no-devices-text',
        search: 'device.search',
        selectedEntities: 'device.selected-devices'
      }
    ],
    [
      EntityType.ASSET,
      {
        type: 'entity.type-asset',
        typePlural: 'entity.type-assets',
        list: 'entity.list-of-assets',
        nameStartsWith: 'entity.asset-name-starts-with',
        details: 'asset.asset-details',
        add: 'asset.add',
        noEntities: 'asset.no-assets-text',
        search: 'asset.search',
        selectedEntities: 'asset.selected-assets'
      }
    ],
    [
      EntityType.ENTITY_VIEW,
      {
        type: 'entity.type-entity-view',
        typePlural: 'entity.type-entity-views',
        list: 'entity.list-of-entity-views',
        nameStartsWith: 'entity.entity-view-name-starts-with',
        details: 'entity-view.entity-view-details',
        add: 'entity-view.add',
        noEntities: 'entity-view.no-entity-views-text',
        search: 'entity-view.search',
        selectedEntities: 'entity-view.selected-entity-views'
      }
    ],
    [
      EntityType.RULE_CHAIN,
      {
        type: 'entity.type-rulechain',
        typePlural: 'entity.type-rulechains',
        list: 'entity.list-of-rulechains',
        nameStartsWith: 'entity.rulechain-name-starts-with',
        details: 'rulechain.rulechain-details',
        add: 'rulechain.add',
        noEntities: 'rulechain.no-rulechains-text',
        search: 'rulechain.search',
        selectedEntities: 'rulechain.selected-rulechains'
      }
    ],
    [
      EntityType.RULE_NODE,
      {
        type: 'entity.type-rulenode',
        typePlural: 'entity.type-rulenodes',
        list: 'entity.list-of-rulenodes',
        nameStartsWith: 'entity.rulenode-name-starts-with'
      }
    ],
    [
      EntityType.DASHBOARD,
      {
        type: 'entity.type-dashboard',
        typePlural: 'entity.type-dashboards',
        list: 'entity.list-of-dashboards',
        nameStartsWith: 'entity.dashboard-name-starts-with',
        details: 'dashboard.dashboard-details',
        add: 'dashboard.add',
        noEntities: 'dashboard.no-dashboards-text',
        search: 'dashboard.search',
        selectedEntities: 'dashboard.selected-dashboards'
      }
    ],
    [
      EntityType.ALARM,
      {
        type: 'entity.type-alarm',
        typePlural: 'entity.type-alarms',
        list: 'entity.list-of-alarms',
        nameStartsWith: 'entity.alarm-name-starts-with',
        details: 'dashboard.dashboard-details',
        noEntities: 'alarm.no-alarms-prompt',
        search: 'alarm.search',
        selectedEntities: 'alarm.selected-alarms'
      }
    ],
    [
      EntityType.ENTITY_GROUP,
      {
        type: 'entity.type-entity-group'
      }
    ],
    [
      EntityType.WIDGETS_BUNDLE,
      {
        details: 'widgets-bundle.widgets-bundle-details',
        add: 'widgets-bundle.add',
        noEntities: 'widgets-bundle.no-widgets-bundles-text',
        search: 'widgets-bundle.search',
        selectedEntities: 'widgets-bundle.selected-widgets-bundles'
      }
    ],
    [
      EntityType.CONVERTER,
      {
        type: 'entity.type-converter',
        typePlural: 'entity.type-converters',
        list: 'entity.list-of-converters',
        nameStartsWith: 'entity.converter-name-starts-with',
        details: 'converter.converter-details',
        add: 'converter.add',
        noEntities: 'converter.no-converters-text',
        search: 'converter.search',
        selectedEntities: 'converter.selected-converters'
      }
    ],
    [
      EntityType.INTEGRATION,
      {
        type: 'entity.type-integration',
        typePlural: 'entity.type-integrations',
        list: 'entity.list-of-integrations',
        nameStartsWith: 'entity.integration-name-starts-with',
        details: 'integration.integration-details',
        add: 'integration.add',
        noEntities: 'integration.no-integrations-text',
        search: 'integration.search',
        selectedEntities: 'integration.selected-integrations'
      }
    ],
    [
      EntityType.SCHEDULER_EVENT,
      {
        type: 'entity.type-scheduler-event',
        typePlural: 'entity.type-scheduler-events',
        list: 'entity.list-of-scheduler-events',
        nameStartsWith: 'entity.scheduler-event-name-starts-with'
      }
    ],
    [
      EntityType.BLOB_ENTITY,
      {
        type: 'entity.type-blob-entity',
        typePlural: 'entity.type-blob-entities',
        list: 'entity.list-of-blob-entities',
        nameStartsWith: 'entity.blob-entity-name-starts-with'
      }
    ],
    [
      EntityType.ROLE,
      {
        type: 'entity.type-role',
        typePlural: 'entity.type-roles',
        list: 'entity.list-of-roles',
        nameStartsWith: 'entity.role-name-starts-with',
        details: 'role.role-details',
        add: 'role.add',
        noEntities: 'role.no-roles-text',
        search: 'role.search',
        selectedEntities: 'role.selected-roles'
      }
    ],
    [
      EntityType.GROUP_PERMISSION,
      {
        type: 'entity.type-group-permission'
      }
    ],
    [
      AliasEntityType.CURRENT_CUSTOMER,
      {
        type: 'entity.type-entity-view',
        list: 'entity.type-current-customer'
      }
    ]
  ]
);

export const entityTypeResources = new Map<EntityType, EntityTypeResource<BaseData<HasId>>>(
  [
    [
      EntityType.TENANT,
      {
        helpLinkId: 'tenants'
      }
    ],
    [
      EntityType.CUSTOMER,
      {
        helpLinkId: 'customers'
      }
    ],
    [
      EntityType.USER,
      {
        helpLinkId: 'users'
      }
    ],
    [
      EntityType.DEVICE,
      {
        helpLinkId: 'devices'
      }
    ],
    [
      EntityType.ASSET,
      {
        helpLinkId: 'assets'
      }
    ],
    [
      EntityType.ENTITY_VIEW,
      {
        helpLinkId: 'entityViews'
      }
    ],
    [
      EntityType.RULE_CHAIN,
      {
        helpLinkId: 'rulechains'
      }
    ],
    [
      EntityType.DASHBOARD,
      {
        helpLinkId: 'dashboards'
      }
    ],
    [
      EntityType.WIDGETS_BUNDLE,
      {
        helpLinkId: 'widgetsBundles'
      }
    ],
    [
      EntityType.ROLE,
      {
        helpLinkId: 'roles'
      }
    ]
  ]
);

export interface EntitySubtype {
  tenantId: TenantId;
  entityType: EntityType;
  type: string;
}
