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
package org.thingsboard.server.dao.model.sql;

import com.datastax.driver.core.utils.UUIDs;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.model.BaseSqlEntity;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.dao.model.SearchTextEntity;
import org.thingsboard.server.dao.util.mapping.JsonStringType;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@TypeDef(name = "json", typeClass = JsonStringType.class)
@Table(name = ModelConstants.DASHBOARD_COLUMN_FAMILY_NAME)
public final class DashboardEntity extends BaseSqlEntity<Dashboard> implements SearchTextEntity<Dashboard> {

    @Transient
    private static final long serialVersionUID = -4838084363113078898L;

    @Column(name = ModelConstants.DASHBOARD_TENANT_ID_PROPERTY)
    private String tenantId;

    @Column(name = ModelConstants.DASHBOARD_CUSTOMER_ID_PROPERTY)
    private String customerId;

    @Column(name = ModelConstants.DASHBOARD_TITLE_PROPERTY)
    private String title;
    
    @Column(name = ModelConstants.SEARCH_TEXT_PROPERTY)
    private String searchText;

    @Type(type = "json")
    @Column(name = ModelConstants.DASHBOARD_CONFIGURATION_PROPERTY)
    private JsonNode configuration;

    public DashboardEntity() {
        super();
    }

    public DashboardEntity(Dashboard dashboard) {
        if (dashboard.getId() != null) {
            this.setId(dashboard.getId().getId());
        }
        if (dashboard.getTenantId() != null) {
            this.tenantId = toString(dashboard.getTenantId().getId());
        }
        if (dashboard.getCustomerId() != null) {
            this.customerId = toString(dashboard.getCustomerId().getId());
        }
        this.title = dashboard.getTitle();
        this.configuration = dashboard.getConfiguration();
    }

    @Override
    public String getSearchTextSource() {
        return title;
    }

    @Override
    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    @Override
    public Dashboard toData() {
        Dashboard dashboard = new Dashboard(new DashboardId(this.getId()));
        dashboard.setCreatedTime(UUIDs.unixTimestamp(this.getId()));
        if (tenantId != null) {
            dashboard.setTenantId(new TenantId(toUUID(tenantId)));
        }
        if (customerId != null) {
            dashboard.setCustomerId(new CustomerId(toUUID(customerId)));
        }
        dashboard.setTitle(title);
        dashboard.setConfiguration(configuration);
        return dashboard;
    }
}