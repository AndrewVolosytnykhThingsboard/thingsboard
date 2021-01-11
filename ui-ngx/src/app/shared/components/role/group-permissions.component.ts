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

import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnInit, Output, ViewChild } from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { PageLink } from '@shared/models/page/page-link';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { TranslateService } from '@ngx-translate/core';
import { MatDialog } from '@angular/material/dialog';
import { DialogService } from '@core/services/dialog.service';
import { Direction, SortOrder } from '@shared/models/page/sort-order';
import { forkJoin, fromEvent, merge, Observable } from 'rxjs';
import { debounceTime, distinctUntilChanged, tap } from 'rxjs/operators';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { Operation, Resource } from '@shared/models/security.models';
import { EntityType } from '@shared/models/entity-type.models';
import { UserPermissionsService } from '@core/http/user-permissions.service';
import { EntityGroupInfo } from '@shared/models/entity-group.models';
import { GroupPermissionsDatasource } from '@home/models/datasource/group-permissions.datasource';
import { RoleService } from '@core/http/role.service';
import { GroupPermission, GroupPermissionFullInfo } from '@shared/models/group-permission.models';
import { deepClone } from '@core/utils';
import {
  GroupPermissionDialogComponent,
  GroupPermissionDialogData
} from '@shared/components/role/group-permission-dialog.component';
import { ViewRoleDialogComponent, ViewRoleDialogData } from '@home/components/role/view-role-dialog.component';

@Component({
  selector: 'tb-group-permissions',
  templateUrl: './group-permissions.component.html',
  styleUrls: ['./group-permissions.component.scss']
})
export class GroupPermissionsComponent extends PageComponent implements AfterViewInit, OnInit {

  groupPermissionsMode: 'group' | 'registration';

  displayedColumns: string[] = [];
  columnsWidth: {[key: string]: string} = {};

  editEnabled = false;
  addEnabled = false;
  deleteEnabled = false;

  isUserGroup = false;

  pageLink: PageLink;
  textSearchMode = false;
  dataSource: GroupPermissionsDatasource;

  activeValue = false;
  dirtyValue = false;
  entityGroupValue: EntityGroupInfo;

  registrationPermissionsValue: Array<GroupPermission>;

  viewsInited = false;

  @Input()
  set active(active: boolean) {
    if (this.activeValue !== active) {
      this.activeValue = active;
      if (this.activeValue && this.dirtyValue) {
        this.dirtyValue = false;
        if (this.viewsInited) {
          this.updateData(true);
        }
      }
    }
  }

  @Input()
  set entityGroup(entityGroup: EntityGroupInfo) {
    if (this.entityGroupValue !== entityGroup) {
      this.entityGroupValue = entityGroup;
      this.entityGroupUpdated(entityGroup);
      if (this.viewsInited) {
        this.resetSortAndFilter(this.activeValue);
        if (!this.activeValue) {
          this.dirtyValue = true;
        }
      }
    }
  }

  get entityGroup(): EntityGroupInfo {
    return this.entityGroupValue;
  }

  @Input()
  set registrationPermissions(registrationPermissions: Array<GroupPermission>) {
    if (this.registrationPermissionsValue !== registrationPermissions) {
      this.registrationPermissionsValue = registrationPermissions;
      this.registrationPermissionsUpdated(registrationPermissions);
      if (this.viewsInited) {
        this.resetSortAndFilter(this.activeValue);
        if (!this.activeValue) {
          this.dirtyValue = true;
        }
      }
    }
  }

  get registrationPermissions(): Array<GroupPermission> {
    return this.registrationPermissionsValue;
  }

  private readonlyValue: boolean;
  get readonly(): boolean {
    return this.readonlyValue;
  }

  @Input()
  set readonly(value: boolean) {
    this.readonlyValue = coerceBooleanProperty(value);
  }

  @Output()
  permissionsChanged = new EventEmitter();

  @ViewChild('searchInput') searchInputField: ElementRef;

  @ViewChild(MatPaginator) paginator: MatPaginator;
  @ViewChild(MatSort) sort: MatSort;

  constructor(protected store: Store<AppState>,
              private roleService: RoleService,
              public translate: TranslateService,
              public dialog: MatDialog,
              private userPermissionsService: UserPermissionsService,
              private dialogService: DialogService) {
    super(store);
    this.dirtyValue = !this.activeValue;
    const sortOrder: SortOrder = { property: 'roleName', direction: Direction.ASC };
    this.pageLink = new PageLink(10, 0, null, sortOrder);
    this.dataSource = new GroupPermissionsDatasource(this.roleService, this.translate);
  }

  ngOnInit() {
  }

  private entityGroupUpdated(entityGroup: EntityGroupInfo) {
    this.groupPermissionsMode = 'group';
    this.editEnabled = false;
    this.addEnabled = false;
    this.deleteEnabled = false;
    this.isUserGroup = entityGroup.type === EntityType.USER;
    const readOnlyGroup = !this.userPermissionsService.hasEntityGroupPermission(Operation.WRITE, entityGroup);
    if (!readOnlyGroup) {
      this.editEnabled = !this.readonly && this.userPermissionsService.hasGenericPermission(Resource.GROUP_PERMISSION, Operation.WRITE);
      this.addEnabled = !this.readonly && this.userPermissionsService.hasGenericPermission(Resource.GROUP_PERMISSION, Operation.CREATE);
      this.deleteEnabled = !this.readonly && this.userPermissionsService.hasGenericPermission(Resource.GROUP_PERMISSION, Operation.DELETE);
    }
    this.updateColumns();
  }

  private registrationPermissionsUpdated(registrationPermissions: Array<GroupPermission>) {
    this.groupPermissionsMode = 'registration';
    this.editEnabled = !this.readonly;
    this.addEnabled = !this.readonly;
    this.deleteEnabled = !this.readonly;
    this.isUserGroup = true;
    this.updateColumns();
  }

  updateColumns() {
    this.displayedColumns = [];
    if (this.deleteEnabled) {
      this.displayedColumns.push('select');
    }
    this.displayedColumns.push('roleName');
    if (this.isUserGroup) {
      this.displayedColumns.push('roleTypeName', 'entityGroupTypeName', 'entityGroupName');
      if (this.groupPermissionsMode === 'group') {
        this.displayedColumns.push('entityGroupOwnerFullName');
        this.columnsWidth.roleName = '20%';
        this.columnsWidth.roleTypeName = '15%';
        this.columnsWidth.entityGroupTypeName = '15%';
        this.columnsWidth.entityGroupName = '25%';
        this.columnsWidth.entityGroupOwnerFullName = '25%';
      } else {
        this.columnsWidth.roleName = '40%';
        this.columnsWidth.roleTypeName = '20%';
        this.columnsWidth.entityGroupTypeName = '20%';
        this.columnsWidth.entityGroupName = '20%';
      }
    } else {
      this.displayedColumns.push('userGroupName', 'userGroupOwnerFullName');
      this.columnsWidth.roleName = '33%';
      this.columnsWidth.userGroupName = '33%';
      this.columnsWidth.userGroupOwnerFullName = '33%';
    }
    this.displayedColumns.push('actions');
  }

  ngAfterViewInit() {

    fromEvent(this.searchInputField.nativeElement, 'keyup')
      .pipe(
        debounceTime(150),
        distinctUntilChanged(),
        tap(() => {
          this.paginator.pageIndex = 0;
          this.updateData();
        })
      )
      .subscribe();

    this.sort.sortChange.subscribe(() => this.paginator.pageIndex = 0);

    merge(this.sort.sortChange, this.paginator.page)
      .pipe(
        tap(() => this.updateData())
      )
      .subscribe();

    this.viewsInited = true;
    if (this.activeValue && (this.entityGroupValue || this.registrationPermissionsValue)) {
      this.updateData(true);
    }
  }

  updateData(reload: boolean = false) {
    this.pageLink.page = this.paginator.pageIndex;
    this.pageLink.pageSize = this.paginator.pageSize;
    this.pageLink.sortOrder.property = this.sort.active;
    this.pageLink.sortOrder.direction = Direction[this.sort.direction.toUpperCase()];
    this.dataSource.loadGroupPermissions(this.entityGroupValue, this.registrationPermissionsValue, this.pageLink, reload);
  }

  enterFilterMode() {
    this.textSearchMode = true;
    this.pageLink.textSearch = '';
    setTimeout(() => {
      this.searchInputField.nativeElement.focus();
      this.searchInputField.nativeElement.setSelectionRange(0, 0);
    }, 10);
  }

  exitFilterMode() {
    this.textSearchMode = false;
    this.pageLink.textSearch = null;
    this.paginator.pageIndex = 0;
    this.updateData();
  }

  resetSortAndFilter(update: boolean = true) {
    this.pageLink.textSearch = null;
    this.paginator.pageIndex = 0;
    const sortable = this.sort.sortables.get('roleName');
    this.sort.active = sortable.id;
    this.sort.direction = 'asc';
    if (update) {
      this.updateData(true);
    }
  }

  reloadGroupPermissions() {
    this.updateData(true);
  }

  addGroupPermission($event: Event) {
    this.openGroupPermissionDialog($event);
  }

  editGroupPermission($event: Event, groupPermission: GroupPermissionFullInfo) {
    this.openGroupPermissionDialog($event, groupPermission);
  }

  deleteGroupPermission($event: Event, groupPermission: GroupPermissionFullInfo) {
    if ($event) {
      $event.stopPropagation();
    }
    const title = this.translate.instant('group-permission.delete-group-permission-title', {roleName: groupPermission.roleName});
    const content = this.translate.instant('group-permission.delete-group-permission-text');

    this.dialogService.confirm(
      title,
      content,
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        if (this.groupPermissionsMode === 'registration') {
          const index = this.registrationPermissionsValue.indexOf(groupPermission.sourceGroupPermission);
          if (index > -1) {
            this.registrationPermissionsValue.splice(index, 1);
            this.permissionsChanged.emit();
            this.reloadGroupPermissions();
          }
        } else {
          this.roleService.deleteGroupPermission(groupPermission.id.id).subscribe(
            () => {
              this.permissionsChanged.emit();
              this.reloadGroupPermissions();
            }
          );
        }
      }
    });
  }

  deleteGroupPermissions($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    if (this.dataSource.selection.selected.length > 0) {
      const title = this.translate.instant('group-permission.delete-group-permissions-title',
        {count: this.dataSource.selection.selected.length});
      const content = this.translate.instant('group-permission.delete-group-permissions-text');

      this.dialogService.confirm(
        title,
        content,
        this.translate.instant('action.no'),
        this.translate.instant('action.yes'),
        true
      ).subscribe((result) => {
        if (result) {
          if (this.groupPermissionsMode === 'registration') {
            this.dataSource.selection.selected.forEach((groupPermission) => {
              const index = this.registrationPermissionsValue.indexOf(groupPermission.sourceGroupPermission);
              if (index > -1) {
                this.registrationPermissionsValue.splice(index, 1);
              }
            });
            this.permissionsChanged.emit();
            this.reloadGroupPermissions();
          } else {
            const tasks: Observable<any>[] = [];
            this.dataSource.selection.selected.forEach((groupPermission) => {
              tasks.push(this.roleService.deleteGroupPermission(groupPermission.id.id));
            });
            forkJoin(tasks).subscribe(
              () => {
                this.permissionsChanged.emit();
                this.reloadGroupPermissions();
              }
            );
          }
        }
      });
    }
  }

  openGroupPermissionDialog($event: Event, groupPermission: GroupPermissionFullInfo = null) {
    if ($event) {
      $event.stopPropagation();
    }
    let isAdd = false;
    let index = -1;
    if (!groupPermission) {
      isAdd = true;
      groupPermission = {} as GroupPermissionFullInfo;
      if (this.isUserGroup) {
        if (this.groupPermissionsMode === 'group') {
          groupPermission.userGroupId = {
            entityType: EntityType.ENTITY_GROUP,
            id: this.entityGroup.id.id
          };
        }
      } else {
        groupPermission.entityGroupId = {
          entityType: EntityType.ENTITY_GROUP,
          id: this.entityGroup.id.id
        };
        groupPermission.entityGroupType = this.entityGroup.type;
      }
    } else {
      if (this.groupPermissionsMode === 'registration') {
        index = this.registrationPermissionsValue.indexOf(groupPermission.sourceGroupPermission);
      }
      groupPermission = deepClone(groupPermission, ['sourceGroupPermission']);
    }
    this.dialog.open<GroupPermissionDialogComponent, GroupPermissionDialogData, boolean | GroupPermission>(GroupPermissionDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        isAdd,
        isUserGroup: this.isUserGroup,
        groupPermission,
        groupPermissionsMode: this.groupPermissionsMode
      }
    }).afterClosed().subscribe(
      (res) => {
        if (res) {
          if (this.groupPermissionsMode === 'registration') {
            if (isAdd) {
              this.registrationPermissionsValue.push(res as GroupPermission);
            } else {
              this.registrationPermissionsValue[index] = res as GroupPermission;
            }
          }
          this.permissionsChanged.emit();
          this.reloadGroupPermissions();
        }
      }
    );
  }

  viewRole($event: Event, groupPermission: GroupPermissionFullInfo) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<ViewRoleDialogComponent, ViewRoleDialogData>(ViewRoleDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        role: groupPermission.role
      }
    })
  }

}
