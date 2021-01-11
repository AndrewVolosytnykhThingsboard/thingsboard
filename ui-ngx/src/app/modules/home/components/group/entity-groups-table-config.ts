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

import {
  checkBoxCell,
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { EntityGroupInfo, EntityGroupParams, entityGroupsTitle } from '@shared/models/entity-group.models';
import { EntityGroupService } from '@core/http/entity-group.service';
import { CustomerService } from '@core/http/customer.service';
import { UserPermissionsService } from '@core/http/user-permissions.service';
import { BroadcastService } from '@core/services/broadcast.service';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { UtilsService } from '@core/services/utils.service';
import { ActivatedRoute, Router } from '@angular/router';
import { HomeDialogsService } from '@home/dialogs/home-dialogs.service';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { isDefinedAndNotNull } from '@core/utils';
import { Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { Operation, publicGroupTypes, Resource, sharableGroupTypes } from '@shared/models/security.models';
import { AddEntityDialogData, EntityAction } from '@home/models/entity/entity-component.models';
import { EntityGroupComponent } from '@home/components/group/entity-group.component';
import { EntityGroupTabsComponent } from '@home/components/group/entity-group-tabs.component';
import { MatDialog } from '@angular/material/dialog';
import {
  EntityGroupWizardDialogComponent,
  EntityGroupWizardDialogResult
} from '@home/components/wizard/entity-group-wizard-dialog.component';

export class EntityGroupsTableConfig extends EntityTableConfig<EntityGroupInfo> {

  customerId: string;
  groupType: EntityType;

  constructor(private entityGroupService: EntityGroupService,
              private customerService: CustomerService,
              private userPermissionsService: UserPermissionsService,
              private broadcast: BroadcastService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private utils: UtilsService,
              private route: ActivatedRoute,
              private router: Router,
              private dialog: MatDialog,
              private homeDialogs: HomeDialogsService,
              private params: EntityGroupParams) {
    super();

    this.customerId = params.customerId;
    if (this.customerId && params.childGroupType) {
      this.groupType = params.childGroupType;
    } else {
      this.groupType = params.groupType;
    }

    this.entityType = EntityType.ENTITY_GROUP;
    this.entityComponent = EntityGroupComponent;
    this.entityTabsComponent = EntityGroupTabsComponent;
    this.entityTranslations = entityTypeTranslations.get(EntityType.ENTITY_GROUP);
    this.entityResources = entityTypeResources.get(EntityType.ENTITY_GROUP);

    this.hideDetailsTabsOnEdit = false;

    this.entityTitle = (entityGroup) => entityGroup ?
      this.utils.customTranslation(entityGroup.name, entityGroup.name) : '';

    this.columns.push(
      new DateEntityTableColumn<EntityGroupInfo>('createdTime', 'common.created-time', this.datePipe, '150px'),
      new EntityTableColumn<EntityGroupInfo>('name', 'entity-group.name', '33%', this.entityTitle),
      new EntityTableColumn<EntityGroupInfo>('description', 'entity-group.description', '40%',
        (entityGroup) =>
          entityGroup && entityGroup.additionalInfo && isDefinedAndNotNull(entityGroup.additionalInfo.description)
            ? entityGroup.additionalInfo.description : '', entity => ({}), false)
    );
    if (publicGroupTypes.has(this.groupType)) {
      this.columns.push(
        new EntityTableColumn<EntityGroupInfo>('isPublic', 'entity-group.public', '60px',
          entityGroup => {
            return checkBoxCell(entityGroup && entityGroup.additionalInfo ? entityGroup.additionalInfo.isPublic : false);
          }, () => ({}), false)
      );
    }

    this.deleteEntityTitle = entityGroup =>
      this.translate.instant('entity-group.delete-entity-group-title', { entityGroupName: entityGroup.name });
    this.deleteEntityContent = () => this.translate.instant('entity-group.delete-entity-group-text');
    this.deleteEntitiesTitle = count => this.translate.instant('entity-group.delete-entity-groups-title', {count});
    this.deleteEntitiesContent = () => this.translate.instant('entity-group.delete-entity-groups-text');

    this.entitiesFetchFunction = pageLink => {
      let fetchObservable: Observable<Array<EntityGroupInfo>>;
      if (this.customerId) {
        fetchObservable = this.entityGroupService.getEntityGroupsByOwnerId(EntityType.CUSTOMER, this.customerId, this.groupType);
      } else {
        fetchObservable = this.entityGroupService.getEntityGroups(this.groupType);
      }
      return fetchObservable.pipe(
        map((entityGroups) => pageLink.filterData(entityGroups))
      );
    };

    this.loadEntity = id => this.entityGroupService.getEntityGroup(id.id);

    this.saveEntity = entityGroup => {
      entityGroup.type = this.groupType;
      if (this.customerId) {
        entityGroup.ownerId = {
          entityType: EntityType.CUSTOMER,
          id: this.customerId
        };
      }
      return this.entityGroupService.saveEntityGroup(entityGroup).pipe(
        tap((savedEntityGroup) => {
            this.notifyEntityGroupUpdated();
          }
        ));
    };

    this.deleteEntity = id => {
      return this.entityGroupService.deleteEntityGroup(id.id).pipe(
        tap(() => {
            this.notifyEntityGroupUpdated();
          }
        ));
    };

    this.onEntityAction = action => this.onEntityGroupAction(action);

    this.deleteEnabled = (entityGroup) => entityGroup && !entityGroup.groupAll &&
      this.userPermissionsService.hasEntityGroupPermission(Operation.DELETE, entityGroup);
    this.detailsReadonly = (entityGroup) =>
      !this.userPermissionsService.hasEntityGroupPermission(Operation.WRITE, entityGroup);
    this.entitySelectionEnabled = (entityGroup) => entityGroup && !entityGroup.groupAll &&
      this.userPermissionsService.hasEntityGroupPermission(Operation.DELETE, entityGroup);

    if (!this.userPermissionsService.hasGenericEntityGroupTypePermission(Operation.CREATE, this.groupType)) {
      this.addEnabled = false;
    }
    if (!this.userPermissionsService.hasGenericEntityGroupTypePermission(Operation.DELETE, this.groupType)) {
      this.entitiesDeleteEnabled = false;
    }
    this.componentsData = {
      isGroupEntitiesView: false
    };
    this.updateActionCellDescriptors();
    this.tableTitle = this.translate.instant(entityGroupsTitle(this.groupType));
    if (sharableGroupTypes.has(this.groupType) &&
      this.userPermissionsService.hasGenericPermission(Resource.GROUP_PERMISSION, Operation.CREATE)) {
      this.addEntity = () => this.entityGroupWizard();
    }
  }

  private updateActionCellDescriptors() {
    this.cellActionDescriptors.splice(0);
    this.cellActionDescriptors.push(
      {
        name: this.translate.instant('action.open'),
        icon: 'view_list',
        isEnabled: (entity) => true,
        onAction: ($event, entity) => this.open($event, entity)
      }
    );
    if (sharableGroupTypes.has(this.groupType) &&
      this.userPermissionsService.hasGenericPermission(Resource.GROUP_PERMISSION, Operation.CREATE)) {
      this.cellActionDescriptors.push(
        {
          name: this.translate.instant('action.share'),
          icon: 'assignment_ind',
          isEnabled: (entity) => entity && this.userPermissionsService.hasEntityGroupPermission(Operation.WRITE, entity),
          onAction: ($event, entity) => this.share($event, entity)
        }
      );
    }
    if (publicGroupTypes.has(this.groupType)) {
      this.cellActionDescriptors.push(
        {
          name: this.translate.instant('action.make-public'),
          icon: 'share',
          isEnabled: (entity) => entity
            && (!entity.additionalInfo || !entity.additionalInfo.isPublic)
            && this.userPermissionsService.isDirectlyOwnedGroup(entity)
            && this.userPermissionsService.hasEntityGroupPermission(Operation.WRITE, entity),
          onAction: ($event, entity) => this.makePublic($event, entity)
        },
        {
          name: this.translate.instant('action.make-private'),
          icon: 'reply',
          isEnabled: (entity) => entity
            && entity.additionalInfo && entity.additionalInfo.isPublic
            && this.userPermissionsService.isDirectlyOwnedGroup(entity)
            && this.userPermissionsService.hasEntityGroupPermission(Operation.WRITE, entity),
          onAction: ($event, entity) => this.makePrivate($event, entity)
        }
      );
    }
  }

  private entityGroupWizard(): Observable<EntityGroupInfo> {
    return this.dialog.open<EntityGroupWizardDialogComponent, AddEntityDialogData<EntityGroupInfo>,
      EntityGroupWizardDialogResult>(EntityGroupWizardDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        entitiesTableConfig: this
      }
    }).afterClosed().pipe(
      map((result) => {
        if (result && result.shared) {
          this.notifyEntityGroupUpdated();
        }
        return result?.entityGroup;
      }
    ));
  }

  private share($event: Event, entityGroup: EntityGroupInfo) {
    if ($event) {
      $event.stopPropagation();
    }
    this.homeDialogs.shareEntityGroup($event, entityGroup).subscribe((res) => {
      if (res) {
        this.onGroupUpdated();
      }
    });
  }

  private makePublic($event: Event, entityGroup: EntityGroupInfo) {
    if ($event) {
      $event.stopPropagation();
    }
    this.homeDialogs.makeEntityGroupPublic($event, entityGroup)
      .subscribe((res) => {
        if (res) {
          this.onGroupUpdated();
        }
      });
  }

  private makePrivate($event: Event, entityGroup: EntityGroupInfo) {
    if ($event) {
      $event.stopPropagation();
    }
    this.homeDialogs.makeEntityGroupPrivate($event, entityGroup)
      .subscribe((res) => {
        if (res) {
          this.onGroupUpdated();
        }
      });
  }

  onGroupUpdated() {
    this.notifyEntityGroupUpdated();
    if (this.componentsData.isGroupEntitiesView) {
      this.componentsData.reloadEntityGroup();
    } else {
      this.table.updateData();
    }
  }

  private notifyEntityGroupUpdated() {
    if (!this.customerId) {
      this.broadcast.broadcast(this.groupType + 'changed');
    }
    if (!this.componentsData.isGroupEntitiesView && this.params.hierarchyView) {
      this.params.hierarchyCallbacks.refreshEntityGroups(this.params.internalId);
    }
  }

  private open($event: Event, entityGroup: EntityGroupInfo) {
    if ($event) {
      $event.stopPropagation();
    }
    if (this.params.hierarchyView) {
      this.params.hierarchyCallbacks.groupSelected(this.params.nodeId, entityGroup.id.id);
    } else {
      const url = this.router.createUrlTree([entityGroup.id.id], {relativeTo: this.table.route});
      this.router.navigateByUrl(url);
    }
  }

  private onEntityGroupAction(action: EntityAction<EntityGroupInfo>): boolean {
    switch (action.action) {
      case 'open':
        this.open(action.event, action.entity);
        return true;
      case 'share':
        this.share(action.event, action.entity);
        return true;
      case 'makePublic':
        this.makePublic(action.event, action.entity);
        return true;
      case 'makePrivate':
        this.makePrivate(action.event, action.entity);
        return true;
    }
    return false;
  }
}
