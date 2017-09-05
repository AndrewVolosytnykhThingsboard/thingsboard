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
package org.thingsboard.server.dao.model.nosql;

import com.datastax.driver.core.utils.UUIDs;
import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Table;
import com.datastax.driver.mapping.annotations.Transient;
import com.fasterxml.jackson.databind.JsonNode;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.WidgetTypeId;
import org.thingsboard.server.common.data.widget.WidgetType;
import org.thingsboard.server.dao.model.BaseEntity;
import org.thingsboard.server.dao.model.type.JsonCodec;

import java.util.UUID;

import static org.thingsboard.server.dao.model.ModelConstants.*;

@Table(name = WIDGET_TYPE_COLUMN_FAMILY_NAME)
public final class WidgetTypeEntity implements BaseEntity<WidgetType> {

    @Transient
    private static final long serialVersionUID = 3591054897680176342L;

    @PartitionKey(value = 0)
    @Column(name = ID_PROPERTY)
    private UUID id;

    @PartitionKey(value = 1)
    @Column(name = WIDGET_TYPE_TENANT_ID_PROPERTY)
    private UUID tenantId;

    @PartitionKey(value = 2)
    @Column(name = WIDGET_TYPE_BUNDLE_ALIAS_PROPERTY)
    private String bundleAlias;

    @Column(name = WIDGET_TYPE_ALIAS_PROPERTY)
    private String alias;

    @Column(name = WIDGET_TYPE_NAME_PROPERTY)
    private String name;

    @Column(name = WIDGET_TYPE_DESCRIPTOR_PROPERTY, codec = JsonCodec.class)
    private JsonNode descriptor;

    public WidgetTypeEntity() {
        super();
    }

    public WidgetTypeEntity(WidgetType widgetType) {
        if (widgetType.getId() != null) {
            this.id = widgetType.getId().getId();
        }
        if (widgetType.getTenantId() != null) {
            this.tenantId = widgetType.getTenantId().getId();
        }
        this.bundleAlias = widgetType.getBundleAlias();
        this.alias = widgetType.getAlias();
        this.name = widgetType.getName();
        this.descriptor = widgetType.getDescriptor();
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getBundleAlias() {
        return bundleAlias;
    }

    public void setBundleAlias(String bundleAlias) {
        this.bundleAlias = bundleAlias;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public JsonNode getDescriptor() {
        return descriptor;
    }

    public void setDescriptor(JsonNode descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (tenantId != null ? tenantId.hashCode() : 0);
        result = 31 * result + (bundleAlias != null ? bundleAlias.hashCode() : 0);
        result = 31 * result + (alias != null ? alias.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (descriptor != null ? descriptor.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        WidgetTypeEntity that = (WidgetTypeEntity) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (tenantId != null ? !tenantId.equals(that.tenantId) : that.tenantId != null) return false;
        if (bundleAlias != null ? !bundleAlias.equals(that.bundleAlias) : that.bundleAlias != null) return false;
        if (alias != null ? !alias.equals(that.alias) : that.alias != null) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        return descriptor != null ? descriptor.equals(that.descriptor) : that.descriptor == null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("WidgetTypeEntity{");
        sb.append("id=").append(id);
        sb.append(", tenantId=").append(tenantId);
        sb.append(", bundleAlias='").append(bundleAlias).append('\'');
        sb.append(", alias='").append(alias).append('\'');
        sb.append(", name ='").append(name).append('\'');
        sb.append(", descriptor=").append(descriptor);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public WidgetType toData() {
        WidgetType widgetType = new WidgetType(new WidgetTypeId(id));
        widgetType.setCreatedTime(UUIDs.unixTimestamp(id));
        if (tenantId != null) {
            widgetType.setTenantId(new TenantId(tenantId));
        }
        widgetType.setBundleAlias(bundleAlias);
        widgetType.setAlias(alias);
        widgetType.setName(name);
        widgetType.setDescriptor(descriptor);
        return widgetType;
    }

}
