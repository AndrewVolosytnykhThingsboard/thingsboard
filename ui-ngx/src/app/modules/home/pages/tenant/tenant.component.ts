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

import { Component, Inject } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Tenant } from '@app/shared/models/tenant.model';
import { ActionNotificationShow } from '@app/core/notification/notification.actions';
import { TranslateService } from '@ngx-translate/core';
import { ContactBasedComponent } from '../../components/entity/contact-based.component';
import { isDefined } from '@core/utils';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';

@Component({
  selector: 'tb-tenant',
  templateUrl: './tenant.component.html',
  styleUrls: ['./tenant.component.scss']
})
export class TenantComponent extends ContactBasedComponent<Tenant> {

  constructor(protected store: Store<AppState>,
              protected translate: TranslateService,
              @Inject('entity') protected entityValue: Tenant,
              @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<Tenant>,
              protected fb: FormBuilder) {
    super(store, fb, entityValue, entitiesTableConfigValue);
  }

  hideDelete() {
    if (this.entitiesTableConfig) {
      return !this.entitiesTableConfig.deleteEnabled(this.entity);
    } else {
      return false;
    }
  }

  buildEntityForm(entity: Tenant): FormGroup {
    return this.fb.group(
      {
        title: [entity ? entity.title : '', [Validators.required]],
        isolatedTbCore: [entity ? entity.isolatedTbCore : false, []],
        isolatedTbRuleEngine: [entity ? entity.isolatedTbRuleEngine : false, []],
        additionalInfo: this.fb.group(
          {
            description: [entity && entity.additionalInfo ? entity.additionalInfo.description : ''],
            allowWhiteLabeling: [entity && entity.additionalInfo
                     && isDefined(entity.additionalInfo.allowWhiteLabeling) ? entity.additionalInfo.allowWhiteLabeling : true],
            allowCustomerWhiteLabeling: [entity && entity.additionalInfo
                    && isDefined(entity.additionalInfo.allowCustomerWhiteLabeling) ?
                        entity.additionalInfo.allowCustomerWhiteLabeling : true]
          }
        )
      }
    );
  }

  updateEntityForm(entity: Tenant) {
    this.entityForm.patchValue({title: entity.title});
    this.entityForm.patchValue({isolatedTbCore: entity.isolatedTbCore});
    this.entityForm.patchValue({isolatedTbRuleEngine: entity.isolatedTbRuleEngine});
    this.entityForm.patchValue({additionalInfo: {
      description: entity.additionalInfo ? entity.additionalInfo.description : '',
      allowWhiteLabeling: entity.additionalInfo
        && isDefined(entity.additionalInfo.allowWhiteLabeling) ? entity.additionalInfo.allowWhiteLabeling : true,
      allowCustomerWhiteLabeling: entity.additionalInfo
        && isDefined(entity.additionalInfo.allowCustomerWhiteLabeling) ?
          entity.additionalInfo.allowCustomerWhiteLabeling : true
    }});
  }

  updateFormState() {
    if (this.entityForm) {
      if (this.isEditValue) {
        this.entityForm.enable({emitEvent: false});
        if (!this.isAdd) {
          this.entityForm.get('isolatedTbCore').disable({emitEvent: false});
          this.entityForm.get('isolatedTbRuleEngine').disable({emitEvent: false});
        }
      } else {
        this.entityForm.disable({emitEvent: false});
      }
    }
  }

  onTenantIdCopied(event) {
    this.store.dispatch(new ActionNotificationShow(
      {
        message: this.translate.instant('tenant.idCopiedMessage'),
        type: 'success',
        duration: 750,
        verticalPosition: 'bottom',
        horizontalPosition: 'right'
      }));
  }

}