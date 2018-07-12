/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2018 ThingsBoard, Inc.. All Rights Reserved.
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
package org.thingsboard.server.service.script;

import com.datastax.driver.core.utils.UUIDs;
import com.google.common.collect.Sets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.rule.engine.api.ScriptEngine;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;

import javax.script.ScriptException;

import java.util.Set;

import static org.junit.Assert.*;

public class RuleNodeJsScriptEngineTest {

    private ScriptEngine scriptEngine;
    private TestNashornJsSandboxService jsSandboxService;

    @Before
    public void beforeTest() throws Exception {
        jsSandboxService = new TestNashornJsSandboxService(false, 1, 100, 3);
    }

    @After
    public void afterTest() throws Exception {
        jsSandboxService.stop();
    }

    @Test
    public void msgCanBeUpdated() throws ScriptException {
        String function = "metadata.temp = metadata.temp * 10; return {metadata: metadata};";
        scriptEngine = new RuleNodeJsScriptEngine(jsSandboxService, function);

        TbMsgMetaData metaData = new TbMsgMetaData();
        metaData.putValue("temp", "7");
        metaData.putValue("humidity", "99");
        String rawJson = "{\"name\": \"Vit\", \"passed\": 5, \"bigObj\": {\"prop\":42}}";

        TbMsg msg = new TbMsg(UUIDs.timeBased(), "USER", null, metaData, rawJson, null, null, 0L);

        TbMsg actual = scriptEngine.executeUpdate(msg);
        assertEquals("70", actual.getMetaData().getValue("temp"));
        scriptEngine.destroy();
    }

    @Test
    public void newAttributesCanBeAddedInMsg() throws ScriptException {
        String function = "metadata.newAttr = metadata.humidity - msg.passed; return {metadata: metadata};";
        scriptEngine = new RuleNodeJsScriptEngine(jsSandboxService, function);
        TbMsgMetaData metaData = new TbMsgMetaData();
        metaData.putValue("temp", "7");
        metaData.putValue("humidity", "99");
        String rawJson = "{\"name\": \"Vit\", \"passed\": 5, \"bigObj\": {\"prop\":42}}";

        TbMsg msg = new TbMsg(UUIDs.timeBased(), "USER", null, metaData, rawJson, null, null, 0L);

        TbMsg actual = scriptEngine.executeUpdate(msg);
        assertEquals("94", actual.getMetaData().getValue("newAttr"));
        scriptEngine.destroy();
    }

    @Test
    public void payloadCanBeUpdated() throws ScriptException {
        String function = "msg.passed = msg.passed * metadata.temp; msg.bigObj.newProp = 'Ukraine'; return {msg: msg};";
        scriptEngine = new RuleNodeJsScriptEngine(jsSandboxService, function);
        TbMsgMetaData metaData = new TbMsgMetaData();
        metaData.putValue("temp", "7");
        metaData.putValue("humidity", "99");
        String rawJson = "{\"name\":\"Vit\",\"passed\": 5,\"bigObj\":{\"prop\":42}}";

        TbMsg msg = new TbMsg(UUIDs.timeBased(), "USER", null, metaData, rawJson, null, null, 0L);

        TbMsg actual = scriptEngine.executeUpdate(msg);

        String expectedJson = "{\"name\":\"Vit\",\"passed\":35,\"bigObj\":{\"prop\":42,\"newProp\":\"Ukraine\"}}";
        assertEquals(expectedJson, actual.getData());
        scriptEngine.destroy();
    }

    @Test
    public void metadataAccessibleForFilter() throws ScriptException {
        String function = "return metadata.humidity < 15;";
        scriptEngine = new RuleNodeJsScriptEngine(jsSandboxService, function);
        TbMsgMetaData metaData = new TbMsgMetaData();
        metaData.putValue("temp", "7");
        metaData.putValue("humidity", "99");
        String rawJson = "{\"name\": \"Vit\", \"passed\": 5, \"bigObj\": {\"prop\":42}}";

        TbMsg msg = new TbMsg(UUIDs.timeBased(), "USER", null, metaData, rawJson, null, null, 0L);
        assertFalse(scriptEngine.executeFilter(msg));
        scriptEngine.destroy();
    }

    @Test
    public void dataAccessibleForFilter() throws ScriptException {
        String function = "return msg.passed < 15 && msg.name === 'Vit' && metadata.temp == 7 && msg.bigObj.prop == 42;";
        scriptEngine = new RuleNodeJsScriptEngine(jsSandboxService, function);
        TbMsgMetaData metaData = new TbMsgMetaData();
        metaData.putValue("temp", "7");
        metaData.putValue("humidity", "99");
        String rawJson = "{\"name\": \"Vit\", \"passed\": 5, \"bigObj\": {\"prop\":42}}";

        TbMsg msg = new TbMsg(UUIDs.timeBased(), "USER", null, metaData, rawJson, null, null, 0L);
        assertTrue(scriptEngine.executeFilter(msg));
        scriptEngine.destroy();
    }

    @Test
    public void dataAccessibleForSwitch() throws ScriptException {
        String jsCode = "function nextRelation(metadata, msg) {\n" +
                "    if(msg.passed == 5 && metadata.temp == 10)\n" +
                "        return 'one'\n" +
                "    else\n" +
                "        return 'two';\n" +
                "};\n" +
                "\n" +
                "return nextRelation(metadata, msg);";
        scriptEngine = new RuleNodeJsScriptEngine(jsSandboxService, jsCode);
        TbMsgMetaData metaData = new TbMsgMetaData();
        metaData.putValue("temp", "10");
        metaData.putValue("humidity", "99");
        String rawJson = "{\"name\": \"Vit\", \"passed\": 5, \"bigObj\": {\"prop\":42}}";

        TbMsg msg = new TbMsg(UUIDs.timeBased(), "USER", null, metaData, rawJson, null, null, 0L);
        Set<String> actual = scriptEngine.executeSwitch(msg);
        assertEquals(Sets.newHashSet("one"), actual);
        scriptEngine.destroy();
    }

    @Test
    public void multipleRelationsReturnedFromSwitch() throws ScriptException {
        String jsCode = "function nextRelation(metadata, msg) {\n" +
                "    if(msg.passed == 5 && metadata.temp == 10)\n" +
                "        return ['three', 'one']\n" +
                "    else\n" +
                "        return 'two';\n" +
                "};\n" +
                "\n" +
                "return nextRelation(metadata, msg);";
        scriptEngine = new RuleNodeJsScriptEngine(jsSandboxService, jsCode);
        TbMsgMetaData metaData = new TbMsgMetaData();
        metaData.putValue("temp", "10");
        metaData.putValue("humidity", "99");
        String rawJson = "{\"name\": \"Vit\", \"passed\": 5, \"bigObj\": {\"prop\":42}}";

        TbMsg msg = new TbMsg(UUIDs.timeBased(), "USER", null, metaData, rawJson, null, null, 0L);
        Set<String> actual = scriptEngine.executeSwitch(msg);
        assertEquals(Sets.newHashSet("one", "three"), actual);
        scriptEngine.destroy();
    }

}