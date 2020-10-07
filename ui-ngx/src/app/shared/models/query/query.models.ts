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

import { AliasFilterType, EntityFilters } from '@shared/models/alias.models';
import { EntityId } from '@shared/models/id/entity-id';
import { SortDirection } from '@angular/material/sort';
import { DataKeyType } from '@shared/models/telemetry/telemetry.models';
import { EntityInfo } from '@shared/models/entity.models';
import { EntityType } from '@shared/models/entity-type.models';
import { DataKey, Datasource, DatasourceType } from '@shared/models/widget.models';
import { PageData } from '@shared/models/page/page-data';
import { isDefined, isDefinedAndNotNull, isEqual } from '@core/utils';
import { TranslateService } from '@ngx-translate/core';
import { AlarmInfo, AlarmSearchStatus, AlarmSeverity } from '../alarm.models';
import { Filter } from '@material-ui/icons';
import { DatePipe } from '@angular/common';

export enum EntityKeyType {
  ATTRIBUTE = 'ATTRIBUTE',
  CLIENT_ATTRIBUTE = 'CLIENT_ATTRIBUTE',
  SHARED_ATTRIBUTE = 'SHARED_ATTRIBUTE',
  SERVER_ATTRIBUTE = 'SERVER_ATTRIBUTE',
  TIME_SERIES = 'TIME_SERIES',
  ENTITY_FIELD = 'ENTITY_FIELD',
  ALARM_FIELD = 'ALARM_FIELD'
}

export const entityKeyTypeTranslationMap = new Map<EntityKeyType, string>(
  [
    [EntityKeyType.ATTRIBUTE, 'filter.key-type.attribute'],
    [EntityKeyType.TIME_SERIES, 'filter.key-type.timeseries'],
    [EntityKeyType.ENTITY_FIELD, 'filter.key-type.entity-field']
  ]
);

export function entityKeyTypeToDataKeyType(entityKeyType: EntityKeyType): DataKeyType {
  switch (entityKeyType) {
    case EntityKeyType.ATTRIBUTE:
    case EntityKeyType.CLIENT_ATTRIBUTE:
    case EntityKeyType.SHARED_ATTRIBUTE:
    case EntityKeyType.SERVER_ATTRIBUTE:
      return DataKeyType.attribute;
    case EntityKeyType.TIME_SERIES:
      return DataKeyType.timeseries;
    case EntityKeyType.ENTITY_FIELD:
      return DataKeyType.entityField;
    case EntityKeyType.ALARM_FIELD:
      return DataKeyType.alarm;
  }
}

export function dataKeyTypeToEntityKeyType(dataKeyType: DataKeyType): EntityKeyType {
  switch (dataKeyType) {
    case DataKeyType.timeseries:
      return EntityKeyType.TIME_SERIES;
    case DataKeyType.attribute:
      return EntityKeyType.ATTRIBUTE;
    case DataKeyType.function:
      return EntityKeyType.ENTITY_FIELD;
    case DataKeyType.alarm:
      return EntityKeyType.ALARM_FIELD;
    case DataKeyType.entityField:
      return EntityKeyType.ENTITY_FIELD;
  }
}

export interface EntityKey {
  type: EntityKeyType;
  key: string;
}

export function dataKeyToEntityKey(dataKey: DataKey): EntityKey {
  return {
    key: dataKey.name,
    type: dataKeyTypeToEntityKeyType(dataKey.type)
  };
}

export enum EntityKeyValueType {
  STRING = 'STRING',
  NUMERIC = 'NUMERIC',
  BOOLEAN = 'BOOLEAN',
  DATE_TIME = 'DATE_TIME'
}

export interface EntityKeyValueTypeData {
  name: string;
  icon: string;
}

export const entityKeyValueTypesMap = new Map<EntityKeyValueType, EntityKeyValueTypeData>(
  [
    [
      EntityKeyValueType.STRING,
      {
        name: 'filter.value-type.string',
        icon: 'mdi:format-text'
      }
    ],
    [
      EntityKeyValueType.NUMERIC,
      {
        name: 'filter.value-type.numeric',
        icon: 'mdi:numeric'
      }
    ],
    [
      EntityKeyValueType.BOOLEAN,
      {
        name: 'filter.value-type.boolean',
        icon: 'mdi:checkbox-marked-outline'
      }
    ],
    [
      EntityKeyValueType.DATE_TIME,
      {
        name: 'filter.value-type.date-time',
        icon: 'mdi:calendar-clock'
      }
    ]
  ]
);

export function entityKeyValueTypeToFilterPredicateType(valueType: EntityKeyValueType): FilterPredicateType {
  switch (valueType) {
    case EntityKeyValueType.STRING:
      return FilterPredicateType.STRING;
    case EntityKeyValueType.NUMERIC:
    case EntityKeyValueType.DATE_TIME:
      return FilterPredicateType.NUMERIC;
    case EntityKeyValueType.BOOLEAN:
      return FilterPredicateType.BOOLEAN;
  }
}

export function createDefaultFilterPredicateInfo(valueType: EntityKeyValueType, complex: boolean): KeyFilterPredicateInfo {
  const predicate = createDefaultFilterPredicate(valueType, complex);
  return {
    keyFilterPredicate: predicate,
    userInfo: createDefaultFilterPredicateUserInfo()
  };
}

export function createDefaultFilterPredicateUserInfo(): KeyFilterPredicateUserInfo {
  return {
    editable: true,
    label: '',
    autogeneratedLabel: true,
    order: 0
  };
}

export function createDefaultFilterPredicate(valueType: EntityKeyValueType, complex: boolean): KeyFilterPredicate {
  const predicate = {
    type: complex ? FilterPredicateType.COMPLEX : entityKeyValueTypeToFilterPredicateType(valueType)
  } as KeyFilterPredicate;
  switch (predicate.type) {
    case FilterPredicateType.STRING:
      predicate.operation = StringOperation.STARTS_WITH;
      predicate.value = {
        defaultValue: ''
      };
      predicate.ignoreCase = false;
      break;
    case FilterPredicateType.NUMERIC:
      predicate.operation = NumericOperation.EQUAL;
      predicate.value = {
        defaultValue: valueType === EntityKeyValueType.DATE_TIME ? Date.now() : 0
      };
      break;
    case FilterPredicateType.BOOLEAN:
      predicate.operation = BooleanOperation.EQUAL;
      predicate.value = {
        defaultValue: false
      };
      break;
    case FilterPredicateType.COMPLEX:
      predicate.operation = ComplexOperation.AND;
      predicate.predicates = [];
      break;
  }
  return predicate;
}

export enum FilterPredicateType {
  STRING = 'STRING',
  NUMERIC = 'NUMERIC',
  BOOLEAN = 'BOOLEAN',
  COMPLEX = 'COMPLEX'
}

export enum StringOperation {
  EQUAL = 'EQUAL',
  NOT_EQUAL = 'NOT_EQUAL',
  STARTS_WITH = 'STARTS_WITH',
  ENDS_WITH = 'ENDS_WITH',
  CONTAINS = 'CONTAINS',
  NOT_CONTAINS = 'NOT_CONTAINS'
}

export const stringOperationTranslationMap = new Map<StringOperation, string>(
  [
    [StringOperation.EQUAL, 'filter.operation.equal'],
    [StringOperation.NOT_EQUAL, 'filter.operation.not-equal'],
    [StringOperation.STARTS_WITH, 'filter.operation.starts-with'],
    [StringOperation.ENDS_WITH, 'filter.operation.ends-with'],
    [StringOperation.CONTAINS, 'filter.operation.contains'],
    [StringOperation.NOT_CONTAINS, 'filter.operation.not-contains']
  ]
);

export enum NumericOperation {
  EQUAL = 'EQUAL',
  NOT_EQUAL = 'NOT_EQUAL',
  GREATER = 'GREATER',
  LESS = 'LESS',
  GREATER_OR_EQUAL = 'GREATER_OR_EQUAL',
  LESS_OR_EQUAL = 'LESS_OR_EQUAL'
}

export const numericOperationTranslationMap = new Map<NumericOperation, string>(
  [
    [NumericOperation.EQUAL, 'filter.operation.equal'],
    [NumericOperation.NOT_EQUAL, 'filter.operation.not-equal'],
    [NumericOperation.GREATER, 'filter.operation.greater'],
    [NumericOperation.LESS, 'filter.operation.less'],
    [NumericOperation.GREATER_OR_EQUAL, 'filter.operation.greater-or-equal'],
    [NumericOperation.LESS_OR_EQUAL, 'filter.operation.less-or-equal']
  ]
);

export enum BooleanOperation {
  EQUAL = 'EQUAL',
  NOT_EQUAL = 'NOT_EQUAL'
}

export const booleanOperationTranslationMap = new Map<BooleanOperation, string>(
  [
    [BooleanOperation.EQUAL, 'filter.operation.equal'],
    [BooleanOperation.NOT_EQUAL, 'filter.operation.not-equal']
  ]
);

export enum ComplexOperation {
  AND = 'AND',
  OR = 'OR'
}

export const complexOperationTranslationMap = new Map<ComplexOperation, string>(
  [
    [ComplexOperation.AND, 'filter.operation.and'],
    [ComplexOperation.OR, 'filter.operation.or']
  ]
);

export enum DynamicValueSourceType {
  CURRENT_TENANT = 'CURRENT_TENANT',
  CURRENT_CUSTOMER = 'CURRENT_CUSTOMER',
  CURRENT_USER = 'CURRENT_USER',
  CURRENT_DEVICE = 'CURRENT_DEVICE'
}

export const dynamicValueSourceTypeTranslationMap = new Map<DynamicValueSourceType, string>(
  [
    [DynamicValueSourceType.CURRENT_TENANT, 'filter.current-tenant'],
    [DynamicValueSourceType.CURRENT_CUSTOMER, 'filter.current-customer'],
    [DynamicValueSourceType.CURRENT_USER, 'filter.current-user'],
    [DynamicValueSourceType.CURRENT_DEVICE, 'filter.current-device']
  ]
);

export interface DynamicValue<T> {
  sourceType: DynamicValueSourceType;
  sourceAttribute: string;
}

export interface FilterPredicateValue<T> {
  defaultValue: T;
  userValue?: T;
  dynamicValue?: DynamicValue<T>;
}

export interface StringFilterPredicate {
  type: FilterPredicateType.STRING;
  operation: StringOperation;
  value: FilterPredicateValue<string>;
  ignoreCase: boolean;
}

export interface NumericFilterPredicate {
  type: FilterPredicateType.NUMERIC;
  operation: NumericOperation;
  value: FilterPredicateValue<number>;
}

export interface BooleanFilterPredicate {
  type: FilterPredicateType.BOOLEAN;
  operation: BooleanOperation;
  value: FilterPredicateValue<boolean>;
}

export interface BaseComplexFilterPredicate<T extends KeyFilterPredicate | KeyFilterPredicateInfo> {
  type: FilterPredicateType.COMPLEX;
  operation: ComplexOperation;
  predicates: Array<T>;
}

export type ComplexFilterPredicate = BaseComplexFilterPredicate<KeyFilterPredicate>;

export type ComplexFilterPredicateInfo = BaseComplexFilterPredicate<KeyFilterPredicateInfo>;

export type KeyFilterPredicate = StringFilterPredicate |
  NumericFilterPredicate |
  BooleanFilterPredicate |
  ComplexFilterPredicate |
  ComplexFilterPredicateInfo;

export interface KeyFilterPredicateUserInfo {
  editable: boolean;
  label: string;
  autogeneratedLabel: boolean;
  order?: number;
}

export interface KeyFilterPredicateInfo {
  keyFilterPredicate: KeyFilterPredicate;
  userInfo: KeyFilterPredicateUserInfo;
}

export interface KeyFilter {
  key: EntityKey;
  valueType: EntityKeyValueType;
  predicate: KeyFilterPredicate;
}

export interface KeyFilterInfo {
  key: EntityKey;
  valueType: EntityKeyValueType;
  predicates: Array<KeyFilterPredicateInfo>;
}

export interface FilterInfo {
  filter: string;
  editable: boolean;
  keyFilters: Array<KeyFilterInfo>;
}

export interface FiltersInfo {
  datasourceFilters: {[datasourceIndex: number]: FilterInfo};
}

export function keyFiltersToText(translate: TranslateService, datePipe: DatePipe, keyFilters: Array<KeyFilter>): string {
  const filtersText = keyFilters.map(keyFilter =>
      keyFilterToText(translate, datePipe, keyFilter,
        keyFilters.length > 1 ? ComplexOperation.AND : undefined));
  let result: string;
  if (filtersText.length > 1) {
    const andText = translate.instant('filter.operation.and');
    result = filtersText.join(' <span class="tb-filter-complex-operation">' + andText + '</span> ');
  } else {
    result = filtersText[0];
  }
  return result;
}

export function keyFilterToText(translate: TranslateService, datePipe: DatePipe, keyFilter: KeyFilter,
                                parentComplexOperation?: ComplexOperation): string {
  const keyFilterPredicate = keyFilter.predicate;
  return keyFilterPredicateToText(translate, datePipe, keyFilter, keyFilterPredicate, parentComplexOperation);
}

export function keyFilterPredicateToText(translate: TranslateService,
                                         datePipe: DatePipe,
                                         keyFilter: KeyFilter,
                                         keyFilterPredicate: KeyFilterPredicate,
                                         parentComplexOperation?: ComplexOperation): string {
  if (keyFilterPredicate.type === FilterPredicateType.COMPLEX) {
    const complexPredicate = keyFilterPredicate as ComplexFilterPredicate;
    const complexOperation = complexPredicate.operation;
    const complexPredicatesText =
      complexPredicate.predicates.map(predicate => keyFilterPredicateToText(translate, datePipe, keyFilter, predicate, complexOperation));
    if (complexPredicatesText.length > 1) {
      const operationText = translate.instant(complexOperationTranslationMap.get(complexOperation));
      let result = complexPredicatesText.join(' <span class="tb-filter-complex-operation">' + operationText + '</span> ');
      if (complexOperation === ComplexOperation.OR && parentComplexOperation && ComplexOperation.OR !== parentComplexOperation) {
        result = `<span class="tb-filter-bracket"><span class="tb-left-bracket">(</span>${result}<span class="tb-right-bracket">)</span></span>`;
      }
      return result;
    } else {
      return complexPredicatesText[0];
    }
  } else {
    return simpleKeyFilterPredicateToText(translate, datePipe, keyFilter, keyFilterPredicate);
  }
}

function simpleKeyFilterPredicateToText(translate: TranslateService,
                                        datePipe: DatePipe,
                                        keyFilter: KeyFilter,
                                        keyFilterPredicate: StringFilterPredicate |
                                                            NumericFilterPredicate |
                                                            BooleanFilterPredicate): string {
  const key = keyFilter.key.key;
  let operation: string;
  let value: string;
  const val = keyFilterPredicate.value;
  const dynamicValue = !!val.dynamicValue && !!val.dynamicValue.sourceType;
  if (dynamicValue) {
    value = '<span class="tb-filter-dynamic-value"><span class="tb-filter-dynamic-source">' +
    translate.instant(dynamicValueSourceTypeTranslationMap.get(val.dynamicValue.sourceType)) + '</span>';
    value += '.<span class="tb-filter-value">' + val.dynamicValue.sourceAttribute + '</span></span>';
  }
  switch (keyFilterPredicate.type) {
    case FilterPredicateType.STRING:
      operation = translate.instant(stringOperationTranslationMap.get(keyFilterPredicate.operation));
      if (keyFilterPredicate.ignoreCase) {
        operation += ' ' + translate.instant('filter.ignore-case');
      }
      if (!dynamicValue) {
        value = `'${keyFilterPredicate.value.defaultValue}'`;
      }
      break;
    case FilterPredicateType.NUMERIC:
      operation = translate.instant(numericOperationTranslationMap.get(keyFilterPredicate.operation));
      if (!dynamicValue) {
        if (keyFilter.valueType === EntityKeyValueType.DATE_TIME) {
          value = datePipe.transform(keyFilterPredicate.value.defaultValue, 'yyyy-MM-dd HH:mm');
        } else {
          value = keyFilterPredicate.value.defaultValue + '';
        }
      }
      break;
    case FilterPredicateType.BOOLEAN:
      operation = translate.instant(booleanOperationTranslationMap.get(keyFilterPredicate.operation));
      value = translate.instant(keyFilterPredicate.value.defaultValue ? 'value.true' : 'value.false');
      break;
  }
  if (!dynamicValue) {
    value = `<span class="tb-filter-value">${value}</span>`;
  }
  return `<span class="tb-filter-predicate"><span class="tb-filter-entity-key">${key}</span> <span class="tb-filter-simple-operation">${operation}</span> ${value}</span>`;
}

export function keyFilterInfosToKeyFilters(keyFilterInfos: Array<KeyFilterInfo>): Array<KeyFilter> {
  if (!keyFilterInfos) {
    return [];
  }
  const keyFilters: Array<KeyFilter> = [];
  for (const keyFilterInfo of keyFilterInfos) {
    const key = keyFilterInfo.key;
    for (const predicate of keyFilterInfo.predicates) {
      const keyFilter: KeyFilter = {
        key,
        valueType: keyFilterInfo.valueType,
        predicate: keyFilterPredicateInfoToKeyFilterPredicate(predicate)
      };
      keyFilters.push(keyFilter);
    }
  }
  return keyFilters;
}

export function keyFiltersToKeyFilterInfos(keyFilters: Array<KeyFilter>): Array<KeyFilterInfo> {
  const keyFilterInfos: Array<KeyFilterInfo> = [];
  const keyFilterInfoMap: {[infoKey: string]: KeyFilterInfo} = {};
  for (const keyFilter of keyFilters) {
    const key = keyFilter.key;
    const infoKey = key.key + key.type + keyFilter.valueType;
    let keyFilterInfo = keyFilterInfoMap[infoKey];
    if (!keyFilterInfo) {
      keyFilterInfo = {
        key,
        valueType: keyFilter.valueType,
        predicates: []
      };
      keyFilterInfoMap[infoKey] = keyFilterInfo;
      keyFilterInfos.push(keyFilterInfo);
    }
    if (keyFilter.predicate) {
      keyFilterInfo.predicates.push(keyFilterPredicateToKeyFilterPredicateInfo(keyFilter.predicate));
    }
  }
  return keyFilterInfos;
}

export function filterInfoToKeyFilters(filter: FilterInfo): Array<KeyFilter> {
  const keyFilterInfos = filter.keyFilters;
  const keyFilters: Array<KeyFilter> = [];
  for (const keyFilterInfo of keyFilterInfos) {
    const key = keyFilterInfo.key;
    for (const predicate of keyFilterInfo.predicates) {
      const keyFilter: KeyFilter = {
        key,
        valueType: keyFilterInfo.valueType,
        predicate: keyFilterPredicateInfoToKeyFilterPredicate(predicate)
      };
      keyFilters.push(keyFilter);
    }
  }
  return keyFilters;
}

export function keyFilterPredicateInfoToKeyFilterPredicate(keyFilterPredicateInfo: KeyFilterPredicateInfo): KeyFilterPredicate {
  let keyFilterPredicate = keyFilterPredicateInfo.keyFilterPredicate;
  if (keyFilterPredicate.type === FilterPredicateType.COMPLEX) {
    const complexInfo = keyFilterPredicate as ComplexFilterPredicateInfo;
    const predicates = complexInfo.predicates.map((predicateInfo => keyFilterPredicateInfoToKeyFilterPredicate(predicateInfo)));
    keyFilterPredicate = {
      type: FilterPredicateType.COMPLEX,
      operation: complexInfo.operation,
      predicates
    } as ComplexFilterPredicate;
  }
  return keyFilterPredicate;
}

export function keyFilterPredicateToKeyFilterPredicateInfo(keyFilterPredicate: KeyFilterPredicate): KeyFilterPredicateInfo {
  const keyFilterPredicateInfo: KeyFilterPredicateInfo = {
    keyFilterPredicate: null,
    userInfo: null
  };
  if (keyFilterPredicate.type === FilterPredicateType.COMPLEX) {
    const complexPredicate = keyFilterPredicate as ComplexFilterPredicate;
    const predicateInfos = complexPredicate.predicates.map(
      predicate => keyFilterPredicateToKeyFilterPredicateInfo(predicate));
    keyFilterPredicateInfo.keyFilterPredicate = {
      predicates: predicateInfos,
      operation: complexPredicate.operation,
      type: FilterPredicateType.COMPLEX
    } as ComplexFilterPredicateInfo;
  } else {
    keyFilterPredicateInfo.keyFilterPredicate = keyFilterPredicate;
  }
  return keyFilterPredicateInfo;
}

export function isFilterEditable(filter: FilterInfo): boolean {
  if (filter.editable) {
    return filter.keyFilters.some(value => isKeyFilterInfoEditable(value));
  } else {
    return false;
  }
}

export function isKeyFilterInfoEditable(keyFilterInfo: KeyFilterInfo): boolean {
  return keyFilterInfo.predicates.some(value => isPredicateInfoEditable(value));
}

export function isPredicateInfoEditable(predicateInfo: KeyFilterPredicateInfo): boolean {
  if (predicateInfo.keyFilterPredicate.type === FilterPredicateType.COMPLEX) {
    const complexFilterPredicateInfo: ComplexFilterPredicateInfo = predicateInfo.keyFilterPredicate as ComplexFilterPredicateInfo;
    return complexFilterPredicateInfo.predicates.some(value => isPredicateInfoEditable(value));
  } else {
    return predicateInfo.userInfo.editable;
  }
}

export interface UserFilterInputInfo {
  label: string;
  valueType: EntityKeyValueType;
  info: KeyFilterPredicateInfo;
}

export function filterToUserFilterInfoList(filter: Filter, translate: TranslateService): Array<UserFilterInputInfo> {
  const result = filter.keyFilters.map((keyFilterInfo => keyFilterInfoToUserFilterInfoList(keyFilterInfo, translate)));
  let userInputs: Array<UserFilterInputInfo> = [].concat.apply([], result);
  userInputs = userInputs.sort((input1, input2) => {
    const order1 = isDefined(input1.info.userInfo.order) ? input1.info.userInfo.order : 0;
    const order2 = isDefined(input2.info.userInfo.order) ? input2.info.userInfo.order : 0;
    return order1 - order2;
  });
  return userInputs;
}

export function keyFilterInfoToUserFilterInfoList(keyFilterInfo: KeyFilterInfo, translate: TranslateService): Array<UserFilterInputInfo> {
  const result = keyFilterInfo.predicates.map((predicateInfo => predicateInfoToUserFilterInfoList(keyFilterInfo.key,
    keyFilterInfo.valueType, predicateInfo, translate)));
  return [].concat.apply([], result);
}

export function predicateInfoToUserFilterInfoList(key: EntityKey,
                                                  valueType: EntityKeyValueType,
                                                  predicateInfo: KeyFilterPredicateInfo,
                                                  translate: TranslateService): Array<UserFilterInputInfo> {
  if (predicateInfo.keyFilterPredicate.type === FilterPredicateType.COMPLEX) {
    const complexFilterPredicateInfo: ComplexFilterPredicateInfo = predicateInfo.keyFilterPredicate as ComplexFilterPredicateInfo;
    const result = complexFilterPredicateInfo.predicates.map((predicateInfo1 =>
      predicateInfoToUserFilterInfoList(key, valueType, predicateInfo1, translate)));
    return [].concat.apply([], result);
  } else {
    if (predicateInfo.userInfo.editable) {
      const userInput: UserFilterInputInfo = {
        info: predicateInfo,
        label: predicateInfo.userInfo.label,
        valueType
      };
      if (predicateInfo.userInfo.autogeneratedLabel) {
        userInput.label = generateUserFilterValueLabel(key.key, valueType,
          predicateInfo.keyFilterPredicate.operation, translate);
      }
      return [userInput];
    } else {
      return [];
    }
  }
}

export function generateUserFilterValueLabel(key: string, valueType: EntityKeyValueType,
                                             operation: StringOperation | BooleanOperation | NumericOperation,
                                             translate: TranslateService) {
  let label = key;
  let operationTranslationKey: string;
  switch (valueType) {
    case EntityKeyValueType.STRING:
      operationTranslationKey = stringOperationTranslationMap.get(operation as StringOperation);
      break;
    case EntityKeyValueType.NUMERIC:
    case EntityKeyValueType.DATE_TIME:
      operationTranslationKey = numericOperationTranslationMap.get(operation as NumericOperation);
      break;
    case EntityKeyValueType.BOOLEAN:
      operationTranslationKey = booleanOperationTranslationMap.get(operation as BooleanOperation);
      break;
  }
  label += ' ' + translate.instant(operationTranslationKey);
  return label;
}

export interface Filter extends FilterInfo {
  id: string;
}

export interface Filters {
  [id: string]: Filter;
}

export interface EntityFilter extends EntityFilters {
  type?: AliasFilterType;
}

export enum Direction {
  ASC = 'ASC',
  DESC = 'DESC'
}

export interface EntityDataSortOrder {
  key: EntityKey;
  direction: Direction;
}

export interface EntityDataPageLink {
  pageSize: number;
  page: number;
  textSearch?: string;
  sortOrder?: EntityDataSortOrder;
  dynamic?: boolean;
}

export interface AlarmDataPageLink extends EntityDataPageLink {
  startTs?: number;
  endTs?: number;
  timeWindow?: number;
  typeList?: Array<string>;
  statusList?: Array<AlarmSearchStatus>;
  severityList?: Array<AlarmSeverity>;
  searchPropagatedAlarms?: boolean;
}

export function entityDataPageLinkSortDirection(pageLink: EntityDataPageLink): SortDirection {
  if (pageLink.sortOrder) {
    return (pageLink.sortOrder.direction + '').toLowerCase() as SortDirection;
  } else {
    return '' as SortDirection;
  }
}

export function createDefaultEntityDataPageLink(pageSize: number): EntityDataPageLink {
  return {
    pageSize,
    page: 0,
    sortOrder: {
      key: {
        type: EntityKeyType.ENTITY_FIELD,
        key: 'createdTime'
      },
      direction: Direction.DESC
    }
  };
}

export const singleEntityDataPageLink: EntityDataPageLink = createDefaultEntityDataPageLink(1);
export const defaultEntityDataPageLink: EntityDataPageLink = createDefaultEntityDataPageLink(1024);

export interface EntityCountQuery {
  entityFilter: EntityFilter;
}

export interface AbstractDataQuery<T extends EntityDataPageLink> extends EntityCountQuery {
  pageLink: T;
  entityFields?: Array<EntityKey>;
  latestValues?: Array<EntityKey>;
  keyFilters?: Array<KeyFilter>;
}

export interface EntityDataQuery extends AbstractDataQuery<EntityDataPageLink> {
}

export interface AlarmDataQuery extends AbstractDataQuery<AlarmDataPageLink> {
  alarmFields?: Array<EntityKey>;
}

export interface TsValue {
  ts: number;
  value: string;
}

export interface EntityData {
  entityId: EntityId;
  latest: {[entityKeyType: string]: {[key: string]: TsValue}};
  timeseries: {[key: string]: Array<TsValue>};
}

export interface AlarmData extends AlarmInfo {
  entityId: string;
  latest: {[entityKeyType: string]: {[key: string]: TsValue}};
}

export function getLatestDataValue(latest: {[entityKeyType: string]: {[key: string]: TsValue}},
                                   entityKeyType: EntityKeyType, key: string, defaultValue?: string): string {
  let value = defaultValue;
  const fields = latest[entityKeyType];
  if (fields) {
    const tsValue = fields[key];
    if (tsValue && isDefinedAndNotNull(tsValue.value)) {
      value = tsValue.value;
    }
  }
  return value;
}

export function entityPageDataChanged(prevPageData: PageData<EntityData>, nextPageData: PageData<EntityData>): boolean {
  const prevIds = prevPageData.data.map((entityData) => entityData.entityId.id);
  const nextIds = nextPageData.data.map((entityData) => entityData.entityId.id);
  return !isEqual(prevIds, nextIds);
}

export const entityInfoFields: EntityKey[] = [
  {
    type: EntityKeyType.ENTITY_FIELD,
    key: 'name'
  },
  {
    type: EntityKeyType.ENTITY_FIELD,
    key: 'label'
  },
  {
    type: EntityKeyType.ENTITY_FIELD,
    key: 'additionalInfo'
  }
];

export function entityDataToEntityInfo(entityData: EntityData): EntityInfo {
  const entityInfo: EntityInfo = {
    id: entityData.entityId.id,
    entityType: entityData.entityId.entityType as EntityType
  };
  if (entityData.latest && entityData.latest[EntityKeyType.ENTITY_FIELD]) {
    const fields = entityData.latest[EntityKeyType.ENTITY_FIELD];
    if (fields.name) {
      entityInfo.name = fields.name.value;
    } else {
      entityInfo.name = '';
    }
    if (fields.label) {
      entityInfo.label = fields.label.value;
    } else {
      entityInfo.label = '';
    }
    entityInfo.entityDescription = '';
    if (fields.additionalInfo) {
      const additionalInfo = fields.additionalInfo.value;
      if (additionalInfo && additionalInfo.length) {
        try {
          const additionalInfoJson = JSON.parse(additionalInfo);
          if (additionalInfoJson && additionalInfoJson.description) {
            entityInfo.entityDescription = additionalInfoJson.description;
          }
        } catch (e) {}
      }
    }
  }
  return entityInfo;
}

export function updateDatasourceFromEntityInfo(datasource: Datasource, entity: EntityInfo, createFilter = false) {
  datasource.entity = {
    id: {
      entityType: entity.entityType,
      id: entity.id
    }
  };
  datasource.entityId = entity.id;
  datasource.entityType = entity.entityType;
  if (datasource.type === DatasourceType.entity) {
    datasource.entityName = entity.name;
    datasource.entityLabel = entity.label;
    datasource.name = entity.name;
    datasource.entityDescription = entity.entityDescription;
    datasource.entity.label = entity.label;
    datasource.entity.name = entity.name;
    if (createFilter) {
      datasource.entityFilter = {
        type: AliasFilterType.singleEntity,
        singleEntity: {
          id: entity.id,
          entityType: entity.entityType
        }
      };
    }
  }
}
