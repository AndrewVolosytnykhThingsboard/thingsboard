///
/// Copyright © 2016-2021 The Thingsboard Authors
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

import { Component, forwardRef, Input, OnInit } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { GroupPermission } from '@shared/models/group-permission.models';

@Component({
  selector: 'tb-registration-permissions',
  templateUrl: './registration-permissions.component.html',
  styleUrls: ['./registration-permissions.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => RegistrationPermissionsComponent),
      multi: true
    }
  ]
})
export class RegistrationPermissionsComponent extends PageComponent implements ControlValueAccessor, OnInit {

  @Input() disabled: boolean;

  registrationPermissions: Array<GroupPermission>;

  private propagateChange = null;

  constructor(protected store: Store<AppState>) {
    super(store);
  }

  ngOnInit(): void {
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState?(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  writeValue(permissions: Array<GroupPermission>): void {
    this.registrationPermissions = permissions || [];
  }

  registrationPermissionsChanged() {
    this.propagateChange(this.registrationPermissions);
  }

}
