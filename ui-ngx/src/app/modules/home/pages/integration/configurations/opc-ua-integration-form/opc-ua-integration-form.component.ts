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

import { Component, OnInit, Input, ChangeDetectionStrategy, ChangeDetectorRef, AfterViewInit } from '@angular/core';
import { FormGroup, FormArray, FormBuilder, Validators } from '@angular/forms';
import { opcUaMappingType, extensionKeystoreType, opcSecurityTypes, identityType } from '../../integration-forms-templates';
import { disableFields, enableFields } from '../../integration-utils';

@Component({
  selector: 'tb-opc-ua-integration-form',
  templateUrl: './opc-ua-integration-form.component.html',
  styleUrls: ['./opc-ua-integration-form.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OpcUaIntegrationFormComponent implements OnInit {


  @Input() form: FormGroup;

  identityType = identityType;
  opcUaMappingType = opcUaMappingType;
  extensionKeystoreType = extensionKeystoreType;
  opcSecurityTypes = opcSecurityTypes;
  showIdentityForm: boolean;

  constructor(private fb: FormBuilder, private cd: ChangeDetectorRef) { }

  ngOnInit(): void {
    if (this.form) {
      this.form.get('mapping').setValidators(Validators.required)
      this.form.get('keystore').get('location').setValidators(Validators.required);
      this.form.get('keystore').get('fileContent').setValidators(Validators.required);
      this.identityTypeChanged({ value: this.form.get('identity').get('type').value });
    }
  }

  identityTypeChanged($event?) {
    disableFields(this.form.get('identity') as FormGroup, ['username', 'password']);
    if ($event?.value === 'username') {
      this.showIdentityForm = true;
      enableFields(this.form.get('identity') as FormGroup, ['username', 'password']);
    }
    else this.showIdentityForm = false;
  }

  securityChanged() {
    if (this.form.get('security').value === 'None')
      this.form.get('keystore').disable();
    else
      this.form.get('keystore').enable();
  }

  addMap() {
    (this.form.get('mapping') as FormArray).push(
      this.fb.group({
        deviceNodePattern: ['Channel1\\.Device\\d+$'],
        mappingType: ['FQN', Validators.required],
        subscriptionTags: this.fb.array([], [Validators.required]),
        namespace: [Validators.min(0)]
      })
    );
  }
}
