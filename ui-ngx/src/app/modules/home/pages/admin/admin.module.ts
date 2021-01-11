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

import { AdminRoutingModule } from './admin-routing.module';
import { SharedModule } from '@app/shared/shared.module';
import { MailServerComponent } from '@modules/home/pages/admin/mail-server.component';
import { GeneralSettingsComponent } from '@modules/home/pages/admin/general-settings.component';
import { SecuritySettingsComponent } from '@modules/home/pages/admin/security-settings.component';
import { HomeComponentsModule } from '@modules/home/components/home-components.module';
import { MailTemplatesComponent } from '@home/pages/admin/mail-templates.component';
import { CustomTranslationComponent } from '@home/pages/admin/custom-translation.component';
import { CustomMenuComponent } from '@home/pages/admin/custom-menu.component';
import { WhiteLabelingComponent } from '@home/pages/admin/white-labeling.component';
import { PaletteComponent } from '@home/pages/admin/palette.component';
import { PaletteDialogComponent } from '@home/pages/admin/palette-dialog.component';
import { CustomCssDialogComponent } from '@home/pages/admin/custom-css-dialog.component';
import { SelfRegistrationComponent } from '@home/pages/admin/self-registration.component';
import { OAuth2SettingsComponent } from '@modules/home/pages/admin/oauth2-settings.component';
import { SmsProviderComponent } from '@home/pages/admin/sms-provider.component';
import { SendTestSmsDialogComponent } from '@home/pages/admin/send-test-sms-dialog.component';

@NgModule({
  declarations:
    [
      GeneralSettingsComponent,
      MailServerComponent,
      MailTemplatesComponent,
      SmsProviderComponent,
      SendTestSmsDialogComponent,
      CustomTranslationComponent,
      CustomMenuComponent,
      WhiteLabelingComponent,
      SecuritySettingsComponent,
      PaletteComponent,
      PaletteDialogComponent,
      CustomCssDialogComponent,
      SelfRegistrationComponent,
      SecuritySettingsComponent,
      OAuth2SettingsComponent
    ],
  imports: [
    CommonModule,
    SharedModule,
    HomeComponentsModule,
    AdminRoutingModule
  ]
})
export class AdminModule { }
