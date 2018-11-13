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
package org.thingsboard.server.dao.sql.widget;

import com.datastax.driver.core.utils.UUIDs;
import com.github.springtestdbunit.annotation.DatabaseOperation;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DatabaseTearDown;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.WidgetsBundleId;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.widget.WidgetsBundle;
import org.thingsboard.server.dao.AbstractJpaDaoTest;
import org.thingsboard.server.dao.service.AbstractServiceTest;
import org.thingsboard.server.dao.widget.WidgetsBundleDao;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.thingsboard.server.dao.model.ModelConstants.NULL_UUID;

/**
 * Created by Valerii Sosliuk on 4/23/2017.
 */
public class JpaWidgetsBundleDaoTest extends AbstractJpaDaoTest {

    @Autowired
    private WidgetsBundleDao widgetsBundleDao;

    @Test
    @DatabaseSetup(value = "classpath:dbunit/widgets_bundle.xml",type= DatabaseOperation.CLEAN_INSERT)
    @DatabaseTearDown(value = "classpath:dbunit/widgets_bundle.xml", type= DatabaseOperation.DELETE_ALL)
    public void testFindAll() {
        assertEquals(7, widgetsBundleDao.find(AbstractServiceTest.SYSTEM_TENANT_ID).size());
    }

    @Test
    @DatabaseSetup(value = "classpath:dbunit/widgets_bundle.xml",type= DatabaseOperation.CLEAN_INSERT)
    @DatabaseTearDown(value = "classpath:dbunit/widgets_bundle.xml", type= DatabaseOperation.DELETE_ALL)
    public void testFindWidgetsBundleByTenantIdAndAlias() {
        WidgetsBundle widgetsBundle = widgetsBundleDao.findWidgetsBundleByTenantIdAndAlias(
                UUID.fromString("250aca8e-2825-11e7-93ae-92361f002671"), "WB3");
        assertEquals("44e6af4e-2825-11e7-93ae-92361f002671", widgetsBundle.getId().toString());
    }

    @Test
    @DatabaseSetup(value = "classpath:dbunit/widgets_bundle.xml", type= DatabaseOperation.DELETE_ALL)
    public void testFindSystemWidgetsBundles() {
        createSystemWidgetBundles(30, "WB_");
        assertEquals(30, widgetsBundleDao.find(AbstractServiceTest.SYSTEM_TENANT_ID).size());
        // Get first page
        TextPageLink textPageLink1 = new TextPageLink(10, "WB");
        List<WidgetsBundle> widgetsBundles1 = widgetsBundleDao.findSystemWidgetsBundles(AbstractServiceTest.SYSTEM_TENANT_ID, textPageLink1);
        assertEquals(10, widgetsBundles1.size());
        // Get next page
        TextPageLink textPageLink2 = new TextPageLink(10, "WB", widgetsBundles1.get(9).getId().getId(), null);
        List<WidgetsBundle> widgetsBundles2 = widgetsBundleDao.findSystemWidgetsBundles(AbstractServiceTest.SYSTEM_TENANT_ID, textPageLink2);
        assertEquals(10, widgetsBundles2.size());
    }

    @Test
    @DatabaseSetup(value = "classpath:dbunit/widgets_bundle.xml", type= DatabaseOperation.DELETE_ALL)
    public void testFindWidgetsBundlesByTenantId() {
        UUID tenantId1 = UUIDs.timeBased();
        UUID tenantId2 = UUIDs.timeBased();
        // Create a bunch of widgetBundles
        for (int i= 0; i < 10; i++) {
            createWidgetBundles(3, tenantId1, "WB1_");
            createWidgetBundles(5, tenantId2, "WB2_");
            createSystemWidgetBundles(10, "WB_SYS_");
        }
        assertEquals(180, widgetsBundleDao.find(AbstractServiceTest.SYSTEM_TENANT_ID).size());

        TextPageLink textPageLink1 = new TextPageLink(40, "WB");
        List<WidgetsBundle> widgetsBundles1 = widgetsBundleDao.findTenantWidgetsBundlesByTenantId(tenantId1, textPageLink1);
        assertEquals(30, widgetsBundles1.size());

        TextPageLink textPageLink2 = new TextPageLink(40, "WB");
        List<WidgetsBundle> widgetsBundles2 = widgetsBundleDao.findTenantWidgetsBundlesByTenantId(tenantId2, textPageLink2);
        assertEquals(40, widgetsBundles2.size());

        TextPageLink textPageLink3 = new TextPageLink(40, "WB",
                widgetsBundles2.get(39).getId().getId(), null);
        List<WidgetsBundle> widgetsBundles3 = widgetsBundleDao.findTenantWidgetsBundlesByTenantId(tenantId2, textPageLink3);
        assertEquals(10, widgetsBundles3.size());
    }

    @Test
    @DatabaseSetup(value = "classpath:dbunit/widgets_bundle.xml", type= DatabaseOperation.DELETE_ALL)
    public void testFindAllWidgetsBundlesByTenantId() {
        UUID tenantId1 = UUIDs.timeBased();
        UUID tenantId2 = UUIDs.timeBased();
        // Create a bunch of widgetBundles
        for (int i= 0; i < 10; i++) {
            createWidgetBundles( 5, tenantId1,"WB1_");
            createWidgetBundles(3, tenantId2, "WB2_");
            createSystemWidgetBundles(2, "WB_SYS_");
        }

        TextPageLink textPageLink1 = new TextPageLink(30, "WB");
        List<WidgetsBundle> widgetsBundles1 = widgetsBundleDao.findAllTenantWidgetsBundlesByTenantId(tenantId1, textPageLink1);
        assertEquals(30, widgetsBundles1.size());

        TextPageLink textPageLink2 = new TextPageLink(30, "WB",
                widgetsBundles1.get(29).getId().getId(), null);
        List<WidgetsBundle> widgetsBundles2 = widgetsBundleDao.findAllTenantWidgetsBundlesByTenantId(tenantId1, textPageLink2);

        assertEquals(30, widgetsBundles2.size());

        TextPageLink textPageLink3 = new TextPageLink(30, "WB",
                widgetsBundles2.get(29).getId().getId(), null);
        List<WidgetsBundle> widgetsBundles3 = widgetsBundleDao.findAllTenantWidgetsBundlesByTenantId(tenantId1, textPageLink3);
        assertEquals(10, widgetsBundles3.size());

        TextPageLink textPageLink4 = new TextPageLink(30, "WB",
                widgetsBundles3.get(9).getId().getId(), null);
        List<WidgetsBundle> widgetsBundles4 = widgetsBundleDao.findAllTenantWidgetsBundlesByTenantId(tenantId1, textPageLink4);
        assertEquals(0, widgetsBundles4.size());
    }

    @Test
    @DatabaseSetup("classpath:dbunit/empty_dataset.xml")
    @DatabaseTearDown(value = "classpath:dbunit/empty_dataset.xml", type= DatabaseOperation.DELETE_ALL)
    public void testSearchTextNotFound() {
        UUID tenantId = UUIDs.timeBased();
        createWidgetBundles(5, tenantId, "ABC_");
        createSystemWidgetBundles(5, "SYS_");

        TextPageLink textPageLink = new TextPageLink(30, "TEXT_NOT_FOUND");
        List<WidgetsBundle> widgetsBundles4 = widgetsBundleDao.findAllTenantWidgetsBundlesByTenantId(tenantId, textPageLink);
        assertEquals(0, widgetsBundles4.size());
    }

    private void createWidgetBundles(int count, UUID tenantId, String prefix) {
        for (int i = 0; i < count; i++) {
            WidgetsBundle widgetsBundle = new WidgetsBundle();
            widgetsBundle.setAlias(prefix + i);
            widgetsBundle.setTitle(prefix + i);
            widgetsBundle.setId(new WidgetsBundleId(UUIDs.timeBased()));
            widgetsBundle.setTenantId(new TenantId(tenantId));
            widgetsBundleDao.save(AbstractServiceTest.SYSTEM_TENANT_ID, widgetsBundle);
        }
    }
    private void createSystemWidgetBundles(int count, String prefix) {
        for (int i = 0; i < count; i++) {
            WidgetsBundle widgetsBundle = new WidgetsBundle();
            widgetsBundle.setAlias(prefix + i);
            widgetsBundle.setTitle(prefix + i);
            widgetsBundle.setTenantId(new TenantId(NULL_UUID));
            widgetsBundle.setId(new WidgetsBundleId(UUIDs.timeBased()));
            widgetsBundleDao.save(AbstractServiceTest.SYSTEM_TENANT_ID, widgetsBundle);
        }
    }
}
