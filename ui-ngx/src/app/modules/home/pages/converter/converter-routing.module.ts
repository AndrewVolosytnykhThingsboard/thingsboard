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

import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { EntitiesTableComponent } from '../../components/entity/entities-table.component';
import { Authority } from '@shared/models/authority.enum';
import { ConvertersTableConfigResolver } from './converters-table-config.resolver';

const routes: Routes = [
  {
    path: 'converters',
    component: EntitiesTableComponent,
    data: {
      auth: [Authority.TENANT_ADMIN],
      title: 'converter.converters',
      breadcrumb: {
        label: 'converter.converters',
        icon: 'transform'
      }
    },
    resolve: {
      entitiesTableConfig: ConvertersTableConfigResolver
    }
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
  providers: [
    ConvertersTableConfigResolver
  ]
})
export class ConverterRoutingModule { }
