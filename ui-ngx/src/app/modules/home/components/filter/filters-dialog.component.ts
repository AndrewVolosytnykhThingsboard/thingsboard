///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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

import { Component, Inject, OnInit, SkipSelf } from '@angular/core';
import { ErrorStateMatcher } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import {
  AbstractControl,
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  FormGroupDirective,
  NgForm,
  Validators
} from '@angular/forms';
import { Router } from '@angular/router';
import { DialogComponent } from '@app/shared/components/dialog.component';
import { DatasourceType, Widget } from '@shared/models/widget.models';
import { UtilsService } from '@core/services/utils.service';
import { TranslateService } from '@ngx-translate/core';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { DialogService } from '@core/services/dialog.service';
import { deepClone, isUndefined } from '@core/utils';
import { Filter, Filters, KeyFilterInfo } from '@shared/models/query/query.models';
import { FilterDialogComponent, FilterDialogData } from '@home/components/filter/filter-dialog.component';

export interface FiltersDialogData {
  filters: Filters;
  widgets: Array<Widget>;
  isSingleFilter?: boolean;
  isSingleWidget?: boolean;
  disableAdd?: boolean;
  singleFilter?: Filter;
  customTitle?: string;
}

@Component({
  selector: 'tb-filters-dialog',
  templateUrl: './filters-dialog.component.html',
  providers: [{provide: ErrorStateMatcher, useExisting: FiltersDialogComponent}],
  styleUrls: ['./filters-dialog.component.scss']
})
export class FiltersDialogComponent extends DialogComponent<FiltersDialogComponent, Filters>
  implements OnInit, ErrorStateMatcher {

  title: string;
  disableAdd: boolean;

  filterToWidgetsMap: {[filterId: string]: Array<string>} = {};

  filtersFormGroup: FormGroup;

  submitted = false;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: FiltersDialogData,
              @SkipSelf() private errorStateMatcher: ErrorStateMatcher,
              public dialogRef: MatDialogRef<FiltersDialogComponent, Filters>,
              private fb: FormBuilder,
              private utils: UtilsService,
              private translate: TranslateService,
              private dialogs: DialogService,
              private dialog: MatDialog) {
    super(store, router, dialogRef);
    this.title = data.customTitle ? data.customTitle : 'filter.filters';
    this.disableAdd = this.data.disableAdd;

    if (data.widgets) {
      let widgetsTitleList: Array<string>;
      if (this.data.isSingleWidget && this.data.widgets.length === 1) {
        const widget = this.data.widgets[0];
        widgetsTitleList = [widget.config.title];
        for (const filterId of Object.keys(this.data.filters)) {
          this.filterToWidgetsMap[filterId] = widgetsTitleList;
        }
      } else {
        this.data.widgets.forEach((widget) => {
          const datasources = this.utils.validateDatasources(widget.config.datasources);
          datasources.forEach((datasource) => {
            if (datasource.type === DatasourceType.entity && datasource.filterId) {
              widgetsTitleList = this.filterToWidgetsMap[datasource.filterId];
              if (!widgetsTitleList) {
                widgetsTitleList = [];
                this.filterToWidgetsMap[datasource.filterId] = widgetsTitleList;
              }
              widgetsTitleList.push(widget.config.title);
            }
          });
        });
      }
    }
    const filterControls: Array<AbstractControl> = [];
    for (const filterId of Object.keys(this.data.filters)) {
      const filter = this.data.filters[filterId];
      if (isUndefined(filter.editable)) {
        filter.editable = true;
      }
      filterControls.push(this.createFilterFormControl(filterId, filter));
    }

    this.filtersFormGroup = this.fb.group({
      filters: this.fb.array(filterControls)
    });
  }

  private createFilterFormControl(filterId: string, filter: Filter): AbstractControl {
    const filterFormControl = this.fb.group({
      id: [filterId],
      filter: [filter ? filter.filter : null, [Validators.required]],
      keyFilters: [filter ? filter.keyFilters : [], [Validators.required]],
      editable: [filter ? filter.editable : true]
    });
    return filterFormControl;
  }


  filtersFormArray(): FormArray {
    return this.filtersFormGroup.get('filters') as FormArray;
  }

  ngOnInit(): void {
  }

  isErrorState(control: FormControl | null, form: FormGroupDirective | NgForm | null): boolean {
    const originalErrorState = this.errorStateMatcher.isErrorState(control, form);
    const customErrorState = !!(control && control.invalid && this.submitted);
    return originalErrorState || customErrorState;
  }

  removeFilter(index: number) {
    const filter = (this.filtersFormGroup.get('filters').value as any[])[index];
    const widgetsTitleList = this.filterToWidgetsMap[filter.id];
    if (widgetsTitleList) {
      let widgetsListHtml = '';
      for (const widgetTitle of widgetsTitleList) {
        widgetsListHtml += '<br/>\'' + widgetTitle + '\'';
      }
      const message = this.translate.instant('filter.unable-delete-filter-text',
        {filter: filter.filter, widgetsList: widgetsListHtml});
      this.dialogs.alert(this.translate.instant('filter.unable-delete-filter-title'),
        message, this.translate.instant('action.close'), true);
    } else {
      (this.filtersFormGroup.get('filters') as FormArray).removeAt(index);
      this.filtersFormGroup.markAsDirty();
    }
  }

  public addFilter() {
    this.openFilterDialog(-1);
  }

  public editFilter(index: number) {
    this.openFilterDialog(index);
  }

  private openFilterDialog(index: number) {
    const isAdd = index === -1;
    let filter;
    const filtersArray = this.filtersFormGroup.get('filters').value as any[];
    if (!isAdd) {
      filter = filtersArray[index];
    }
    this.dialog.open<FilterDialogComponent, FilterDialogData,
      Filter>(FilterDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        isAdd,
        filters: filtersArray,
        filter: isAdd ? null : deepClone(filter)
      }
    }).afterClosed().subscribe((result) => {
      if (result) {
        if (isAdd) {
          (this.filtersFormGroup.get('filters') as FormArray)
            .push(this.createFilterFormControl(result.id, result));
        } else {
          const filterFormControl = (this.filtersFormGroup.get('filters') as FormArray).at(index);
          filterFormControl.get('filter').patchValue(result.filter);
          filterFormControl.get('editable').patchValue(result.editable);
          filterFormControl.get('keyFilters').patchValue(result.keyFilters);
        }
        this.filtersFormGroup.markAsDirty();
      }
    });
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  save(): void {
    this.submitted = true;
    const filters: Filters = {};
    const uniqueFilterList: {[filter: string]: string} = {};

    let valid = true;
    let message: string;

    const filtersArray = this.filtersFormGroup.get('filters').value as any[];
    for (const filterValue of filtersArray) {
      const filterId: string = filterValue.id;
      const filter: string = filterValue.filter;
      const keyFilters: Array<KeyFilterInfo> = filterValue.keyFilters;
      const editable: boolean = filterValue.editable;
      if (uniqueFilterList[filter]) {
        valid = false;
        message = this.translate.instant('filter.duplicate-filter-error', {filter});
        break;
      } else if (!keyFilters || !keyFilters.length) {
        valid = false;
        message = this.translate.instant('filter.missing-key-filters-error', {filter});
        break;
      } else {
        uniqueFilterList[filter] = filter;
        filters[filterId] = {id: filterId, filter, keyFilters, editable};
      }
    }
    if (valid) {
      this.dialogRef.close(filters);
    } else {
      this.store.dispatch(new ActionNotificationShow(
        {
          message,
          type: 'error'
        }));
    }
  }
}
