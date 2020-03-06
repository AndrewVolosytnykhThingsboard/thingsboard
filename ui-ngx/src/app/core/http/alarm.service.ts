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

import { Injectable } from '@angular/core';
import { defaultHttpOptionsFromConfig, RequestConfig } from './http-utils';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { PageData } from '@shared/models/page/page-data';
import { EntityId } from '@shared/models/id/entity-id';
import {
  Alarm,
  AlarmInfo,
  AlarmQuery,
  AlarmSearchStatus,
  AlarmSeverity,
  AlarmStatus,
  simulatedAlarm
} from '@shared/models/alarm.models';
import { EntityType } from '@shared/models/entity-type.models';
import { Datasource, DatasourceType } from '@shared/models/widget.models';
import { SubscriptionTimewindow } from '@shared/models/time/time.models';
import { UtilsService } from '@core/services/utils.service';
import { TimePageLink } from '@shared/models/page/page-link';
import { Direction, SortOrder } from '@shared/models/page/sort-order';
import { concatMap, expand, map, toArray } from 'rxjs/operators';
import { EMPTY } from 'rxjs';
import Timeout = NodeJS.Timeout;
import { isDefined } from '@core/utils';

interface AlarmSourceListenerQuery {
  entityType: EntityType;
  entityId: string;
  alarmSearchStatus: AlarmSearchStatus;
  alarmStatus: AlarmStatus;
  alarmsMaxCountLoad: number;
  alarmsFetchSize: number;
  fetchOriginator?: boolean;
  limit?: number;
  interval?: number;
  startTime?: number;
  endTime?: number;
  onAlarms?: (alarms: Array<AlarmInfo>) => void;
}

export interface AlarmSourceListener {
  id?: string;
  subscriptionTimewindow: SubscriptionTimewindow;
  alarmSource: Datasource;
  alarmsPollingInterval: number;
  alarmSearchStatus: AlarmSearchStatus;
  alarmsMaxCountLoad: number;
  alarmsFetchSize: number;
  alarmsUpdated: (alarms: Array<AlarmInfo>) => void;
  lastUpdateTs?: number;
  alarmsQuery?: AlarmSourceListenerQuery;
  pollTimer?: Timeout;
}

@Injectable({
  providedIn: 'root'
})
export class AlarmService {

  private alarmSourceListeners: {[id: string]: AlarmSourceListener} = {};

  constructor(
    private http: HttpClient,
    private utils: UtilsService
  ) { }

  public getAlarm(alarmId: string, config?: RequestConfig): Observable<Alarm> {
    return this.http.get<Alarm>(`/api/alarm/${alarmId}`, defaultHttpOptionsFromConfig(config));
  }

  public getAlarmInfo(alarmId: string, config?: RequestConfig): Observable<AlarmInfo> {
    return this.http.get<AlarmInfo>(`/api/alarm/info/${alarmId}`, defaultHttpOptionsFromConfig(config));
  }

  public saveAlarm(alarm: Alarm, config?: RequestConfig): Observable<Alarm> {
    return this.http.post<Alarm>('/api/alarm', alarm, defaultHttpOptionsFromConfig(config));
  }

  public ackAlarm(alarmId: string, config?: RequestConfig): Observable<void> {
    return this.http.post<void>(`/api/alarm/${alarmId}/ack`, null, defaultHttpOptionsFromConfig(config));
  }

  public clearAlarm(alarmId: string, config?: RequestConfig): Observable<void> {
    return this.http.post<void>(`/api/alarm/${alarmId}/clear`, null, defaultHttpOptionsFromConfig(config));
  }

  public deleteAlarm(alarmId: string, config?: RequestConfig): Observable<void> {
    return this.http.delete<void>(`/api/alarm/${alarmId}`, defaultHttpOptionsFromConfig(config));
  }

  public getAlarms(query: AlarmQuery,
                   config?: RequestConfig): Observable<PageData<AlarmInfo>> {
    return this.http.get<PageData<AlarmInfo>>(`/api/alarm${query.toQuery()}`,
      defaultHttpOptionsFromConfig(config));
  }

  public getHighestAlarmSeverity(entityId: EntityId, alarmSearchStatus: AlarmSearchStatus, alarmStatus: AlarmStatus,
                                 config?: RequestConfig): Observable<AlarmSeverity> {
    let url = `/api/alarm/highestSeverity/${entityId.entityType}/${entityId.entityType}`;
    if (alarmSearchStatus) {
      url += `?searchStatus=${alarmSearchStatus}`;
    } else if (alarmStatus) {
      url += `?status=${alarmStatus}`;
    }
    return this.http.get<AlarmSeverity>(url,
      defaultHttpOptionsFromConfig(config));
  }

  public subscribeForAlarms(alarmSourceListener: AlarmSourceListener): void {
    alarmSourceListener.id = this.utils.guid();
    this.alarmSourceListeners[alarmSourceListener.id] = alarmSourceListener;
    const alarmSource = alarmSourceListener.alarmSource;
    if (alarmSource.type === DatasourceType.function) {
      setTimeout(() => {
        alarmSourceListener.alarmsUpdated([simulatedAlarm]);
      }, 0);
    } else if (alarmSource.entityType && alarmSource.entityId) {
      const pollingInterval = alarmSourceListener.alarmsPollingInterval;
      alarmSourceListener.alarmsQuery = {
        entityType: alarmSource.entityType,
        entityId: alarmSource.entityId,
        alarmSearchStatus: alarmSourceListener.alarmSearchStatus,
        alarmStatus: null,
        alarmsMaxCountLoad: alarmSourceListener.alarmsMaxCountLoad,
        alarmsFetchSize: alarmSourceListener.alarmsFetchSize
      };
      const originatorKeys = alarmSource.dataKeys.filter(dataKey => dataKey.name.toLocaleLowerCase().includes('originator'));
      if (originatorKeys.length) {
        alarmSourceListener.alarmsQuery.fetchOriginator = true;
      }
      const subscriptionTimewindow = alarmSourceListener.subscriptionTimewindow;
      if (subscriptionTimewindow.realtimeWindowMs) {
        alarmSourceListener.alarmsQuery.startTime = subscriptionTimewindow.startTs;
      } else {
        alarmSourceListener.alarmsQuery.startTime = subscriptionTimewindow.fixedWindow.startTimeMs;
        alarmSourceListener.alarmsQuery.endTime = subscriptionTimewindow.fixedWindow.endTimeMs;
      }
      alarmSourceListener.alarmsQuery.onAlarms = (alarms) => {
        if (subscriptionTimewindow.realtimeWindowMs) {
          const now = Date.now();
          if (alarmSourceListener.lastUpdateTs) {
            const interval = now - alarmSourceListener.lastUpdateTs;
            alarmSourceListener.alarmsQuery.startTime += interval;
          }
          alarmSourceListener.lastUpdateTs = now;
        }
        alarmSourceListener.alarmsUpdated(alarms);
      };
      this.onPollAlarms(alarmSourceListener.alarmsQuery);
      alarmSourceListener.pollTimer = setInterval(this.onPollAlarms.bind(this), pollingInterval, alarmSourceListener.alarmsQuery);
    }
  }

  public unsubscribeFromAlarms(alarmSourceListener: AlarmSourceListener): void {
    if (alarmSourceListener && alarmSourceListener.id) {
      if (alarmSourceListener.pollTimer) {
        clearInterval(alarmSourceListener.pollTimer);
        alarmSourceListener.pollTimer = null;
      }
      delete this.alarmSourceListeners[alarmSourceListener.id];
    }
  }

  private onPollAlarms(alarmsQuery: AlarmSourceListenerQuery): void {
    this.getAlarmsByAlarmSourceQuery(alarmsQuery).subscribe((alarms) => {
      alarmsQuery.onAlarms(alarms);
    });
  }

  private getAlarmsByAlarmSourceQuery(alarmsQuery: AlarmSourceListenerQuery): Observable<Array<AlarmInfo>> {
    const time = Date.now();
    let pageLink: TimePageLink;
    const sortOrder: SortOrder = {property: 'createdTime', direction: Direction.DESC};
    if (alarmsQuery.limit) {
      pageLink = new TimePageLink(alarmsQuery.limit, 0,
        null,
        sortOrder);
    } else if (alarmsQuery.interval) {
      pageLink = new TimePageLink(alarmsQuery.alarmsFetchSize || 100, 0,
        null,
        sortOrder, time - alarmsQuery.interval);
    } else if (alarmsQuery.startTime) {
      pageLink = new TimePageLink(alarmsQuery.alarmsFetchSize || 100, 0,
        null,
        sortOrder, Math.round(alarmsQuery.startTime));
      if (alarmsQuery.endTime) {
        pageLink.endTime = Math.round(alarmsQuery.endTime);
      }
    }
    let leftToLoad;
    if (isDefined(alarmsQuery.alarmsMaxCountLoad) && alarmsQuery.alarmsMaxCountLoad !== 0) {
      leftToLoad = alarmsQuery.alarmsMaxCountLoad;
      if (leftToLoad < pageLink.pageSize) {
        pageLink.pageSize = leftToLoad;
      }
    }
    return this.fetchAlarms(alarmsQuery, pageLink, leftToLoad);
  }

  private fetchAlarms(query: AlarmSourceListenerQuery,
                      pageLink: TimePageLink, leftToLoad?: number): Observable<Array<AlarmInfo>> {
    const alarmQuery = new AlarmQuery(
      {id: query.entityId, entityType: query.entityType},
      pageLink,
      query.alarmSearchStatus,
      query.alarmStatus,
      query.fetchOriginator,
      null);
    return this.getAlarms(alarmQuery, {ignoreLoading: true}).pipe(
      expand((data) => {
        let continueLoad = data.hasNext && !query.limit;
        if (continueLoad && isDefined(leftToLoad)) {
          leftToLoad -= data.data.length;
          if (leftToLoad === 0) {
            continueLoad = false;
          } else if (leftToLoad < alarmQuery.pageLink.pageSize) {
            alarmQuery.pageLink.pageSize = leftToLoad;
          }
        }
        if (continueLoad) {
          alarmQuery.offset = data.data[data.data.length-1].id.id;
          return this.getAlarms(alarmQuery, {ignoreLoading: true});
        } else {
          return EMPTY;
        }
      }),
      map((data) => data.data),
      concatMap((data) => data),
      toArray(),
      map((data) => data.sort((a, b) => alarmQuery.pageLink.sort(a, b))),
    );
  }
}
