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
import { EntityComponent } from '../../components/entity/entity.component';
import { FormBuilder, FormGroup, Validators, FormControl } from '@angular/forms';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { TranslateService } from '@ngx-translate/core';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { Integration, IntegrationType, integrationTypeInfoMap, IntegrationTypeInfo } from '@shared/models/integration.models';
import { guid, isDefined, isUndefined } from '@core/utils';
import { ConverterType } from '@shared/models/converter.models';
import { ClipboardService } from 'ngx-clipboard';
import { templates } from './integartionFormTemapltes';

@Component({
  selector: 'tb-integration',
  templateUrl: './integration.component.html',
  styleUrls: ['./integration.component.scss']
})
export class IntegrationComponent extends EntityComponent<Integration> {

  integrationType: IntegrationType;

  converterType = ConverterType;

  integrationTypes = IntegrationType;

  integrationTypeKeys = Object.keys(IntegrationType)

  integrationTypeInfos = integrationTypeInfoMap;

  httpForm: FormGroup;

  integrationForm: FormGroup;

  sqsConfiguration: FormGroup;

  downlinkTopicPattern: FormControl;

  integrationInfo: IntegrationTypeInfo;

  constructor(protected store: Store<AppState>,
    protected translate: TranslateService,
    @Inject('entity') protected entityValue: Integration,
    @Inject('entitiesTableConfig') protected entitiesTableConfig: EntityTableConfig<Integration>,
    protected fb: FormBuilder) {
    super(store, fb, entityValue, entitiesTableConfig);
  }

  ngOnInit() {
    super.ngOnInit();
  }

  hideDelete() {
    if (this.entitiesTableConfig) {
      return !this.entitiesTableConfig.deleteEnabled(this.entity);
    } else {
      return false;
    }
  }

  buildForm(entity: Integration): FormGroup {
    const form = this.fb.group(
      {
        name: [entity ? entity.name : '', [Validators.required]],
        type: [entity ? entity.type : null, [Validators.required]],
        enabled: [entity && isDefined(entity.enabled) ? entity.enabled : true],
        debugMode: [entity ? entity.debugMode : null],
        defaultConverterId: [entity ? entity.defaultConverterId : null, [Validators.required]],
        downlinkConverterId: [entity ? entity.downlinkConverterId : null, []],
        remote: [entity ? entity.remote : null],
        routingKey: this.fb.control({ value: entity ? entity.routingKey : null, disabled: true }),
        secret: this.fb.control({ value: entity ? entity.secret : null, disabled: true }),
        configuration: [entity ? entity.configuration : {}, []],
        metadata: [entity && entity.configuration ? entity.configuration.metadata : {}],
        additionalInfo: this.fb.group(
          {
            description: [entity && entity.additionalInfo ? entity.additionalInfo.description : ''],
          }
        )
      }
    );
    this.downlinkTopicPattern = this.fb.control(['${topic}', Validators.required]);
    form.get('type').valueChanges.subscribe((type: IntegrationType) => {
      this.integrationType = type;
      if (type === IntegrationType.AWS_SQS)
        this.sqsConfiguration = this.getIntegrationForm(templates[type]);
      else
        this.integrationForm = this.getIntegrationForm(templates[type]);
      this.integrationInfo = this.integrationTypeInfos.get(type);
      this.integrationTypeChanged(form);
    });
    this.httpForm = this.fb.group(templates.HTTP);
    this.checkIsNewIntegration(entity, form);
    return form;
  }

  updateFormState() {
    super.updateFormState();
    if (this.isEditValue && this.entityForm) {
      this.checkIsRemote(this.entityForm);
      this.entityForm.get('routingKey').disable({ emitEvent: false });
      this.entityForm.get('secret').disable({ emitEvent: false });
    }
  }

  private checkIsNewIntegration(entity: Integration, form: FormGroup) {
    if (entity && !entity.id) {
      form.get('routingKey').patchValue(guid(), { emitEvent: false });
      form.get('secret').patchValue(this.generateSecret(20), { emitEvent: false });
    }
  }

  private integrationTypeChanged(form: FormGroup) {
    form.get('configuration').patchValue({}, { emitEvent: false });
    form.get('metadata').patchValue({}, { emitEvent: false });
    this.checkIsRemote(form);
  }

  private checkIsRemote(form: FormGroup) {
    const integrationType: IntegrationType = form.get('type').value;
    if (integrationType && this.integrationTypeInfos.get(integrationType).remote) {
      form.get('remote').patchValue(true, { emitEvent: false });
      form.get('remote').disable({ emitEvent: false });
    } else if (this.isEditValue) {
      form.get('remote').enable({ emitEvent: false });
    }
  }

  updateForm(entity: Integration) {
    this.entityForm.patchValue({ name: entity.name });
    this.entityForm.patchValue({ type: entity.type }, { emitEvent: false });
    this.entityForm.patchValue({ enabled: isDefined(entity.enabled) ? entity.enabled : true });
    this.entityForm.patchValue({ debugMode: entity.debugMode });
    this.entityForm.patchValue({ defaultConverterId: entity.defaultConverterId });
    this.entityForm.patchValue({ downlinkConverterId: entity.downlinkConverterId });
    this.entityForm.patchValue({ remote: entity.remote });
    this.entityForm.patchValue({ routingKey: entity.routingKey });
    this.entityForm.patchValue({ secret: entity.secret });
    this.entityForm.patchValue({ configuration: entity.configuration });
    this.entityForm.patchValue({ metadata: entity.configuration ? entity.configuration.metadata : {} });
    this.entityForm.patchValue({ additionalInfo: { description: entity.additionalInfo ? entity.additionalInfo.description : '' } });
    this.checkIsNewIntegration(entity, this.entityForm);
  }

  getIntegrationForm(form: object): FormGroup {
    let template = form;
    for (const key in template) {
      if (template[key] && typeof (template[key]) === 'object' && !Array.isArray(template[key])) {
        template[key] = this.getIntegrationForm(template[key]);
      }
    }
    return this.fb.group(
      template
    )
  }

  log(a) {
    console.log(a);
  }

  prepareFormValue(formValue: any): any {
    if (!formValue.configuration) {
      formValue.configuration = {};
    }
    formValue.configuration.metadata = formValue.metadata || {};
    formValue.configuration = formValue.configuration || {};
    if (this.integrationInfo?.http) {
      formValue.configuration = { ...formValue.configuration, ...this.httpForm.value };
    }
    if (this.integrationType === IntegrationType.AWS_SQS) {
      formValue.sqsConfiguration = this.integrationForm.value;
    }
    else {
      formValue.clientConfiguration = this.integrationForm.value;
    }
    delete formValue.metadata;
    return formValue;
  }

  private generateSecret(length?: number): string {
    if (isUndefined(length) || length == null) {
      length = 1;
    }
    const l = length > 10 ? 10 : length;
    const str = Math.random().toString(36).substr(2, l);
    if (str.length >= length) {
      return str;
    }
    return str.concat(this.generateSecret(length - str.length));
  }

  onIntegrationIdCopied($event) {
    this.store.dispatch(new ActionNotificationShow(
      {
        message: this.translate.instant('integration.idCopiedMessage'),
        type: 'success',
        duration: 750,
        verticalPosition: 'bottom',
        horizontalPosition: 'right'
      }));
  }

  onIntegrationInfoCopied(type: string) {
    const message = type === 'key' ? 'integration.integration-key-copied-message'
      : 'integration.integration-secret-copied-message';
    this.store.dispatch(new ActionNotificationShow(
      {
        message: this.translate.instant(message),
        type: 'success',
        duration: 750,
        verticalPosition: 'bottom',
        horizontalPosition: 'right'
      }));
  }
}
