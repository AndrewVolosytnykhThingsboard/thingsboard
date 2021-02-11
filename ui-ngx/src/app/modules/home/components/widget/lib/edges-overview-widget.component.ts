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

import { Component, Input, OnInit } from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { WidgetContext } from '@home/models/widget-component.models';
import { Datasource, DatasourceType, WidgetConfig } from '@shared/models/widget.models';
import { IWidgetSubscription } from '@core/api/widget-api.models';
import { UtilsService } from '@core/services/utils.service';
import { LoadNodesCallback } from '@shared/components/nav-tree.component';
import { EntityType } from '@shared/models/entity-type.models';
import {
  EdgeGroupsNodeData,
  edgeGroupsNodeText,
  EdgeOverviewNode,
  EntityGroupNodeData,
  entityGroupNodeText,
  EntityGroupsNodeData,
  EntityNodeDatasource,
  entityNodeText
} from '@home/components/widget/lib/edges-overview-widget.models';
import { EdgeService } from '@core/http/edge.service';
import { EntityService } from '@core/http/entity.service';
import { TranslateService } from '@ngx-translate/core';
import { PageLink } from '@shared/models/page/page-link';
import { BaseData, HasId } from '@shared/models/base-data';
import { EntityId } from '@shared/models/id/entity-id';
import { edgeAllEntityTypes, edgeEntityGroupTypes, edgeEntityTypes } from "@shared/models/edge.models";
import { groupResourceByGroupType, Operation, resourceByEntityType } from "@shared/models/security.models";
import { UserPermissionsService } from "@core/http/user-permissions.service";
import { EntityGroupService } from '@core/http/entity-group.service';
import { EntityGroupInfo } from '@shared/models/entity-group.models';
import { getCurrentAuthState } from '@core/auth/auth.selectors';
import { Authority } from '@shared/models/authority.enum';

@Component({
  selector: 'tb-edges-overview-widget',
  templateUrl: './edges-overview-widget.component.html',
  styleUrls: ['./edges-overview-widget.component.scss']
})
export class EdgesOverviewWidgetComponent extends PageComponent implements OnInit {

  @Input()
  ctx: WidgetContext;

  public toastTargetId = 'edges-overview-' + this.utils.guid();
  public edgeIsDatasource: boolean = true;

  private widgetConfig: WidgetConfig;
  private subscription: IWidgetSubscription;
  private datasources: Array<EntityNodeDatasource>;

  private nodeIdCounter = 0;

  private isSysAdmin: boolean = getCurrentAuthState(this.store).authUser.authority === Authority.SYS_ADMIN;

  private allowedGroupTypes: Array<EntityType> = edgeEntityGroupTypes.filter((groupType) =>
    this.userPermissionsService.hasGenericPermission(groupResourceByGroupType.get(groupType), Operation.READ));
  private allowedEntityTypes: Array<EntityType> = edgeEntityTypes.filter((entityType) =>
    this.userPermissionsService.hasGenericPermission(resourceByEntityType.get(entityType), Operation.READ));

  constructor(protected store: Store<AppState>,
              private edgeService: EdgeService,
              private entityService: EntityService,
              private entityGroupService: EntityGroupService,
              private translateService: TranslateService,
              private utils: UtilsService,
              private userPermissionsService: UserPermissionsService) {
    super(store);
  }

  ngOnInit(): void {
    this.widgetConfig = this.ctx.widgetConfig;
    this.subscription = this.ctx.defaultSubscription;
    this.datasources = this.subscription.datasources as Array<EntityNodeDatasource>;
    this.ctx.updateWidgetParams();
  }

  public loadNodes: LoadNodesCallback = (node, cb) => {
    const datasource: Datasource = this.datasources[0];
    if (datasource) {
      const pageLink = datasource.pageLink ? new PageLink(datasource.pageLink.pageSize) : null;
      const groupType = node.data ? node.data.groupType : null;
      if (node.id === '#') {
        if (datasource.type === DatasourceType.entity && datasource.entity.id.entityType === EntityType.EDGE) {
          const selectedEdge: BaseData<EntityId> = datasource.entity;
          cb(this.loadNodesForEdge(selectedEdge, groupType));
        } else if (datasource.type === DatasourceType.function) {
          cb(this.loadNodesForEdge(datasource.entity, groupType));
        } else {
          this.edgeIsDatasource = false;
          cb([]);
        }
      } else if (node.data && node.data.type === 'edgeGroups' && datasource.type === DatasourceType.entity) {
        const edgeId = node.data.group.id.id;
        this.entityService.getAssignedToEdgeEntitiesByType(edgeId, groupType, pageLink).subscribe((entityGroups) => {
          cb(this.entityGroupsToNodes(entityGroups, groupType));
        });
      } else if (node.data && node.data.type === 'groups') {
        const entityId = node.data.group.id.id;
        this.entityGroupService.getEntityGroupEntities(entityId, pageLink, groupType).subscribe(
          (entities) => {
            cb(this.entitiesToNodes(entities.data, groupType));
          }
        );
      } else {
        cb([]);
      }
    } else {
      cb([]);
    }
  }

  private loadNodesForEdge(group: BaseData<HasId>, groupType: EntityType): EdgeOverviewNode[] {
    const nodes: EdgeOverviewNode[] = [];
    const allowedGroupAndEntityTypes: Array<EntityType> = this.isSysAdmin ? edgeAllEntityTypes : this.allowedGroupTypes.concat(this.allowedEntityTypes);
    allowedGroupAndEntityTypes.forEach((groupType) => {
      const node: EdgeOverviewNode = this.createEdgeGroupsNode(group, groupType);
      nodes.push(node);
    });
    return nodes;
  }

  private createEdgeGroupsNode(group: BaseData<HasId>, groupType: EntityType): EdgeOverviewNode {
    return {
      id: (++this.nodeIdCounter)+'',
      icon: false,
      text: edgeGroupsNodeText(this.translateService, groupType),
      children: true,
      data: {
        type: 'edgeGroups',
        group,
        groupType
      } as EdgeGroupsNodeData
    };
  }

  private entityGroupsToNodes(entityGroups: EntityGroupInfo[], groupType: EntityType): EdgeOverviewNode[] {
    const nodes: EdgeOverviewNode[] = [];
    if (entityGroups) {
      entityGroups.forEach((entityGroup) => {
        const node = this.createEntityGroupsNode(entityGroup, groupType);
        nodes.push(node);
      });
    }
    return nodes;
  }

  private createEntityGroupsNode(group: EntityGroupInfo, groupType: EntityType): EdgeOverviewNode {
    return {
      id: (++this.nodeIdCounter)+'',
      icon: false,
      text: group.id.entityType === EntityType.ENTITY_GROUP ? entityGroupNodeText(group) : entityNodeText(group),
      children: group.id.entityType === EntityType.ENTITY_GROUP,
      data: {
        type: 'groups',
        group: group,
        groupType
      } as EntityGroupsNodeData
    } as EdgeOverviewNode;
  }

  private entitiesToNodes(entities: BaseData<HasId>[], groupType: EntityType): EdgeOverviewNode[] {
    const nodes: EdgeOverviewNode[] = [];
    if (entities) {
      entities.forEach((entity) => {
        const node = this.createEntityGroupNode(entity, groupType);
        nodes.push(node);
      });
    }
    return nodes;
  }

  private createEntityGroupNode(group: BaseData<HasId>, groupType: EntityType): EdgeOverviewNode {
    return {
      id: (++this.nodeIdCounter)+'',
      icon: false,
      text: entityNodeText(group),
      children: false,
      data: {
        type: 'group',
        group,
        groupType
      } as EntityGroupNodeData
    } as EdgeOverviewNode;
  }

}
