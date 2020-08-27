!function(e,t){"object"==typeof exports&&"undefined"!=typeof module?t(exports,require("@angular/core"),require("@angular/common"),require("@ngx-translate/core"),require("@shared/public-api"),require("@home/components/public-api"),require("@ngrx/store"),require("@angular/forms")):"function"==typeof define&&define.amd?define("twilio-sms-config",["exports","@angular/core","@angular/common","@ngx-translate/core","@shared/public-api","@home/components/public-api","@ngrx/store","@angular/forms"],t):t((e=e||self)["twilio-sms-config"]={},e.ng.core,e.ng.common,e["ngx-translate"],e.shared,e.publicApi$1,e["ngrx-store"],e.ng.forms)}(this,(function(e,t,r,o,n,i,a,m){"use strict";
/*! *****************************************************************************
    Copyright (c) Microsoft Corporation.

    Permission to use, copy, modify, and/or distribute this software for any
    purpose with or without fee is hereby granted.

    THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
    REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
    AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
    INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
    LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
    OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
    PERFORMANCE OF THIS SOFTWARE.
    ***************************************************************************** */var u=function(e,t){return(u=Object.setPrototypeOf||{__proto__:[]}instanceof Array&&function(e,t){e.__proto__=t}||function(e,t){for(var r in t)Object.prototype.hasOwnProperty.call(t,r)&&(e[r]=t[r])})(e,t)};function s(e,t,r,o){var n,i=arguments.length,a=i<3?t:null===o?o=Object.getOwnPropertyDescriptor(t,r):o;if("object"==typeof Reflect&&"function"==typeof Reflect.decorate)a=Reflect.decorate(e,t,r,o);else for(var m=e.length-1;m>=0;m--)(n=e[m])&&(a=(i<3?n(a):i>3?n(t,r,a):n(t,r))||a);return i>3&&a&&Object.defineProperty(t,r,a),a}function l(e,t){if("object"==typeof Reflect&&"function"==typeof Reflect.metadata)return Reflect.metadata(e,t)}Object.create;Object.create;var c=function(e){function r(t,r){var o=e.call(this,t)||this;return o.store=t,o.fb=r,o}return function(e,t){function r(){this.constructor=e}u(e,t),e.prototype=null===t?Object.create(t):(r.prototype=t.prototype,new r)}(r,e),r.prototype.configForm=function(){return this.twilioSmsConfigForm},r.prototype.onConfigurationSet=function(e){this.twilioSmsConfigForm=this.fb.group({numberFrom:[e?e.numberFrom:null,[m.Validators.required]],numbersTo:[e?e.numbersTo:null,[m.Validators.required]],accountSid:[e?e.accountSid:null,[m.Validators.required]],accountToken:[e?e.accountToken:null,[m.Validators.required]]})},r.ctorParameters=function(){return[{type:a.Store},{type:m.FormBuilder}]},r=s([t.Component({selector:"tb-action-node-twilio-sms-config",template:'<section [formGroup]="twilioSmsConfigForm" fxLayout="column">\n  <mat-form-field class="mat-block">\n    <mat-label translate>tb.twilio-sms.number-from</mat-label>\n    <input required matInput formControlName="numberFrom">\n    <mat-error *ngIf="twilioSmsConfigForm.get(\'numberFrom\').hasError(\'required\')">\n      {{ \'tb.twilio-sms.number-from-required\' | translate }}\n    </mat-error>\n    <mat-hint innerHTML="{{ \'tb.twilio-sms.number-from-hint\' | translate }}"></mat-hint>\n  </mat-form-field>\n  <mat-form-field class="mat-block">\n    <mat-label translate>tb.twilio-sms.numbers-to</mat-label>\n    <input required matInput formControlName="numbersTo">\n    <mat-error *ngIf="twilioSmsConfigForm.get(\'numbersTo\').hasError(\'required\')">\n      {{ \'tb.twilio-sms.numbers-to-required\' | translate }}\n    </mat-error>\n    <mat-hint innerHTML="{{ \'tb.twilio-sms.numbers-to-hint\' | translate }}"></mat-hint>\n  </mat-form-field>\n  <mat-form-field class="mat-block">\n    <mat-label translate>tb.twilio-sms.account-sid</mat-label>\n    <input required matInput formControlName="accountSid">\n    <mat-error *ngIf="twilioSmsConfigForm.get(\'accountSid\').hasError(\'required\')">\n      {{ \'tb.twilio-sms.account-sid-required\' | translate }}\n    </mat-error>\n  </mat-form-field>\n  <mat-form-field class="mat-block">\n    <mat-label translate>tb.twilio-sms.account-token</mat-label>\n    <input required type="password" matInput formControlName="accountToken">\n    <mat-error *ngIf="twilioSmsConfigForm.get(\'accountToken\').hasError(\'required\')">\n      {{ \'tb.twilio-sms.account-token-required\' | translate }}\n    </mat-error>\n  </mat-form-field>\n</section>\n'}),l("design:paramtypes",[a.Store,m.FormBuilder])],r)}(n.RuleNodeConfigurationComponent),f=function(){function e(e){!function(e){e.setTranslation("en_US",{tb:{"twilio-sms":{"number-from":"Phone Number From","number-from-required":"Phone Number From is required.","number-from-hint":"Phone Number From pattern, use <code>${metaKeyName}</code> to substitute variables from metadata","numbers-to":"Phone Numbers To","numbers-to-required":"Phone Numbers To is required.","numbers-to-hint":"Comma separated Phone Numbers, use <code>${metaKeyName}</code> to substitute variables from metadata","account-sid":"Twilio Account SID","account-sid-required":"Twilio Account SID is required","account-token":"Twilio Account Token","account-token-required":"Twilio Account Token is required"}}},!0)}(e)}return e.ctorParameters=function(){return[{type:o.TranslateService}]},e=s([t.NgModule({declarations:[c],imports:[r.CommonModule,n.SharedModule,i.HomeComponentsModule],exports:[c]}),l("design:paramtypes",[o.TranslateService])],e)}();e.TwilioSmsConfigModule=f,e.ɵa=c,Object.defineProperty(e,"__esModule",{value:!0})}));
//# sourceMappingURL=twilio-sms-config.umd.min.js.map