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

import { Component, forwardRef, Input, OnInit } from '@angular/core';
import {
  ControlValueAccessor,
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
  Validators
} from '@angular/forms';
import { AlarmSchedule, AlarmScheduleType, AlarmScheduleTypeTranslationMap } from '@shared/models/device.models';
import { isDefined, isDefinedAndNotNull } from '@core/utils';
import * as _moment from 'moment-timezone';
import { MatCheckboxChange } from '@angular/material/checkbox';

@Component({
  selector: 'tb-alarm-schedule',
  templateUrl: './alarm-schedule.component.html',
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => AlarmScheduleComponent),
    multi: true
  }, {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => AlarmScheduleComponent),
    multi: true
  }]
})
export class AlarmScheduleComponent implements ControlValueAccessor, Validator, OnInit {
  @Input()
  disabled: boolean;

  alarmScheduleForm: FormGroup;

  defaultTimezone = _moment.tz.guess();

  alarmScheduleTypes = Object.keys(AlarmScheduleType);
  alarmScheduleType = AlarmScheduleType;
  alarmScheduleTypeTranslate = AlarmScheduleTypeTranslationMap;

  private modelValue: AlarmSchedule;

  private defaultItems = Array.from({length: 7}, (value, i) => ({
    enabled: true,
    dayOfWeek: i
  }));

  private propagateChange = (v: any) => { };

  constructor(private fb: FormBuilder) {
  }

  ngOnInit(): void {
    this.alarmScheduleForm = this.fb.group({
      type: [AlarmScheduleType.ANY_TIME, Validators.required],
      timezone: [null, Validators.required],
      daysOfWeek: this.fb.array(new Array(7).fill(false)),
      startsOn: [0, Validators.required],
      endsOn: [0, Validators.required],
      items: this.fb.array(Array.from({length: 7}, (value, i) => this.defaultItemsScheduler(i)))
    });
    this.alarmScheduleForm.get('type').valueChanges.subscribe((type) => {
      this.alarmScheduleForm.reset({type, items: this.defaultItems}, {emitEvent: false});
      this.updateValidators(type, true);
      this.alarmScheduleForm.updateValueAndValidity();
    });
    this.alarmScheduleForm.valueChanges.subscribe(() => {
      this.updateModel();
    });
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.alarmScheduleForm.disable({emitEvent: false});
    } else {
      this.alarmScheduleForm.enable({emitEvent: false});
    }
  }

  writeValue(value: AlarmSchedule): void {
    this.modelValue = value;
    if (!isDefinedAndNotNull(this.modelValue)) {
      this.modelValue = {
        type: AlarmScheduleType.ANY_TIME
      };
    }
    switch (this.modelValue.type) {
      case AlarmScheduleType.SPECIFIC_TIME:
        let daysOfWeek = new Array(7).fill(false);
        if (isDefined(this.modelValue.daysOfWeek)) {
          daysOfWeek = daysOfWeek.map((item, index) => this.modelValue.daysOfWeek.indexOf(index + 1) > -1);
        }
        this.alarmScheduleForm.patchValue({
          type: this.modelValue.type,
          timezone: this.modelValue.timezone,
          daysOfWeek,
          startsOn: this.timestampToTime(this.modelValue.startsOn),
          endsOn: this.timestampToTime(this.modelValue.endsOn)
        }, {emitEvent: false});
        break;
      case AlarmScheduleType.CUSTOM:
        if (this.modelValue.items) {
          const alarmDays = [];
          this.modelValue.items
            .sort((a, b) => a.dayOfWeek - b.dayOfWeek)
            .forEach((item, index) => {
              if (item.enabled) {
                this.itemsSchedulerForm.at(index).get('startsOn').enable({emitEvent: false});
                this.itemsSchedulerForm.at(index).get('endsOn').enable({emitEvent: false});
              } else {
                this.itemsSchedulerForm.at(index).get('startsOn').disable({emitEvent: false});
                this.itemsSchedulerForm.at(index).get('endsOn').disable({emitEvent: false});
              }
              alarmDays.push({
                enabled: item.enabled,
                startsOn: this.timestampToTime(item.startsOn),
                endsOn: this.timestampToTime(item.endsOn)
              });
            });
          this.alarmScheduleForm.patchValue({
            type: this.modelValue.type,
            timezone: this.modelValue.timezone,
            items: alarmDays
          }, {emitEvent: false});
        }
        break;
      default:
        this.alarmScheduleForm.patchValue(this.modelValue || undefined, {emitEvent: false});
    }
    this.updateValidators(this.modelValue.type);
  }

  validate(control: FormGroup): ValidationErrors | null {
    return this.alarmScheduleForm.valid ? null : {
      alarmScheduler: {
        valid: false
      }
    };
  }

  weeklyRepeatControl(index: number): FormControl {
    return (this.alarmScheduleForm.get('daysOfWeek') as FormArray).at(index) as FormControl;
  }

  private updateValidators(type: AlarmScheduleType, changedType = false){
    switch (type){
      case AlarmScheduleType.ANY_TIME:
        this.alarmScheduleForm.get('timezone').disable({emitEvent: false});
        this.alarmScheduleForm.get('daysOfWeek').disable({emitEvent: false});
        this.alarmScheduleForm.get('startsOn').disable({emitEvent: false});
        this.alarmScheduleForm.get('endsOn').disable({emitEvent: false});
        this.alarmScheduleForm.get('items').disable({emitEvent: false});
        break;
      case AlarmScheduleType.SPECIFIC_TIME:
        this.alarmScheduleForm.get('timezone').enable({emitEvent: false});
        this.alarmScheduleForm.get('daysOfWeek').enable({emitEvent: false});
        this.alarmScheduleForm.get('startsOn').enable({emitEvent: false});
        this.alarmScheduleForm.get('endsOn').enable({emitEvent: false});
        this.alarmScheduleForm.get('items').disable({emitEvent: false});
        break;
      case AlarmScheduleType.CUSTOM:
        this.alarmScheduleForm.get('timezone').enable({emitEvent: false});
        this.alarmScheduleForm.get('daysOfWeek').disable({emitEvent: false});
        this.alarmScheduleForm.get('startsOn').disable({emitEvent: false});
        this.alarmScheduleForm.get('endsOn').disable({emitEvent: false});
        if (changedType) {
          this.alarmScheduleForm.get('items').enable({emitEvent: false});
        }
        break;
    }
  }

  private updateModel() {
    const value = this.alarmScheduleForm.value;
    if (this.modelValue) {
      if (isDefined(value.daysOfWeek)) {
        value.daysOfWeek = value.daysOfWeek
          .map((day: boolean, index: number) => day ? index + 1 : null)
          .filter(day => !!day);
      }
      if (isDefined(value.startsOn) && value.startsOn !== 0) {
        value.startsOn = this.timeToTimestamp(value.startsOn);
      }
      if (isDefined(value.endsOn) && value.endsOn !== 0) {
        value.endsOn = this.timeToTimestamp(value.endsOn);
      }
      if (isDefined(value.items)){
        value.items = this.alarmScheduleForm.getRawValue().items;
        value.items = value.items.map((item) => {
          return { ...item, startsOn: this.timeToTimestamp(item.startsOn), endsOn: this.timeToTimestamp(item.endsOn)};
        });
      }
      this.modelValue = value;
      this.propagateChange(this.modelValue);
    }
  }

  private timeToTimestamp(date: Date | number): number {
    if (typeof date === 'number' || date === null) {
      return 0;
    }
    return _moment.utc([1970, 0, 1, date.getHours(), date.getMinutes(), date.getSeconds(), 0]).valueOf();
  }

  private timestampToTime(time = 0): Date {
    return new Date(time + new Date().getTimezoneOffset() * 60 * 1000);
  }

  private defaultItemsScheduler(index): FormGroup {
    return this.fb.group({
      enabled: [true],
      dayOfWeek: [index],
      startsOn: [0, Validators.required],
      endsOn: [0, Validators.required]
    });
  }

  changeCustomScheduler($event: MatCheckboxChange, index: number) {
    const value = $event.checked;
    if (value) {
      this.itemsSchedulerForm.at(index).get('startsOn').enable({emitEvent: false});
      this.itemsSchedulerForm.at(index).get('endsOn').enable();
    } else {
      this.itemsSchedulerForm.at(index).get('startsOn').disable({emitEvent: false});
      this.itemsSchedulerForm.at(index).get('endsOn').disable();
    }
  }

  private get itemsSchedulerForm(): FormArray {
    return this.alarmScheduleForm.get('items') as FormArray;
  }
}
