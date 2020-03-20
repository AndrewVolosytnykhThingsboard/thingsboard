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
  ElementRef,
  forwardRef,
  Input,
  OnInit,
  ViewChild,
  AfterViewInit,
  OnDestroy,
  Output, EventEmitter
} from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { DataKey, DatasourceType } from '@shared/models/widget.models';
import {
  ControlValueAccessor,
  FormBuilder,
  FormControl,
  FormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  Validator,
  Validators
} from '@angular/forms';
import { UtilsService } from '@core/services/utils.service';
import { TranslateService } from '@ngx-translate/core';
import { MatDialog } from '@angular/material/dialog';
import { EntityService } from '@core/http/entity.service';
import { DataKeysCallbacks } from '@home/components/widget/data-keys.component.models';
import { DataKeyType } from '@shared/models/telemetry/telemetry.models';
import { Observable, of, Subscription } from 'rxjs';
import { map, mergeMap, tap } from 'rxjs/operators';
import { alarmFields } from '@shared/models/alarm.models';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { DialogService } from '@core/services/dialog.service';
import { FlowDirective } from '@flowjs/ngx-flow';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';

@Component({
  selector: 'tb-image-input',
  templateUrl: './image-input.component.html',
  styleUrls: ['./image-input.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => ImageInputComponent),
      multi: true
    }
  ]
})
export class ImageInputComponent extends PageComponent implements AfterViewInit, OnDestroy, ControlValueAccessor {

  @Input()
  label: string;

  @Input()
  accept = 'image/*';

  @Input()
  noImageText = 'dashboard.no-image';

  @Input()
  inputId = 'select';

  @Input()
  dropLabel = this.translate.instant('dashboard.drop-image');

  @Input()
  maxImageSize = 0;

  @Input()
  allowedImageMimeTypes: string[];

  private requiredValue: boolean;
  get required(): boolean {
    return this.requiredValue;
  }
  @Input()
  set required(value: boolean) {
    const newVal = coerceBooleanProperty(value);
    if (this.requiredValue !== newVal) {
      this.requiredValue = newVal;
    }
  }

  @Input()
  disabled: boolean;

  @Output()
  imageTypeChanged = new EventEmitter<string>();

  @Output()
  imageSizeOverflow = new EventEmitter();

  @Output()
  imageTypeError = new EventEmitter();

  @Output()
  imageCleared = new EventEmitter();

  imageType: string;
  imageUrl: string;
  safeImageUrl: SafeUrl;

  @ViewChild('flow', {static: true})
  flow: FlowDirective;

  autoUploadSubscription: Subscription;

  private propagateChange = null;

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private sanitizer: DomSanitizer) {
    super(store);
  }

  ngAfterViewInit() {
    this.autoUploadSubscription = this.flow.events$.subscribe(event => {
      if (event.type === 'fileAdded') {
        const file = (event.event[0] as flowjs.FlowFile).file;
        const reader = new FileReader();
        reader.onload = (loadEvent) => {
          let allowedImage = true;
          let type;
          let dataUrl;
          if (typeof reader.result === 'string' && reader.result.startsWith('data:image/')) {
            dataUrl = reader.result;
            type = this.extractType(dataUrl);
            if (this.allowedImageMimeTypes && this.allowedImageMimeTypes.length) {
              if (!type || this.allowedImageMimeTypes.indexOf(type) === -1) {
                allowedImage = false;
              }
            }
          } else {
            allowedImage = false;
          }
          if (allowedImage) {
            this.imageType = type;
            this.imageUrl = dataUrl;
            this.safeImageUrl = this.sanitizer.bypassSecurityTrustUrl(dataUrl);
            this.updateModel();
          } else {
            this.imageTypeError.emit();
          }
        };
        if (this.maxImageSize > 0 && file.size > this.maxImageSize) {
          this.imageSizeOverflow.emit();
        } else {
          reader.readAsDataURL(file);
        }
      }
    });
  }

  private extractType(dataUrl: string): string {
    let type;
    if (dataUrl) {
      let res: string | string[] = dataUrl.split(';');
      if (res && res.length) {
        res = res[0];
        res = res.split(':');
        if (res && res.length > 1) {
          type = res[1];
        }
      }
    }
    return type;
  }

  ngOnDestroy() {
    this.autoUploadSubscription.unsubscribe();
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  writeValue(value: string): void {
    this.imageUrl = value;
    this.imageType = this.extractType(value);
    if (this.imageUrl) {
      this.safeImageUrl = this.sanitizer.bypassSecurityTrustUrl(this.imageUrl);
    } else {
      this.safeImageUrl = null;
    }
  }

  private updateModel() {
    this.propagateChange(this.imageUrl);
    this.imageTypeChanged.emit(this.imageType);
  }

  clearImage() {
    this.imageType = null;
    this.imageUrl = null;
    this.safeImageUrl = null;
    this.updateModel();
    this.imageCleared.emit();
  }
}
