/*
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2018 ThingsBoard, Inc. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 *
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes
 * information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
 * OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
 * THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
 * DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
 * OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
 */

/*@ngInject*/
export default function CustomTranslationController($scope, $translate, utils, toast, types, customTranslationService) {

    var vm = this;

    vm.types = types;
    vm.customTranslation = {};
    vm.customTranslation.translationMap = {};
    vm.save = save;

    vm.languageList = SUPPORTED_LANGS; //eslint-disable-line

    vm.currentLang = vm.languageList[0];

    vm.showError = function (error) {
        var toastParent = angular.element('#tb-custom-translation-panel');
        toast.showError(error, toastParent, 'top left');
    };

    getCurrentCustomTranslation();

    function getCurrentCustomTranslation() {
        var loadPromise = customTranslationService.getCurrentCustomTranslation();
        loadPromise.then(
            function success(customTranslation) {
                vm.customTranslation = customTranslation;
            });
    }

    function validate() {
        for (var lang in vm.customTranslation.translationMap) {
            var translation = vm.customTranslation.translationMap[lang];
            if (translation) {
                try {
                    angular.fromJson(translation);
                } catch (e) {
                    var details = utils.parseException(e);
                    var errorInfo = 'Error parsing JSON for ' + $translate.instant('language.locales.' + lang) + ' language:';
                    if (details.name) {
                        errorInfo += ' ' + details.name + ':';
                    }
                    if (details.message) {
                        errorInfo += ' ' + details.message;
                    }
                    vm.showError(errorInfo);
                    return false;
                }
            } else {
                delete vm.customTranslation.translationMap[lang];
            }
        }
        return true;
    }

    function save() {
        if (validate()) {
            var savePromise = customTranslationService.saveCustomTranslation(vm.customTranslation);
            savePromise.then(
                function success() {
                    vm.customTranslationForm.$setPristine();
                });
        }
    }
}