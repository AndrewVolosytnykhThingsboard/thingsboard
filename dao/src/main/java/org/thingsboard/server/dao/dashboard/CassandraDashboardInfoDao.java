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
package org.thingsboard.server.dao.dashboard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.DashboardInfo;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.model.nosql.DashboardInfoEntity;
import org.thingsboard.server.dao.nosql.CassandraAbstractSearchTextDao;
import org.thingsboard.server.dao.util.NoSqlDao;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static org.thingsboard.server.dao.model.ModelConstants.*;

@Component
@Slf4j
@NoSqlDao
public class CassandraDashboardInfoDao extends CassandraAbstractSearchTextDao<DashboardInfoEntity, DashboardInfo> implements DashboardInfoDao {

    @Override
    protected Class<DashboardInfoEntity> getColumnFamilyClass() {
        return DashboardInfoEntity.class;
    }

    @Override
    protected String getColumnFamilyName() {
        return DASHBOARD_COLUMN_FAMILY_NAME;
    }

    @Override
    public List<DashboardInfo> findDashboardsByTenantId(UUID tenantId, TextPageLink pageLink) {
        log.debug("Try to find dashboards by tenantId [{}] and pageLink [{}]", tenantId, pageLink);
        List<DashboardInfoEntity> dashboardEntities = findPageWithTextSearch(DASHBOARD_BY_TENANT_AND_SEARCH_TEXT_COLUMN_FAMILY_NAME,
                Collections.singletonList(eq(DASHBOARD_TENANT_ID_PROPERTY, tenantId)),
                pageLink);

        log.trace("Found dashboards [{}] by tenantId [{}] and pageLink [{}]", dashboardEntities, tenantId, pageLink);
        return DaoUtil.convertDataList(dashboardEntities);
    }

    @Override
    public List<DashboardInfo> findDashboardsByTenantIdAndCustomerId(UUID tenantId, UUID customerId, TextPageLink pageLink) {
        log.debug("Try to find dashboards by tenantId [{}], customerId[{}] and pageLink [{}]", tenantId, customerId, pageLink);
        List<DashboardInfoEntity> dashboardEntities = findPageWithTextSearch(DASHBOARD_BY_CUSTOMER_AND_SEARCH_TEXT_COLUMN_FAMILY_NAME,
                Arrays.asList(eq(DASHBOARD_CUSTOMER_ID_PROPERTY, customerId),
                        eq(DASHBOARD_TENANT_ID_PROPERTY, tenantId)),
                pageLink);

        log.trace("Found dashboards [{}] by tenantId [{}], customerId [{}] and pageLink [{}]", dashboardEntities, tenantId, customerId, pageLink);
        return DaoUtil.convertDataList(dashboardEntities);
    }

}
