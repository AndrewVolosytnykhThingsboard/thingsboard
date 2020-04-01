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

import { NgModule } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FooterComponent } from '@shared/components/footer.component';
import { LogoComponent } from '@shared/components/logo.component';
import { TbSnackBarComponent, ToastDirective } from '@shared/components/toast.directive';
import { BreadcrumbComponent } from '@shared/components/breadcrumb.component';
import { NgxFlowModule, FlowInjectionToken } from '@flowjs/ngx-flow';
import { NgxFlowchartModule } from 'ngx-flowchart/dist/ngx-flowchart';
import Flow from '@flowjs/flow.js';

import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatGridListModule } from '@angular/material/grid-list';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSliderModule } from '@angular/material/slider';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatSortModule } from '@angular/material/sort';
import { MatStepperModule } from '@angular/material/stepper';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDatetimepickerModule, MatNativeDatetimeModule } from '@mat-datetimepicker/core';
import { NgxDaterangepickerMd } from 'ngx-daterangepicker-material';
import { GridsterModule } from 'angular-gridster2';
import { FlexLayoutModule } from '@angular/flex-layout';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ShareModule as ShareButtonsModule } from '@ngx-share/core';
import { HotkeyModule } from 'angular2-hotkeys';
import { ColorPickerModule } from 'ngx-color-picker';
import { NgxHmCarouselModule } from 'ngx-hm-carousel';
import { EditorModule, TINYMCE_SCRIPT_SRC } from '@tinymce/tinymce-angular';
import { UserMenuComponent } from '@shared/components/user-menu.component';
import { NospacePipe } from '@shared/pipe/nospace.pipe';
import { TranslateModule } from '@ngx-translate/core';
import { TbCheckboxComponent } from '@shared/components/tb-checkbox.component';
import { HelpComponent } from '@shared/components/help.component';
import { TbAnchorComponent } from '@shared/components/tb-anchor.component';
import { MillisecondsToTimeStringPipe } from '@shared/pipe/milliseconds-to-time-string.pipe';
import { TimewindowComponent } from '@shared/components/time/timewindow.component';
import { OverlayModule } from '@angular/cdk/overlay';
import { TimewindowPanelComponent } from '@shared/components/time/timewindow-panel.component';
import { TimeintervalComponent } from '@shared/components/time/timeinterval.component';
import { DatetimePeriodComponent } from '@shared/components/time/datetime-period.component';
import { EnumToArrayPipe } from '@shared/pipe/enum-to-array.pipe';
import { ClipboardModule } from 'ngx-clipboard';
import { ValueInputComponent } from '@shared/components/value-input.component';
import { FullscreenDirective } from '@shared/components/fullscreen.directive';
import { HighlightPipe } from '@shared/pipe/highlight.pipe';
import { DashboardAutocompleteComponent } from '@shared/components/dashboard-autocomplete.component';
import { EntitySubTypeAutocompleteComponent } from '@shared/components/entity/entity-subtype-autocomplete.component';
import { EntitySubTypeSelectComponent } from '@shared/components/entity/entity-subtype-select.component';
import { EntityAutocompleteComponent } from '@shared/components/entity/entity-autocomplete.component';
import { EntityListComponent } from '@shared/components/entity/entity-list.component';
import { EntityTypeSelectComponent } from '@shared/components/entity/entity-type-select.component';
import { EntitySelectComponent } from '@shared/components/entity/entity-select.component';
import { EntityGroupAutocompleteComponent } from '@shared/components/group/entity-group-autocomplete.component';
import { EntityGroupSelectComponent } from '@shared/components/group/entity-group-select.component';
import { DatetimeComponent } from '@shared/components/time/datetime.component';
import { TimezoneSelectComponent } from '@shared/components/time/timezone-select.component';
import { EntityKeysListComponent } from '@shared/components/entity/entity-keys-list.component';
import { SocialSharePanelComponent } from '@shared/components/socialshare-panel.component';
import { RelationTypeAutocompleteComponent } from '@shared/components/relation/relation-type-autocomplete.component';
import { EntityListSelectComponent } from '@shared/components/entity/entity-list-select.component';
import { JsonObjectEditComponent } from '@shared/components/json-object-edit.component';
import { FooterFabButtonsComponent } from '@shared/components/footer-fab-buttons.component';
import { CircularProgressDirective } from '@shared/components/circular-progress.directive';
import { FabToolbarComponent, FabActionsDirective, FabTriggerDirective } from '@shared/components/fab-toolbar.component';
import { DashboardSelectPanelComponent } from '@shared/components/dashboard-select-panel.component';
import { DashboardSelectComponent } from '@shared/components/dashboard-select.component';
import { WidgetsBundleSelectComponent } from '@shared/components/widgets-bundle-select.component';
import { KeyboardShortcutPipe } from '@shared/pipe/keyboard-shortcut.pipe';
import { TbErrorComponent } from '@shared/components/tb-error.component';
import { EntityTypeListComponent } from '@shared/components/entity/entity-type-list.component';
import { EntitySubTypeListComponent } from '@shared/components/entity/entity-subtype-list.component';
import { TruncatePipe } from '@shared/pipe/truncate.pipe';
import { TbJsonPipe } from '@shared/pipe/tbJson.pipe';
import { ColorPickerDialogComponent } from '@shared/components/dialog/color-picker-dialog.component';
import { MatChipDraggableDirective } from '@shared/components/mat-chip-draggable.directive';
import { ColorInputComponent } from '@shared/components/color-input.component';
import { JsFuncComponent } from '@shared/components/js-func.component';
import { JsonFormComponent } from '@shared/components/json-form/json-form.component';
import { ConfirmDialogComponent } from '@shared/components/dialog/confirm-dialog.component';
import { AlertDialogComponent } from '@shared/components/dialog/alert-dialog.component';
import { TodoDialogComponent } from '@shared/components/dialog/todo-dialog.component';
import { MaterialIconsDialogComponent } from '@shared/components/dialog/material-icons-dialog.component';
import { MaterialIconSelectComponent } from '@shared/components/material-icon-select.component';
import { ImageInputComponent } from '@shared/components/image-input.component';
import { FileInputComponent } from '@shared/components/file-input.component';
import { NodeScriptTestDialogComponent } from '@shared/components/dialog/node-script-test-dialog.component';
import { MessageTypeAutocompleteComponent } from '@shared/components/message-type-autocomplete.component';
import { JsonContentComponent } from '@shared/components/json-content.component';
import { KeyValMapComponent } from '@shared/components/kv-map.component';
import { TbCheatSheetComponent } from '@shared/components/cheatsheet.component';
import { TbHotkeysDirective } from '@shared/components/hotkeys.directive';
import { NavTreeComponent } from '@shared/components/nav-tree.component';
import { LedLightComponent } from '@shared/components/led-light.component';
import { TbJsonToStringDirective } from '@shared/components/directives/tb-json-to-string.directive';
import { JsonObjectEditDialogComponent } from '@shared/components/dialog/json-object-edit-dialog.component';
import { HistorySelectorComponent } from './components/time/history-selector/history-selector.component';
import { TbTemplatePipe } from '@shared/pipe/template.pipe';
import { EntityGatewaySelectComponent } from '@shared/components/entity/entity-gateway-select.component';
import {
  HasGenericPermissionPipe,
  HasEntityGroupPermissionPipe,
  HasGroupEntityPermissionPipe
} from '@shared/pipe/permission.pipes';
import { OriginatorSelectComponent } from '@shared/components/originator-select.component';
import { ProgressDialogComponent } from '@shared/components/dialog/progress-dialog.component';

@NgModule({
  providers: [
    DatePipe,
    MillisecondsToTimeStringPipe,
    EnumToArrayPipe,
    HighlightPipe,
    TruncatePipe,
    TbJsonPipe,
    HasGenericPermissionPipe,
    HasEntityGroupPermissionPipe,
    HasGroupEntityPermissionPipe,
    {
      provide: FlowInjectionToken,
      useValue: Flow
    },
    {
      provide: TINYMCE_SCRIPT_SRC,
      useValue: 'tinymce/tinymce.min.js'
    }
  ],
  declarations: [
    FooterComponent,
    LogoComponent,
    FooterFabButtonsComponent,
    ToastDirective,
    FullscreenDirective,
    CircularProgressDirective,
    MatChipDraggableDirective,
    TbHotkeysDirective,
    TbAnchorComponent,
    HelpComponent,
    TbCheckboxComponent,
    TbSnackBarComponent,
    TbErrorComponent,
    TbCheatSheetComponent,
    BreadcrumbComponent,
    UserMenuComponent,
    TimewindowComponent,
    TimewindowPanelComponent,
    TimeintervalComponent,
    DashboardSelectComponent,
    DashboardSelectPanelComponent,
    DatetimePeriodComponent,
    DatetimeComponent,
    TimezoneSelectComponent,
    ValueInputComponent,
    DashboardAutocompleteComponent,
    EntitySubTypeAutocompleteComponent,
    EntitySubTypeSelectComponent,
    EntitySubTypeListComponent,
    EntityAutocompleteComponent,
    EntityListComponent,
    EntityTypeSelectComponent,
    EntitySelectComponent,
    EntityGroupAutocompleteComponent,
    EntityGroupSelectComponent,
    OriginatorSelectComponent,
    EntityKeysListComponent,
    EntityListSelectComponent,
    EntityTypeListComponent,
    RelationTypeAutocompleteComponent,
    SocialSharePanelComponent,
    JsonObjectEditComponent,
    JsonContentComponent,
    JsFuncComponent,
    FabTriggerDirective,
    FabActionsDirective,
    FabToolbarComponent,
    WidgetsBundleSelectComponent,
    ConfirmDialogComponent,
    AlertDialogComponent,
    ProgressDialogComponent,
    TodoDialogComponent,
    ColorPickerDialogComponent,
    MaterialIconsDialogComponent,
    ColorInputComponent,
    MaterialIconSelectComponent,
    NodeScriptTestDialogComponent,
    JsonFormComponent,
    ImageInputComponent,
    FileInputComponent,
    MessageTypeAutocompleteComponent,
    KeyValMapComponent,
    NavTreeComponent,
    LedLightComponent,
    NospacePipe,
    MillisecondsToTimeStringPipe,
    EnumToArrayPipe,
    HighlightPipe,
    TruncatePipe,
    TbJsonPipe,
    TbTemplatePipe,
    KeyboardShortcutPipe,
    HasGenericPermissionPipe,
    HasEntityGroupPermissionPipe,
    HasGroupEntityPermissionPipe,
    TbJsonToStringDirective,
    JsonObjectEditDialogComponent,
    HistorySelectorComponent,
    EntityGatewaySelectComponent
  ],
  imports: [
    CommonModule,
    RouterModule,
    TranslateModule,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    MatCardModule,
    MatProgressBarModule,
    MatInputModule,
    MatSnackBarModule,
    MatSidenavModule,
    MatToolbarModule,
    MatMenuModule,
    MatGridListModule,
    MatDialogModule,
    MatSelectModule,
    MatTooltipModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    MatTabsModule,
    MatRadioModule,
    MatSlideToggleModule,
    MatDatepickerModule,
    MatNativeDatetimeModule,
    MatDatetimepickerModule,
    NgxDaterangepickerMd.forRoot(),
    MatSliderModule,
    MatExpansionModule,
    MatStepperModule,
    MatAutocompleteModule,
    MatChipsModule,
    GridsterModule,
    ClipboardModule,
    FlexLayoutModule.withConfig({addFlexToParent: false}),
    FormsModule,
    ReactiveFormsModule,
    OverlayModule,
    ShareButtonsModule,
    HotkeyModule,
    ColorPickerModule,
    NgxHmCarouselModule,
    EditorModule,
    NgxFlowModule,
    NgxFlowchartModule
  ],
  exports: [
    FooterComponent,
    LogoComponent,
    FooterFabButtonsComponent,
    ToastDirective,
    FullscreenDirective,
    CircularProgressDirective,
    MatChipDraggableDirective,
    TbHotkeysDirective,
    TbAnchorComponent,
    HelpComponent,
    TbCheckboxComponent,
    TbErrorComponent,
    TbCheatSheetComponent,
    BreadcrumbComponent,
    UserMenuComponent,
    TimewindowComponent,
    TimewindowPanelComponent,
    TimeintervalComponent,
    DashboardSelectComponent,
    DatetimePeriodComponent,
    DatetimeComponent,
    TimezoneSelectComponent,
    DashboardAutocompleteComponent,
    EntitySubTypeAutocompleteComponent,
    EntitySubTypeSelectComponent,
    EntitySubTypeListComponent,
    EntityAutocompleteComponent,
    EntityListComponent,
    EntityTypeSelectComponent,
    EntitySelectComponent,
    EntityGroupAutocompleteComponent,
    EntityGroupSelectComponent,
    OriginatorSelectComponent,
    EntityKeysListComponent,
    EntityListSelectComponent,
    EntityTypeListComponent,
    RelationTypeAutocompleteComponent,
    SocialSharePanelComponent,
    JsonObjectEditComponent,
    JsonContentComponent,
    JsFuncComponent,
    FabTriggerDirective,
    FabActionsDirective,
    FabToolbarComponent,
    WidgetsBundleSelectComponent,
    ValueInputComponent,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    MatCardModule,
    MatProgressBarModule,
    MatInputModule,
    MatSnackBarModule,
    MatSidenavModule,
    MatToolbarModule,
    MatMenuModule,
    MatGridListModule,
    MatDialogModule,
    MatSelectModule,
    MatTooltipModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    MatTabsModule,
    MatRadioModule,
    MatSlideToggleModule,
    MatDatepickerModule,
    MatNativeDatetimeModule,
    MatDatetimepickerModule,
    NgxDaterangepickerMd,
    MatSliderModule,
    MatExpansionModule,
    MatStepperModule,
    MatAutocompleteModule,
    MatChipsModule,
    GridsterModule,
    ClipboardModule,
    FlexLayoutModule,
    FormsModule,
    ReactiveFormsModule,
    OverlayModule,
    ShareButtonsModule,
    HotkeyModule,
    ColorPickerModule,
    NgxHmCarouselModule,
    EditorModule,
    NgxFlowchartModule,
    ConfirmDialogComponent,
    AlertDialogComponent,
    ProgressDialogComponent,
    TodoDialogComponent,
    ColorPickerDialogComponent,
    MaterialIconsDialogComponent,
    ColorInputComponent,
    MaterialIconSelectComponent,
    NodeScriptTestDialogComponent,
    JsonFormComponent,
    ImageInputComponent,
    FileInputComponent,
    MessageTypeAutocompleteComponent,
    KeyValMapComponent,
    NavTreeComponent,
    LedLightComponent,
    NospacePipe,
    MillisecondsToTimeStringPipe,
    EnumToArrayPipe,
    HighlightPipe,
    TruncatePipe,
    TbJsonPipe,
    TbTemplatePipe,
    KeyboardShortcutPipe,
    HasGenericPermissionPipe,
    HasEntityGroupPermissionPipe,
    HasGroupEntityPermissionPipe,
    TranslateModule,
    JsonObjectEditDialogComponent,
    HistorySelectorComponent,
    EntityGatewaySelectComponent
  ]
})
export class SharedModule { }
