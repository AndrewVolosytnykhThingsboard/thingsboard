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
package org.thingsboard.server.dao.integration;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.EntitySubtype;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.integration.IntegrationType;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.dao.model.nosql.IntegrationEntity;
import org.thingsboard.server.dao.nosql.CassandraAbstractSearchTextDao;
import org.thingsboard.server.dao.util.NoSqlDao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
@NoSqlDao
public class CassandraIntegrationDao extends CassandraAbstractSearchTextDao<IntegrationEntity, Integration> implements IntegrationDao {



    @Override
    protected Class<IntegrationEntity> getColumnFamilyClass() {
        return null;
    }

    @Override
    protected String getColumnFamilyName() {
        return null;
    }

    @Override
    public Integration save(Integration integration) {
        return null;
    }

    @Override
    public List<Integration> findIntegrationsByTenantId(UUID tenantId, TextPageLink pageLink) {
        return null;
    }

    @Override
    public List<Integration> findIntegrationsByTenantIdAndType(UUID tenantId, IntegrationType type, TextPageLink pageLink) {
        return null;
    }

    @Override
    public ListenableFuture<List<Integration>> findIntegrationsByTenantIdAndIdsAsync(UUID tenantId, List<UUID> integrationIds) {
        return null;
    }

    @Override
    public List<Integration> findIntegrationsByTenantIdAndConverterId(UUID tenantId, UUID defaultConverterId, TextPageLink pageLink) {
        return null;
    }

    @Override
    public List<Integration> findIntegrationsByTenantIdAndConverterIdAndType(UUID tenantId, UUID defaultConverterId, IntegrationType type, TextPageLink pageLink) {
        return null;
    }

    @Override
    public ListenableFuture<List<Integration>> findIntegrationsByTenantIdAndConverterIdAndIdsAsync(UUID tenantId, UUID defaultConverterId, List<UUID> integrationIds) {
        return null;
    }

    @Override
    public Optional<Integration> findIntegrationsByTenantIdAndRoutingKey(UUID tenantId, String routingKey) {
        return null;
    }

    @Override
    public ListenableFuture<List<EntitySubtype>> findTenantIntegrationTypesAsync(UUID tenantId) {
        return null;
    }
}
