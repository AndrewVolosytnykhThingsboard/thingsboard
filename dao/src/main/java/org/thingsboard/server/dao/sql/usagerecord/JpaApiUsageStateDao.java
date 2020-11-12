/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.dao.sql.usagerecord;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.ApiUsageState;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.model.sql.ApiUsageStateEntity;
import org.thingsboard.server.dao.sql.JpaAbstractDao;
import org.thingsboard.server.dao.usagerecord.ApiUsageStateDao;

import java.util.UUID;

/**
 * @author Andrii Shvaika
 */
@Component
public class JpaApiUsageStateDao extends JpaAbstractDao<ApiUsageStateEntity, ApiUsageState> implements ApiUsageStateDao {

    private final ApiUsageStateRepository apiUsageStateRepository;

    public JpaApiUsageStateDao(ApiUsageStateRepository apiUsageStateRepository) {
        this.apiUsageStateRepository = apiUsageStateRepository;
    }

    @Override
    protected Class<ApiUsageStateEntity> getEntityClass() {
        return ApiUsageStateEntity.class;
    }

    @Override
    protected CrudRepository<ApiUsageStateEntity, UUID> getCrudRepository() {
        return apiUsageStateRepository;
    }

    @Override
    public ApiUsageState findTenantApiUsageState(UUID tenantId) {
        return DaoUtil.getData(apiUsageStateRepository.findByTenantId(tenantId));
    }

    @Override
    public void deleteApiUsageStateByTenantId(TenantId tenantId) {
        apiUsageStateRepository.deleteApiUsageStateByTenantId(tenantId.getId());
    }
}
