/**
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
package org.thingsboard.server.service.converter.js;

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.service.script.JsSandboxService;
import org.thingsboard.server.service.script.JsScriptType;

import java.util.UUID;

@Slf4j
public abstract class AbstractJSEvaluator {

    protected final JsSandboxService sandboxService;
    private final JsScriptType scriptType;
    private final String script;

    protected volatile UUID scriptId;
    private volatile boolean isErrorScript = false;


    public AbstractJSEvaluator(JsSandboxService sandboxService, JsScriptType scriptType, String script) {
        this.sandboxService = sandboxService;
        this.scriptType = scriptType;
        this.script = script;
    }

    public void destroy() {
        if (this.scriptId != null) {
            this.sandboxService.release(this.scriptId);
        }
    }

    void validateSuccessfulScriptLazyInit() {
        if (this.scriptId != null) {
            return;
        }

        if (isErrorScript) {
            throw new IllegalArgumentException("Can't compile uplink converter script ");
        }

        synchronized (this) {
            if (this.scriptId == null) {
                try {
                    this.scriptId = this.sandboxService.eval(scriptType, script).get();
                } catch (Exception e) {
                    isErrorScript = true;
                    throw new IllegalArgumentException("Can't compile script: " + e.getMessage(), e);
                }
            }
        }
    }

}
