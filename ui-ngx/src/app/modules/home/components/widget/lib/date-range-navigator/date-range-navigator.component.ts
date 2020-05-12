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

import {
  Component,
  Inject,
  InjectionToken,
  Input,
  OnDestroy,
  OnInit,
  ViewChild,
  ViewContainerRef,
  ViewEncapsulation
} from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { WidgetContext } from '@home/models/widget-component.models';
import { UtilsService } from '@core/services/utils.service';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import {
  cloneDateRangeNavigatorModel,
  DateIntervalEntry,
  dateIntervalsMap,
  DateRangeNavigatorModel,
  DateRangeNavigatorSettings,
  getFormattedDate
} from '@home/components/widget/lib/date-range-navigator/date-range-navigator.models';
import { KeyValue } from '@angular/common';
import * as _moment from 'moment';
import { ConnectedPosition, Overlay, OverlayConfig, OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal, PortalInjector } from '@angular/cdk/portal';
import { MatSelect } from '@angular/material/select';
import { Subscription } from 'rxjs';
import { HistoryWindowType, TimewindowType } from '@shared/models/time/time.models';
import { isDefined } from '@core/utils';

@Component({
  selector: 'tb-date-range-navigator-widget',
  templateUrl: './date-range-navigator.component.html',
  styleUrls: ['./date-range-navigator.component.scss']
})
export class DateRangeNavigatorWidgetComponent extends PageComponent implements OnInit, OnDestroy {

  @Input()
  ctx: WidgetContext;

  @ViewChild('datePicker', {static: true}) datePickerSelect: MatSelect;

  settings: DateRangeNavigatorSettings;

  datesMap = dateIntervalsMap;

  advancedModel: DateRangeNavigatorModel = {};

  selectedDateInterval: number;
  customInterval: DateIntervalEntry;
  selectedStepSize: number;

  private firstUpdate = true;
  private dashboardTimewindowChangedSubscription: Subscription;

  originalOrder = (a: KeyValue<number,DateIntervalEntry>, b: KeyValue<number,DateIntervalEntry>): number => {
    return 0;
  };

  constructor(private utils: UtilsService,
              private overlay: Overlay,
              private viewContainerRef: ViewContainerRef,
              protected store: Store<AppState>) {
    super(store);
  }

  ngOnInit(): void {
    this.settings = this.ctx.settings;
    this.settings.useSessionStorage = isDefined(this.settings.useSessionStorage) ? this.settings.useSessionStorage : true;
    let selection;
    if (this.settings.useSessionStorage) {
      selection = this.readFromStorage('date-range');
    }
    if (selection) {
      this.advancedModel = {
        chosenLabel: selection.name,
        startDate: _moment(selection.start),
        endDate: _moment(selection.end)
      };
    } else {
      const end = new Date();
      end.setHours(23, 59, 59, 999);
      this.advancedModel = {
        startDate: _moment((end.getTime() + 1) - this.datesMap[this.settings.initialInterval || 'week'].ts),
        endDate: _moment(end.getTime())
      };
      this.advancedModel.chosenLabel = getFormattedDate(this.advancedModel);
    }
    this.selectedStepSize = this.datesMap[this.settings.stepSize || 'day'].ts;
    this.widgetContextTimewindowSync();
    this.dashboardTimewindowChangedSubscription = this.ctx.dashboard.dashboardTimewindowChanged.subscribe(() => {
      this.widgetContextTimewindowSync();
    });
  }

  ngOnDestroy(): void {
    if (this.dashboardTimewindowChangedSubscription) {
      this.dashboardTimewindowChangedSubscription.unsubscribe();
      this.dashboardTimewindowChangedSubscription = null;
    }
  }

  openNavigatorPanel($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    this.datePickerSelect.close();
    const target = $event.target || $event.srcElement || $event.currentTarget;
    const config = new OverlayConfig();
    config.backdropClass = 'cdk-overlay-transparent-backdrop';
    config.hasBackdrop = true;
    config.panelClass = 'tb-date-range-navigator-panel';
    const connectedPosition: ConnectedPosition = {
      originX: 'end',
      originY: 'bottom',
      overlayX: 'end',
      overlayY: 'top'
    };
    config.positionStrategy = this.overlay.position().flexibleConnectedTo(target as HTMLElement)
      .withPositions([connectedPosition]);

    const overlayRef = this.overlay.create(config);
    overlayRef.backdropClick().subscribe(() => {
      overlayRef.dispose();
    });
    const injectionTokens = new WeakMap<any, any>([
      [DATE_RANGE_NAVIGATOR_PANEL_DATA, {
        model: cloneDateRangeNavigatorModel(this.advancedModel),
        settings: this.settings,
        onChange: model => {
          this.advancedModel = model;
          this.triggerChange();
        }
      } as DateRangeNavigatorPanelData],
      [OverlayRef, overlayRef]
    ]);
    const injector = new PortalInjector(this.viewContainerRef.injector, injectionTokens);
    overlayRef.attach(new ComponentPortal(DateRangeNavigatorPanelComponent,
      this.viewContainerRef, injector));
    this.ctx.detectChanges();
  }

  private widgetContextTimewindowSync() {
    if (!this.firstUpdate) {
      this.updateAdvancedModel();
    }
    this.updateDateInterval();
    if (this.settings.useSessionStorage) {
      this.updateStorageDate();
    }
    if (this.firstUpdate) {
      this.firstUpdate = false;
      this.updateTimewindow(this.advancedModel.startDate.valueOf(), this.advancedModel.endDate.valueOf());
    }
    this.ctx.detectChanges();
  }

  private updateAdvancedModel() {
    const timewindow = this.ctx.dashboardTimewindow;
    if (timewindow.selectedTab === TimewindowType.HISTORY && timewindow.history.historyType === HistoryWindowType.FIXED) {
      const fixedTimewindow = timewindow.history.fixedTimewindow;
      this.advancedModel.startDate = _moment(fixedTimewindow.startTimeMs);
      this.advancedModel.endDate = _moment(fixedTimewindow.endTimeMs);
      this.advancedModel.chosenLabel = getFormattedDate(this.advancedModel);
    }
  }

  private updateTimewindow(startTime: number, endTime: number) {
   this.ctx.dashboard.onUpdateTimewindow(startTime, endTime, 10, true);
  }

  private updateDateInterval() {
    const interval = this.advancedModel.endDate.valueOf() - this.advancedModel.startDate.valueOf();

    for (const key of Object.keys(this.datesMap)) {
      if (Object.prototype.hasOwnProperty.call(this.datesMap, key)) {
        if (this.datesMap[key].ts === interval || this.datesMap[key].ts === interval + 1 || this.datesMap[key].ts === interval - 1) {
          this.selectedDateInterval = this.datesMap[key].ts;
          this.customInterval = undefined;
          return;
        }
      }
    }

    this.selectedDateInterval = interval;
    this.customInterval = {ts: interval, label: 'Custom interval'};
  }

  triggerChange() {
    this.updateTimewindow(this.advancedModel.startDate.valueOf(), this.advancedModel.endDate.valueOf());
  }

  changeInterval() {
    const endTime = this.ctx.dashboard.dashboardTimewindow.history ?
      this.ctx.dashboard.dashboardTimewindow.history.fixedTimewindow.endTimeMs :
      this.advancedModel.endDate.valueOf();
    this.updateTimewindow(endTime - this.selectedDateInterval / 2, endTime + this.selectedDateInterval / 2);
  }

  goBack() {
    this.step(-1);
  }

  goForth() {
    this.step(1);
  }

  private step(direction: number) {
    const startTime = this.ctx.dashboard.dashboardTimewindow.history ?
      this.ctx.dashboard.dashboardTimewindow.history.fixedTimewindow.startTimeMs :
      this.advancedModel.startDate.valueOf();
    const endTime = this.ctx.dashboard.dashboardTimewindow.history ?
      this.ctx.dashboard.dashboardTimewindow.history.fixedTimewindow.endTimeMs :
      this.advancedModel.endDate.valueOf();
    this.updateTimewindow(startTime + this.selectedStepSize * direction, endTime + this.selectedStepSize * direction);
  }

  private readFromStorage(itemKey: string): any {
    if (window.sessionStorage.getItem(itemKey)) {
      const selection = JSON.parse(window.sessionStorage.getItem(itemKey));
      selection.start = new Date(parseInt(selection.start, 10));
      selection.end = new Date(parseInt(selection.end, 10));
      return selection;
    }
    return undefined;
  }

  private updateStorageDate() {
    this.saveIntoStorage('date-range', {
      start: this.advancedModel.startDate.valueOf(),
      end: this.advancedModel.endDate.valueOf(),
      name: this.advancedModel.chosenLabel
    });
  }

  private saveIntoStorage(keyName: string, selection: any) {
    if (selection) {
      window.sessionStorage.setItem(keyName, JSON.stringify(selection));
    }
  }
}

const DATE_RANGE_NAVIGATOR_PANEL_DATA = new InjectionToken<any>('DateRangeNavigatorPanelData');

export interface DateRangeNavigatorPanelData {
  model: DateRangeNavigatorModel;
  settings: DateRangeNavigatorSettings;
  onChange: (model: DateRangeNavigatorModel) => void;
}

@Component({
  selector: 'tb-date-range-navigator-panel',
  templateUrl: './date-range-navigator-panel.component.html',
  styleUrls: ['./date-range-navigator-panel.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class DateRangeNavigatorPanelComponent {

  settings: DateRangeNavigatorSettings;

  model: DateRangeNavigatorModel;

  locale: any = {};

  constructor(@Inject(DATE_RANGE_NAVIGATOR_PANEL_DATA) public data: DateRangeNavigatorPanelData,
              private overlayRef: OverlayRef) {
    this.model = data.model;
    this.settings = data.settings;
    this.locale.firstDay = this.settings.firstDayOfWeek || 1;
    this.locale.daysOfWeek = _moment.weekdaysMin();
    this.locale.monthNames = _moment.monthsShort();
  }

  choosedDate($event) {
    this.model = $event;
    this.model.chosenLabel = getFormattedDate(this.model);
    if (this.settings.autoConfirm) {
      this.data.onChange(this.model);
      this.overlayRef.dispose();
    }
  }

  apply() {
    this.data.onChange(this.model);
    this.overlayRef.dispose();
  }

}