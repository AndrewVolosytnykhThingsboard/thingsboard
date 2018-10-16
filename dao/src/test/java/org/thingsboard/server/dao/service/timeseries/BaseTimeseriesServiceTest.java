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
package org.thingsboard.server.dao.service.timeseries;

import com.datastax.driver.core.utils.UUIDs;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.Aggregation;
import org.thingsboard.server.common.data.kv.BaseDeleteTsKvQuery;
import org.thingsboard.server.common.data.kv.BaseReadTsKvQuery;
import org.thingsboard.server.common.data.kv.BasicTsKvEntry;
import org.thingsboard.server.common.data.kv.BooleanDataEntry;
import org.thingsboard.server.common.data.kv.DoubleDataEntry;
import org.thingsboard.server.common.data.kv.KvEntry;
import org.thingsboard.server.common.data.kv.LongDataEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.objects.TelemetryEntityView;
import org.thingsboard.server.dao.service.AbstractServiceTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Andrew Shvayka
 */

@Slf4j
public abstract class BaseTimeseriesServiceTest extends AbstractServiceTest {

    private static final String STRING_KEY = "stringKey";
    private static final String LONG_KEY = "longKey";
    private static final String DOUBLE_KEY = "doubleKey";
    private static final String BOOLEAN_KEY = "booleanKey";

    private static final long TS = 42L;
    private static final String DESC_ORDER = "DESC";

    KvEntry stringKvEntry = new StringDataEntry(STRING_KEY, "value");
    KvEntry longKvEntry = new LongDataEntry(LONG_KEY, Long.MAX_VALUE);
    KvEntry doubleKvEntry = new DoubleDataEntry(DOUBLE_KEY, Double.MAX_VALUE);
    KvEntry booleanKvEntry = new BooleanDataEntry(BOOLEAN_KEY, Boolean.TRUE);

    private TenantId tenantId;

    @Before
    public void before() {
        Tenant tenant = new Tenant();
        tenant.setTitle("My tenant");
        Tenant savedTenant = tenantService.saveTenant(tenant);
        Assert.assertNotNull(savedTenant);
        tenantId = savedTenant.getId();
    }

    @After
    public void after() {
        tenantService.deleteTenant(tenantId);
    }

    @Test
    public void testFindAllLatest() throws Exception {
        DeviceId deviceId = new DeviceId(UUIDs.timeBased());

        saveEntries(deviceId, TS - 2);
        saveEntries(deviceId, TS - 1);
        saveEntries(deviceId, TS);

        testLatestTsAndVerify(deviceId);

        EntityView entityView = saveAndCreateEntityView(deviceId, Arrays.asList(STRING_KEY, DOUBLE_KEY, LONG_KEY, BOOLEAN_KEY));

        testLatestTsAndVerify(entityView.getId());
    }

    private void testLatestTsAndVerify(EntityId entityId) throws ExecutionException, InterruptedException {
        List<TsKvEntry> tsList = tsService.findAllLatest(entityId).get();

        assertNotNull(tsList);
        assertEquals(4, tsList.size());
        for (int i = 0; i < tsList.size(); i++) {
            assertEquals(TS, tsList.get(i).getTs());
        }

        Collections.sort(tsList, (o1, o2) -> o1.getKey().compareTo(o2.getKey()));

        List<TsKvEntry> expected = Arrays.asList(
                toTsEntry(TS, stringKvEntry),
                toTsEntry(TS, longKvEntry),
                toTsEntry(TS, doubleKvEntry),
                toTsEntry(TS, booleanKvEntry));
        Collections.sort(expected, (o1, o2) -> o1.getKey().compareTo(o2.getKey()));

        assertEquals(expected, tsList);
    }

    private EntityView saveAndCreateEntityView(DeviceId deviceId, List<String> timeseries) {
        EntityView entityView = new EntityView();
        entityView.setName("entity_view_name");
        entityView.setType("default");
        entityView.setTenantId(tenantId);
        TelemetryEntityView keys = new TelemetryEntityView();
        keys.setTimeseries(timeseries);
        entityView.setKeys(keys);
        entityView.setEntityId(deviceId);
        return entityViewService.saveEntityView(entityView);
    }

    @Test
    public void testFindLatest() throws Exception {
        DeviceId deviceId = new DeviceId(UUIDs.timeBased());

        saveEntries(deviceId, TS - 2);
        saveEntries(deviceId, TS - 1);
        saveEntries(deviceId, TS);

        List<TsKvEntry> entries = tsService.findLatest(deviceId, Collections.singleton(STRING_KEY)).get();
        Assert.assertEquals(1, entries.size());
        Assert.assertEquals(toTsEntry(TS, stringKvEntry), entries.get(0));

        EntityView entityView = saveAndCreateEntityView(deviceId, Arrays.asList(STRING_KEY));

        entries = tsService.findLatest(entityView.getId(), Collections.singleton(STRING_KEY)).get();
        Assert.assertEquals(1, entries.size());
        Assert.assertEquals(toTsEntry(TS, stringKvEntry), entries.get(0));
    }

    @Test
    public void testDeleteDeviceTsData() throws Exception {
        DeviceId deviceId = new DeviceId(UUIDs.timeBased());

        saveEntries(deviceId, 10000);
        saveEntries(deviceId, 20000);
        saveEntries(deviceId, 30000);
        saveEntries(deviceId, 40000);

        tsService.remove(deviceId, Collections.singletonList(
                new BaseDeleteTsKvQuery(STRING_KEY, 15000, 45000))).get();

        List<TsKvEntry> list = tsService.findAll(deviceId, Collections.singletonList(
                new BaseReadTsKvQuery(STRING_KEY, 5000, 45000, 10000, 10, Aggregation.NONE))).get();
        Assert.assertEquals(1, list.size());

        List<TsKvEntry> latest = tsService.findLatest(deviceId, Collections.singletonList(STRING_KEY)).get();
        Assert.assertEquals(null, latest.get(0).getValueAsString());
    }

    @Test
    public void testFindDeviceTsData() throws Exception {
        DeviceId deviceId = new DeviceId(UUIDs.timeBased());
        List<TsKvEntry> entries = new ArrayList<>();

        entries.add(save(deviceId, 5000, 100));
        entries.add(save(deviceId, 15000, 200));

        entries.add(save(deviceId, 25000, 300));
        entries.add(save(deviceId, 35000, 400));

        entries.add(save(deviceId, 45000, 500));
        entries.add(save(deviceId, 55000, 600));

        List<TsKvEntry> list = tsService.findAll(deviceId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0,
                60000, 20000, 3, Aggregation.NONE))).get();
        assertEquals(3, list.size());
        assertEquals(55000, list.get(0).getTs());
        assertEquals(java.util.Optional.of(600L), list.get(0).getLongValue());

        assertEquals(45000, list.get(1).getTs());
        assertEquals(java.util.Optional.of(500L), list.get(1).getLongValue());

        assertEquals(35000, list.get(2).getTs());
        assertEquals(java.util.Optional.of(400L), list.get(2).getLongValue());

        list = tsService.findAll(deviceId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0,
                60000, 20000, 3, Aggregation.AVG))).get();
        assertEquals(3, list.size());
        assertEquals(10000, list.get(0).getTs());
        assertEquals(java.util.Optional.of(150L), list.get(0).getLongValue());

        assertEquals(30000, list.get(1).getTs());
        assertEquals(java.util.Optional.of(350L), list.get(1).getLongValue());

        assertEquals(50000, list.get(2).getTs());
        assertEquals(java.util.Optional.of(550L), list.get(2).getLongValue());

        list = tsService.findAll(deviceId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0,
                60000, 20000, 3, Aggregation.SUM))).get();

        assertEquals(3, list.size());
        assertEquals(10000, list.get(0).getTs());
        assertEquals(java.util.Optional.of(300L), list.get(0).getLongValue());

        assertEquals(30000, list.get(1).getTs());
        assertEquals(java.util.Optional.of(700L), list.get(1).getLongValue());

        assertEquals(50000, list.get(2).getTs());
        assertEquals(java.util.Optional.of(1100L), list.get(2).getLongValue());

        list = tsService.findAll(deviceId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0,
                60000, 20000, 3, Aggregation.MIN))).get();

        assertEquals(3, list.size());
        assertEquals(10000, list.get(0).getTs());
        assertEquals(java.util.Optional.of(100L), list.get(0).getLongValue());

        assertEquals(30000, list.get(1).getTs());
        assertEquals(java.util.Optional.of(300L), list.get(1).getLongValue());

        assertEquals(50000, list.get(2).getTs());
        assertEquals(java.util.Optional.of(500L), list.get(2).getLongValue());

        list = tsService.findAll(deviceId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0,
                60000, 20000, 3, Aggregation.MAX))).get();

        assertEquals(3, list.size());
        assertEquals(10000, list.get(0).getTs());
        assertEquals(java.util.Optional.of(200L), list.get(0).getLongValue());

        assertEquals(30000, list.get(1).getTs());
        assertEquals(java.util.Optional.of(400L), list.get(1).getLongValue());

        assertEquals(50000, list.get(2).getTs());
        assertEquals(java.util.Optional.of(600L), list.get(2).getLongValue());

        list = tsService.findAll(deviceId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0,
                60000, 20000, 3, Aggregation.COUNT))).get();

        assertEquals(3, list.size());
        assertEquals(10000, list.get(0).getTs());
        assertEquals(java.util.Optional.of(2L), list.get(0).getLongValue());

        assertEquals(30000, list.get(1).getTs());
        assertEquals(java.util.Optional.of(2L), list.get(1).getLongValue());

        assertEquals(50000, list.get(2).getTs());
        assertEquals(java.util.Optional.of(2L), list.get(2).getLongValue());
    }

    private TsKvEntry save(DeviceId deviceId, long ts, long value) throws Exception {
        TsKvEntry entry = new BasicTsKvEntry(ts, new LongDataEntry(LONG_KEY, value));
        tsService.save(deviceId, entry).get();
        return entry;
    }

    private void saveEntries(DeviceId deviceId, long ts) throws ExecutionException, InterruptedException {
        tsService.save(deviceId, toTsEntry(ts, stringKvEntry)).get();
        tsService.save(deviceId, toTsEntry(ts, longKvEntry)).get();
        tsService.save(deviceId, toTsEntry(ts, doubleKvEntry)).get();
        tsService.save(deviceId, toTsEntry(ts, booleanKvEntry)).get();
    }

    private static TsKvEntry toTsEntry(long ts, KvEntry entry) {
        return new BasicTsKvEntry(ts, entry);
    }


}
