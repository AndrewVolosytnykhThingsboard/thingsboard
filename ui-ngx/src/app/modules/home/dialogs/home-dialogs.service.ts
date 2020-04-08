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

import { Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { EntityType } from '@shared/models/entity-type.models';
import { Observable, of } from 'rxjs';
import {
  ImportDialogCsvComponent,
  ImportDialogCsvData
} from '@home/components/import-export/import-dialog-csv.component';
import { CustomerId } from '@shared/models/id/customer-id';
import { DialogService } from '@core/services/dialog.service';
import { EntityGroupService } from '@core/http/entity-group.service';
import { EntityGroupInfo } from '@shared/models/entity-group.models';
import { TranslateService } from '@ngx-translate/core';
import { map, mergeMap } from 'rxjs/operators';

@Injectable()
export class HomeDialogsService {
  constructor(
    private dialog: MatDialog,
    private translate: TranslateService,
    private dialogService: DialogService,
    private entityGroupService: EntityGroupService
  ) {
  }

  public importEntities(customerId: CustomerId, entityType: EntityType, entityGroupId: string): Observable<boolean> {
    switch (entityType) {
      case EntityType.DEVICE:
        return this.openImportDialogCSV(customerId, entityType, entityGroupId,'device.import', 'device.device-file');
      case EntityType.ASSET:
        return this.openImportDialogCSV(customerId, entityType, entityGroupId,'asset.import', 'asset.asset-file');
    }
  }

  public makeEntityGroupPublic($event: Event, entityGroup: EntityGroupInfo): Observable<boolean> {
    const title = this.translate.instant('entity-group.make-public-entity-group-title',
      {entityGroupName: entityGroup.name});
    const content = this.translate.instant('entity-group.make-public-entity-group-text');
    return this.dialogService.confirm(
      title,
      content,
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).pipe(
      mergeMap((res) => {
        if (res) {
          return this.entityGroupService.makeEntityGroupPublic(entityGroup.id.id)
            .pipe(map(() => res));
        } else {
          return of(res);
        }
      }
    ));
  }

  public makeEntityGroupPrivate($event: Event, entityGroup: EntityGroupInfo): Observable<boolean> {
    const title = this.translate.instant('entity-group.make-private-entity-group-title',
      {entityGroupName: entityGroup.name});
    const content = this.translate.instant('entity-group.make-private-entity-group-text');
    return this.dialogService.confirm(
      title,
      content,
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).pipe(
      mergeMap((res) => {
          if (res) {
            return this.entityGroupService.makeEntityGroupPrivate(entityGroup.id.id)
              .pipe(map(() => res));
          } else {
            return of(res);
          }
        }
      ));
  }

  private openImportDialogCSV(customerId: CustomerId, entityType: EntityType,
                              entityGroupId: string, importTitle: string, importFileLabel: string): Observable<boolean> {
    return this.dialog.open<ImportDialogCsvComponent, ImportDialogCsvData,
      any>(ImportDialogCsvComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        entityType,
        importTitle,
        importFileLabel,
        customerId,
        entityGroupId
      }
    }).afterClosed();
  }
}
