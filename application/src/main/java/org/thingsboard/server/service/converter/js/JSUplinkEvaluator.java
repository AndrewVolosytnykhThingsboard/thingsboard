/**
 * Thingsboard OÜ ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2017 Thingsboard OÜ. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of Thingsboard OÜ and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Thingsboard OÜ
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

import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.service.converter.UplinkMetaData;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

@Slf4j
public class JSUplinkEvaluator {

    private static final String JS_WRAPPER_PREFIX_TEMPLATE = "function %s(bytes, metadata) { " +
            "    var payload = convertBytes(bytes); " +
            "    return JSON.stringify(Decoder(payload, metadata));" +
            "    function Decoder(payload, metadata) {";

    private static final String JS_WRAPPER_SUFIX = "}" +
            "    function convertBytes(bytes) {\n" +
            "       var payload = [];\n" +
            "       for (var i = 0; i < bytes.length; i++) {\n" +
            "           payload.push(bytes[i]);\n" +
            "       }\n" +
            "       return payload;\n" +
            "    }\n" +
            "\n}";

    private static NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
    private static ScriptEngine engine = factory.getScriptEngine("--no-java");

    private final String functionName;

    public JSUplinkEvaluator(String decoder) {
        this.functionName = "decodeInternal" + this.hashCode();
        String jsWrapperPerfix = String.format(JS_WRAPPER_PREFIX_TEMPLATE, this.functionName);
        compileScript(jsWrapperPerfix
                + decoder
                + JS_WRAPPER_SUFIX);
    }

    public void destroy() {
        //engine = null;
    }

    public String execute(byte[] data, UplinkMetaData metadata) throws ScriptException, NoSuchMethodException {
        return ((Invocable)engine).invokeFunction(this.functionName, data, metadata.getKvMap()).toString();
    }

    private static void compileScript(String script) {
        try {
            engine.eval(script);
        } catch (ScriptException e) {
            log.warn("Failed to compile filter script: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Can't compile script: " + e.getMessage());
        }
    }

}
