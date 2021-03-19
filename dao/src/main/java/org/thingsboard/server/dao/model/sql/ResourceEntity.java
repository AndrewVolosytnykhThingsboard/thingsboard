/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.dao.model.sql;

import lombok.Data;
import org.thingsboard.server.common.data.Resource;
import org.thingsboard.server.common.data.ResourceType;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.model.ToData;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;
import java.util.UUID;

import static org.thingsboard.server.dao.model.ModelConstants.RESOURCE_ID_COLUMN;
import static org.thingsboard.server.dao.model.ModelConstants.RESOURCE_TABLE_NAME;
import static org.thingsboard.server.dao.model.ModelConstants.RESOURCE_TENANT_ID_COLUMN;
import static org.thingsboard.server.dao.model.ModelConstants.RESOURCE_TYPE_COLUMN;
import static org.thingsboard.server.dao.model.ModelConstants.RESOURCE_VALUE_COLUMN;
import static org.thingsboard.server.dao.model.ModelConstants.TEXT_SEARCH_COLUMN;

@Data
@Entity
@Table(name = RESOURCE_TABLE_NAME)
@IdClass(ResourceCompositeKey.class)
public class ResourceEntity implements ToData<Resource> {

    @Id
    @Column(name = RESOURCE_TENANT_ID_COLUMN, columnDefinition = "uuid")
    private UUID tenantId;

    @Id
    @Column(name = RESOURCE_TYPE_COLUMN)
    private String resourceType;

    @Id
    @Column(name = RESOURCE_ID_COLUMN)
    private String resourceId;

    @Column(name = TEXT_SEARCH_COLUMN)
    private String textSearch;

    @Column(name = RESOURCE_VALUE_COLUMN)
    private String value;

    public ResourceEntity() {
    }

    public ResourceEntity(Resource resource) {
        this.tenantId = resource.getTenantId().getId();
        this.resourceType = resource.getResourceType().name();
        this.resourceId = resource.getResourceId();
        this.textSearch = resource.getTextSearch();
        this.value = resource.getValue();
    }

    @Override
    public Resource toData() {
        Resource resource = new Resource();
        resource.setTenantId(new TenantId(tenantId));
        resource.setResourceType(ResourceType.valueOf(resourceType));
        resource.setResourceId(resourceId);
        resource.setTextSearch(textSearch);
        resource.setValue(value);
        return resource;
    }
}
