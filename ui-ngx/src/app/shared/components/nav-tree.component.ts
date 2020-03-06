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

import { Component, ElementRef, Input, NgZone, OnInit, ViewEncapsulation } from '@angular/core';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { deepClone } from '@core/utils';

export interface NavTreeNodeState {
  disabled?: boolean;
  opened?: boolean;
  loaded?: boolean;
}

export interface NavTreeNode {
  id: string;
  icon?: boolean;
  text?: string;
  state?: NavTreeNodeState;
  children?: NavTreeNode[] | boolean;
  data?: any;
}

export interface NavTreeEditCallbacks {
  selectNode?: (id: string) => void;
  deselectAll?: () => void;
  getNode?: (id: string) => NavTreeNode;
  getParentNodeId?: (id: string) => string;
  openNode?: (id: string, cb?: () => void) => void;
  nodeIsOpen?: (id: string) => boolean;
  nodeIsLoaded?: (id: string) => boolean;
  refreshNode?: (id: string) => void;
  updateNode?: (id: string, newName: string) => void;
  createNode?: (parentId: string, node: NavTreeNode, pos: number) => void;
  deleteNode?: (id: string) => void;
  disableNode?: (id: string) => void;
  enableNode?: (id: string) => void;
  setNodeHasChildren?: (id: string, hasChildren: boolean) => void;
  search?: (searchText: string) => void;
  clearSearch?: () => void;
}

export type NodesCallback = (nodes: NavTreeNode[]) => void;
export type LoadNodesCallback = (node: NavTreeNode, cb: NodesCallback) => void;
export type NodeSearchCallback = (searchText: string, node: NavTreeNode) => boolean;
export type NodeSelectedCallback = (node: NavTreeNode, event: Event) => void;
export type NodesInsertedCallback = (nodes: string[], parent: string) => void;

@Component({
  selector: 'tb-nav-tree',
  templateUrl: './nav-tree.component.html',
  styleUrls: ['./nav-tree.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class NavTreeComponent implements OnInit {

  private enableSearchValue: boolean;
  get enableSearch(): boolean {
    return this.enableSearchValue;
  }
  @Input()
  set enableSearch(value: boolean) {
    this.enableSearchValue = coerceBooleanProperty(value);
  }

  @Input()
  private loadNodes: LoadNodesCallback;

  @Input()
  private searchCallback: NodeSearchCallback;

  @Input()
  private onNodeSelected: NodeSelectedCallback;

  @Input()
  private onNodesInserted: NodesInsertedCallback;

  @Input()
  private editCallbacks: NavTreeEditCallbacks;

  private treeElement: JSTree;

  constructor(private elementRef: ElementRef<HTMLElement>,
              private ngZone: NgZone) {
  }

  ngOnInit(): void {
    this.initTree();
  }

  private initTree() {

    const loadNodes: LoadNodesCallback = (node, cb) => {
      const outCb = (nodes: NavTreeNode[]) => {
        const copied: NavTreeNode[] = [];
        if (nodes) {
          nodes.forEach((n) => {
            copied.push(deepClone(n, ['data']));
          });
        }
        cb(copied);
      };
      this.ngZone.runOutsideAngular(() => {
        this.loadNodes(node, outCb);
      });
    };

    const config: JSTreeStaticDefaults = {
      core: {
        worker: false,
        multiple: false,
        check_callback: true,
        themes: { name: 'proton', responsive: true },
        data: loadNodes,
        error: () => {
          console.error('Unexpected jstree error!');
        }
      },
      plugins: []
    };

    if (this.enableSearch) {
      config.plugins.push('search');
      config.search = {
        ajax: false,
        fuzzy: false,
        close_opened_onclear: true,
        case_sensitive: false,
        show_only_matches: true,
        show_only_matches_children: false,
        search_leaves_only: false,
        search_callback: this.searchCallback
      };
    }

    this.treeElement = $('.tb-nav-tree-container', this.elementRef.nativeElement).jstree(config);

    this.treeElement.on('changed.jstree', (e: any, data) => {
      const node: NavTreeNode = data.instance.get_selected(true)[0];
      if (this.onNodeSelected) {
        this.onNodeSelected(node, e as Event);
      }
    });

    this.treeElement.on('model.jstree', (e: any, data) => {
      if (this.onNodesInserted) {
        this.onNodesInserted(data.nodes, data.parent);
      }
    });

    if (this.editCallbacks) {
      this.editCallbacks.selectNode = id => {
        const node: NavTreeNode = this.treeElement.jstree('get_node', id);
        if (node) {
          this.treeElement.jstree('deselect_all', true);
          this.treeElement.jstree('select_node', node);
        }
      };
      this.editCallbacks.deselectAll = () => {
        this.treeElement.jstree('deselect_all');
      };
      this.editCallbacks.getNode = (id) => {
        const node: NavTreeNode = this.treeElement.jstree('get_node', id);
        return node;
      };
      this.editCallbacks.getParentNodeId = (id) => {
        const node: NavTreeNode = this.treeElement.jstree('get_node', id);
        if (node) {
          return this.treeElement.jstree('get_parent', node);
        }
      };
      this.editCallbacks.openNode = (id, cb) => {
        const node: NavTreeNode = this.treeElement.jstree('get_node', id);
        if (node) {
          this.treeElement.jstree('open_node', node, cb);
        }
      };
      this.editCallbacks.nodeIsOpen = (id) => {
        const node: NavTreeNode = this.treeElement.jstree('get_node', id);
        if (node) {
          return this.treeElement.jstree('is_open', node);
        } else {
          return true;
        }
      };
      this.editCallbacks.nodeIsLoaded = (id) => {
        const node: NavTreeNode = this.treeElement.jstree('get_node', id);
        if (node) {
          return this.treeElement.jstree('is_loaded', node);
        } else {
          return true;
        }
      };
      this.editCallbacks.refreshNode = (id) => {
        if (id === '#') {
          this.treeElement.jstree('refresh');
          this.treeElement.jstree('redraw');
        } else {
          const node: NavTreeNode = this.treeElement.jstree('get_node', id);
          if (node) {
            const opened = this.treeElement.jstree('is_open', node);
            this.treeElement.jstree('refresh_node', node);
            this.treeElement.jstree('redraw');
            if (node.children && opened/* && !node.children.length*/) {
              this.treeElement.jstree('open_node', node);
            }
          }
        }
      };
      this.editCallbacks.updateNode = (id, newName) => {
        const node: NavTreeNode = this.treeElement.jstree('get_node', id);
        if (node) {
          this.treeElement.jstree('rename_node', node, newName);
        }
      };
      this.editCallbacks.createNode = (parentId, node, pos) => {
        const parentNode: NavTreeNode = this.treeElement.jstree('get_node', parentId);
        if (parentNode) {
          this.treeElement.jstree('create_node', parentNode, node, pos);
        }
      };
      this.editCallbacks.deleteNode = (id) => {
        const node: NavTreeNode = this.treeElement.jstree('get_node', id);
        if (node) {
          this.treeElement.jstree('delete_node', node);
        }
      };
      this.editCallbacks.disableNode = (id) => {
        const node: NavTreeNode = this.treeElement.jstree('get_node', id);
        if (node) {
          this.treeElement.jstree('disable_node', node);
        }
      };
      this.editCallbacks.enableNode = (id) => {
        const node: NavTreeNode = this.treeElement.jstree('get_node', id);
        if (node) {
          this.treeElement.jstree('enable_node', node);
        }
      };
      this.editCallbacks.setNodeHasChildren = (id, hasChildren) => {
        const node: NavTreeNode = this.treeElement.jstree('get_node', id);
        if (node) {
          if (!node.children || (Array.isArray(node.children) && !node.children.length)) {
            node.children = hasChildren;
            node.state.loaded = !hasChildren;
            node.state.opened = false;
            this.treeElement.jstree('_node_changed', node.id);
            this.treeElement.jstree('redraw');
          }
        }
      };
      this.editCallbacks.search = (searchText) => {
        this.treeElement.jstree('search', searchText);
      };
      this.editCallbacks.clearSearch = () => {
        this.treeElement.jstree('clear_search');
      };
    }
  }
}
