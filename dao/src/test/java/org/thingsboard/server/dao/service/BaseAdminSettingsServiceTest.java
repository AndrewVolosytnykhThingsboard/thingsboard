/**
 * Thingsboard OÜ ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2018 Thingsboard OÜ. All Rights Reserved.
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
package org.thingsboard.server.dao.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Assert;
import org.junit.Test;
import org.thingsboard.server.common.data.AdminSettings;
import org.thingsboard.server.dao.exception.DataValidationException;

public abstract class BaseAdminSettingsServiceTest extends AbstractServiceTest {

    @Test
    public void testFindAdminSettingsByKey() {
        AdminSettings adminSettings = adminSettingsService.findAdminSettingsByKey("general");
        Assert.assertNotNull(adminSettings);
        adminSettings = adminSettingsService.findAdminSettingsByKey("mail");
        Assert.assertNotNull(adminSettings);
        adminSettings = adminSettingsService.findAdminSettingsByKey("unknown");
        Assert.assertNull(adminSettings);
    }
    
    @Test
    public void testFindAdminSettingsById() {
        AdminSettings adminSettings = adminSettingsService.findAdminSettingsByKey("general");
        AdminSettings foundAdminSettings = adminSettingsService.findAdminSettingsById(adminSettings.getId());
        Assert.assertNotNull(foundAdminSettings);
        Assert.assertEquals(adminSettings, foundAdminSettings);
    }
    
    @Test
    public void testSaveAdminSettings() throws Exception {
        AdminSettings adminSettings = adminSettingsService.findAdminSettingsByKey("general");
        JsonNode json = adminSettings.getJsonValue();
        ((ObjectNode) json).put("baseUrl", "http://myhost.org");
        adminSettings.setJsonValue(json);
        adminSettingsService.saveAdminSettings(adminSettings);
        AdminSettings savedAdminSettings = adminSettingsService.findAdminSettingsByKey("general");
        Assert.assertNotNull(savedAdminSettings);
        Assert.assertEquals(adminSettings.getJsonValue(), savedAdminSettings.getJsonValue());
    }
    
    @Test(expected = DataValidationException.class)
    public void testSaveAdminSettingsWithEmptyKey() {
        AdminSettings adminSettings = adminSettingsService.findAdminSettingsByKey("mail");
        adminSettings.setKey(null);
        adminSettingsService.saveAdminSettings(adminSettings);
    }
    
    @Test(expected = DataValidationException.class)
    public void testChangeAdminSettingsKey() {
        AdminSettings adminSettings = adminSettingsService.findAdminSettingsByKey("mail");
        adminSettings.setKey("newKey");
        adminSettingsService.saveAdminSettings(adminSettings);
    }
    
    @Test(expected = DataValidationException.class)
    public void testSaveAdminSettingsWithNewJsonStructure() throws Exception {
        AdminSettings adminSettings = adminSettingsService.findAdminSettingsByKey("mail");
        JsonNode json = adminSettings.getJsonValue();
        ((ObjectNode) json).put("newKey", "my new value");
        adminSettings.setJsonValue(json);
        adminSettingsService.saveAdminSettings(adminSettings);
    }
    
    @Test(expected = DataValidationException.class)
    public void testSaveAdminSettingsWithNonTextValue() throws Exception {
        AdminSettings adminSettings = adminSettingsService.findAdminSettingsByKey("mail");
        JsonNode json = adminSettings.getJsonValue();
        ((ObjectNode) json).put("timeout", 10000L);
        adminSettings.setJsonValue(json);
        adminSettingsService.saveAdminSettings(adminSettings);
    }
}
