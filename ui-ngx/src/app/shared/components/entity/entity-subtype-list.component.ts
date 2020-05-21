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

import { AfterViewInit, Component, ElementRef, forwardRef, Input, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ControlValueAccessor, FormBuilder, FormGroup, NG_VALUE_ACCESSOR, Validators } from '@angular/forms';
import { Observable, Subscription, throwError } from 'rxjs';
import { map, mergeMap, publishReplay, refCount, share } from 'rxjs/operators';
import { Store } from '@ngrx/store';
import { AppState } from '@app/core/core.state';
import { TranslateService } from '@ngx-translate/core';
import { EntitySubtype, EntityType } from '@shared/models/entity-type.models';
import { MatAutocomplete, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatChipInputEvent, MatChipList } from '@angular/material/chips';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { AssetService } from '@core/http/asset.service';
import { DeviceService } from '@core/http/device.service';
import { EntityViewService } from '@core/http/entity-view.service';
import { BroadcastService } from '@core/services/broadcast.service';
import { COMMA, ENTER, SEMICOLON } from '@angular/cdk/keycodes';

@Component({
  selector: 'tb-entity-subtype-list',
  templateUrl: './entity-subtype-list.component.html',
  styleUrls: ['./entity-subtype-list.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => EntitySubTypeListComponent),
      multi: true
    }
  ]
})
export class EntitySubTypeListComponent implements ControlValueAccessor, OnInit, AfterViewInit, OnDestroy {

  entitySubtypeListFormGroup: FormGroup;

  modelValue: Array<string> | null;

  private requiredValue: boolean;
  get required(): boolean {
    return this.requiredValue;
  }
  @Input()
  set required(value: boolean) {
    const newVal = coerceBooleanProperty(value);
    if (this.requiredValue !== newVal) {
      this.requiredValue = newVal;
      this.updateValidators();
    }
  }

  @Input()
  disabled: boolean;

  @Input()
  entityType: EntityType;

  @ViewChild('entitySubtypeInput') entitySubtypeInput: ElementRef<HTMLInputElement>;
  @ViewChild('entitySubtypeAutocomplete') entitySubtypeAutocomplete: MatAutocomplete;
  @ViewChild('chipList', {static: true}) chipList: MatChipList;

  entitySubtypeList: Array<string> = [];
  filteredEntitySubtypeList: Observable<Array<string>>;
  entitySubtypes: Observable<Array<string>>;

  private broadcastSubscription: Subscription;

  placeholder: string;
  secondaryPlaceholder: string;
  noSubtypesMathingText: string;
  subtypeListEmptyText: string;

  separatorKeysCodes: number[] = [ENTER, COMMA, SEMICOLON];

  searchText = '';

  private dirty = false;

  private propagateChange = (v: any) => { };

  constructor(private store: Store<AppState>,
              private broadcast: BroadcastService,
              public translate: TranslateService,
              private assetService: AssetService,
              private deviceService: DeviceService,
              private entityViewService: EntityViewService,
              private fb: FormBuilder) {
    this.entitySubtypeListFormGroup = this.fb.group({
      entitySubtypeList: [this.entitySubtypeList, this.required ? [Validators.required] : []],
      entitySubtype: [null]
    });
  }

  updateValidators() {
    this.entitySubtypeListFormGroup.get('entitySubtypeList').setValidators(this.required ? [Validators.required] : []);
    this.entitySubtypeListFormGroup.get('entitySubtypeList').updateValueAndValidity();
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  ngOnInit() {

    switch (this.entityType) {
      case EntityType.ASSET:
        this.placeholder = this.required ? this.translate.instant('asset.enter-asset-type')
          : this.translate.instant('asset.any-asset');
        this.secondaryPlaceholder = '+' + this.translate.instant('asset.asset-type');
        this.noSubtypesMathingText = 'asset.no-asset-types-matching';
        this.subtypeListEmptyText = 'asset.asset-type-list-empty';
        this.broadcastSubscription = this.broadcast.on('assetSaved', () => {
          this.entitySubtypes = null;
        });
        break;
      case EntityType.DEVICE:
        this.placeholder = this.required ? this.translate.instant('device.enter-device-type')
          : this.translate.instant('device.any-device');
        this.secondaryPlaceholder = '+' + this.translate.instant('device.device-type');
        this.noSubtypesMathingText = 'device.no-device-types-matching';
        this.subtypeListEmptyText = 'device.device-type-list-empty';
        this.broadcastSubscription = this.broadcast.on('deviceSaved', () => {
          this.entitySubtypes = null;
        });
        break;
      case EntityType.ENTITY_VIEW:
        this.placeholder = this.required ? this.translate.instant('entity-view.enter-entity-view-type')
          : this.translate.instant('entity-view.any-entity-view');
        this.secondaryPlaceholder = '+' + this.translate.instant('entity-view.entity-view-type');
        this.noSubtypesMathingText = 'entity-view.no-entity-view-types-matching';
        this.subtypeListEmptyText = 'entity-view.entity-view-type-list-empty';
        this.broadcastSubscription = this.broadcast.on('entityViewSaved', () => {
          this.entitySubtypes = null;
        });
        break;
    }

    this.filteredEntitySubtypeList = this.entitySubtypeListFormGroup.get('entitySubtype').valueChanges
      .pipe(
        map(value => value ? value : ''),
        mergeMap(name => this.fetchEntitySubtypes(name) ),
        share()
      );
  }

  ngAfterViewInit(): void {
  }

  ngOnDestroy(): void {
    if (this.broadcastSubscription) {
      this.broadcastSubscription.unsubscribe();
    }
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.entitySubtypeListFormGroup.disable({emitEvent: false});
    } else {
      this.entitySubtypeListFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: Array<string> | null): void {
    this.searchText = '';
    if (value != null && value.length > 0) {
      this.modelValue = [...value];
      this.entitySubtypeList = [...value];
      this.entitySubtypeListFormGroup.get('entitySubtypeList').setValue(this.entitySubtypeList);
    } else {
      this.entitySubtypeList = [];
      this.entitySubtypeListFormGroup.get('entitySubtypeList').setValue(this.entitySubtypeList);
      this.modelValue = null;
    }
    this.dirty = true;
  }

  add(entitySubtype: string): void {
    if (!this.modelValue || this.modelValue.indexOf(entitySubtype) === -1) {
      if (!this.modelValue) {
        this.modelValue = [];
      }
      this.modelValue.push(entitySubtype);
      this.entitySubtypeList.push(entitySubtype);
      this.entitySubtypeListFormGroup.get('entitySubtypeList').setValue(this.entitySubtypeList);
    }
    this.propagateChange(this.modelValue);
    this.clear();
  }

  chipAdd(event: MatChipInputEvent): void {
    const value = event.value;
    if ((value || '').trim()) {
      this.add(value.trim());
    }
    this.clear('');
  }

  remove(entitySubtype: string) {
    const index = this.entitySubtypeList.indexOf(entitySubtype);
    if (index >= 0) {
      this.entitySubtypeList.splice(index, 1);
      this.entitySubtypeListFormGroup.get('entitySubtypeList').setValue(this.entitySubtypeList);
      this.modelValue.splice(index, 1);
      if (!this.modelValue.length) {
        this.modelValue = null;
      }
      this.propagateChange(this.modelValue);
      this.clear();
    }
  }

  selected(event: MatAutocompleteSelectedEvent): void {
    this.add(event.option.viewValue);
    this.clear('');
  }

  displayEntitySubtypeFn(entitySubtype?: string): string | undefined {
    return entitySubtype ? entitySubtype : undefined;
  }

  fetchEntitySubtypes(searchText?: string): Observable<Array<string>> {
    this.searchText = searchText;
    return this.getEntitySubtypes().pipe(
      map(subTypes => {
        let result = subTypes.filter( subType => {
          return searchText ? subType.toUpperCase().startsWith(searchText.toUpperCase()) : true;
        });
        if (!result.length) {
          result = [searchText];
        }
        return result;
      })
    );
  }

  getEntitySubtypes(): Observable<Array<string>> {
    if (!this.entitySubtypes) {
      let subTypesObservable: Observable<Array<EntitySubtype>>;
      switch (this.entityType) {
        case EntityType.ASSET:
          subTypesObservable = this.assetService.getAssetTypes({ignoreLoading: true});
          break;
        case EntityType.DEVICE:
          subTypesObservable = this.deviceService.getDeviceTypes({ignoreLoading: true});
          break;
        case EntityType.ENTITY_VIEW:
          subTypesObservable = this.entityViewService.getEntityViewTypes({ignoreLoading: true});
          break;
      }
      if (subTypesObservable) {
        this.entitySubtypes = subTypesObservable.pipe(
          map(subTypes => subTypes.map(subType => subType.type)),
          publishReplay(1),
          refCount()
        );
      } else {
        return throwError(null);
      }
    }
    return this.entitySubtypes;
  }

  onFocus() {
    if (this.dirty) {
      this.entitySubtypeListFormGroup.get('entitySubtype').updateValueAndValidity({onlySelf: true, emitEvent: true});
      this.dirty = false;
    }
  }

  clear(value: string = '') {
    this.entitySubtypeInput.nativeElement.value = value;
    this.entitySubtypeListFormGroup.get('entitySubtype').patchValue(value, {emitEvent: true});
    setTimeout(() => {
      this.entitySubtypeInput.nativeElement.blur();
      this.entitySubtypeInput.nativeElement.focus();
    }, 0);
  }

}