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

import { Component, OnInit, Input, ChangeDetectionStrategy } from '@angular/core';
import { FormGroup, Validators } from '@angular/forms';
import { handlerConfigurationTypes, tcpBinaryByteOrder, tcpTextMessageSeparator } from '../../integration-forms-templates';


@Component({
  selector: 'tb-tcp-integration-form',
  templateUrl: './tcp-integration-form.component.html',
  styleUrls: ['./tcp-integration-form.component.scss'],
  changeDetection: ChangeDetectionStrategy.Default
})
export class TcpIntegrationFormComponent implements OnInit {


  @Input() form: FormGroup;

  handlerConfigurationTypes = handlerConfigurationTypes;
  handlerTypes = handlerConfigurationTypes;
  tcpBinaryByteOrder = tcpBinaryByteOrder;
  tcpTextMessageSeparator = tcpTextMessageSeparator;

  defaultHandlerConfigurations = {
    [handlerConfigurationTypes.binary.value]: {
      handlerType: handlerConfigurationTypes.binary.value,
      byteOrder: tcpBinaryByteOrder.littleEndian.value,
      maxFrameLength: 128,
      lengthFieldOffset: 0,
      lengthFieldLength: 2,
      lengthAdjustment: 0,
      initialBytesToStrip: 0,
      failFast: false
    }, [handlerConfigurationTypes.text.value]: {
      handlerType: handlerConfigurationTypes.text.value,
      maxFrameLength: 128,
      stripDelimiter: true,
      messageSeparator: tcpTextMessageSeparator.systemLineSeparator.value
    },
    [handlerConfigurationTypes.json.value]: {
      handlerType: handlerConfigurationTypes.json.value
    }
  }

  constructor() { }

  ngOnInit(): void {
    delete this.handlerTypes.hex;
  }

  handlerConfigurationTypeChanged(type) {
    const handlerConf = this.defaultHandlerConfigurations[type.value];
    const controls = (this.form.get('handlerConfiguration') as FormGroup).controls;
    // tslint:disable-next-line: forin
    for (const property in controls) {
      const control = controls[property];
      if (control) {
        if (handlerConf[property] !== undefined)
          control.setValidators(Validators.required)
        else
          control.setValidators([]);
      }
    }
    this.form.get('handlerConfiguration').patchValue(handlerConf);
  };

}
