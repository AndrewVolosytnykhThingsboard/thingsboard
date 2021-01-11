///
/// Copyright © 2016-2021 ThingsBoard, Inc.
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

import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SharedModule } from '@app/shared/shared.module';
import { AlarmDetailsDialogComponent } from '@home/components/alarm/alarm-details-dialog.component';
import { SchedulerEventModule } from '@home/components/scheduler/scheduler-event.module';
import { BlobEntitiesComponent } from '@home/components/blob-entity/blob-entities.component';

@NgModule({
  declarations:
    [
      AlarmDetailsDialogComponent,
      BlobEntitiesComponent
    ],
  imports: [
    CommonModule,
    SharedModule,
    SchedulerEventModule
  ],
  exports: [
    AlarmDetailsDialogComponent,
    BlobEntitiesComponent,
    SchedulerEventModule
  ]
})
export class SharedHomeComponentsModule { }
