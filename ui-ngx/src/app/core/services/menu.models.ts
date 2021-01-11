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

import { Observable } from 'rxjs';
import { EntityType } from '@shared/models/entity-type.models';
import { HasUUID } from '@shared/models/id/has-uuid';

export declare type MenuSectionType = 'link' | 'toggle';

export interface MenuSection extends HasUUID{
  name: string;
  type: MenuSectionType;
  path: string;
  queryParams?: {[k: string]: any};
  icon: string;
  notExact?: boolean;
  iconUrl?: string;
  isMdiIcon?: boolean;
  asyncPages?: Observable<Array<MenuSection>>;
  pages?: Array<MenuSection>;
  disabled?: boolean;
  ignoreTranslate?: boolean;
  groupType?: EntityType;
  isCustom?: boolean;
  stateId?: string;
  childStateIds?: {[stateId: string]: boolean};
}

export interface HomeSection {
  name: string;
  places: Array<HomeSectionPlace>;
}

export interface HomeSectionPlace {
  name: string;
  icon: string;
  isMdiIcon?: boolean;
  path: string;
  disabled?: boolean;
}
