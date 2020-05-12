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

import { Component } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { EntityTableHeaderComponent } from '../../components/entity/entity-table-header.component';
import {Device} from '@app/shared/models/device.models';
import { EntityType, entityTypeTranslations } from '@shared/models/entity-type.models';
import { BaseData, HasId } from '@shared/models/base-data';
import { EntityComponent } from '@home/components/entity/entity.component';
import { PageLink } from '@shared/models/page/page-link';
import { ShortEntityView } from '@shared/models/entity-group.models';
import { GroupEntityTableConfig } from '@home/models/group/group-entities-table-config.models';
import { UtilsService } from '@core/services/utils.service';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'tb-group-entity-table-header',
  templateUrl: './group-entity-table-header.component.html',
  styleUrls: ['./group-entity-table-header.component.scss']
})
export class GroupEntityTableHeaderComponent<T extends BaseData<HasId>>
  extends EntityTableHeaderComponent<T, PageLink, ShortEntityView, GroupEntityTableConfig<T>> {

  tableTitle: string;
  entitiesTitle: string;

  constructor(protected store: Store<AppState>,
              private utils: UtilsService,
              private translate: TranslateService) {
    super(store);
  }

  protected setEntitiesTableConfig(entitiesTableConfig: GroupEntityTableConfig<T>) {
    super.setEntitiesTableConfig(entitiesTableConfig);
    const settings = entitiesTableConfig.settings;
    const entityGroup = entitiesTableConfig.entityGroup;
    const entityType = entityGroup.type;
    if (settings.groupTableTitle && settings.groupTableTitle.length) {
      this.tableTitle = settings.groupTableTitle;
      this.entitiesTitle = '';
    } else {
      this.tableTitle = this.utils.customTranslation(entityGroup.name, entityGroup.name);
      this.entitiesTitle = `: ${this.translate.instant(entityTypeTranslations.get(entityType).typePlural)}`;
    }
  }

  toggleGroupDetails() {
    this.entitiesTableConfig.onToggleEntityGroupDetails();
  }

}