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
import com.fasterxml.jackson.databind.JsonNode;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.thingsboard.server.common.data.AdminSettings;
import org.thingsboard.server.common.data.id.AdminSettingsId;
import org.thingsboard.server.dao.model.BaseEntity;
import org.thingsboard.server.dao.model.type.JsonCodec;

import java.util.UUID;

import static org.thingsboard.server.dao.model.ModelConstants.*;

@Table(name = ADMIN_SETTINGS_COLUMN_FAMILY_NAME)
@EqualsAndHashCode
@ToString
public final class AdminSettingsEntity implements BaseEntity<AdminSettings> {

    @PartitionKey(value = 0)
    @Column(name = ID_PROPERTY)
    private UUID id;
    
    @Column(name = ADMIN_SETTINGS_KEY_PROPERTY)
    private String key;

    @Column(name = ADMIN_SETTINGS_JSON_VALUE_PROPERTY, codec = JsonCodec.class)
    private JsonNode jsonValue;

    public AdminSettingsEntity() {
        super();
    }

    public AdminSettingsEntity(AdminSettings adminSettings) {
        if (adminSettings.getId() != null) {
            this.id = adminSettings.getId().getId();
        }
        this.key = adminSettings.getKey();
        this.jsonValue = adminSettings.getJsonValue();
    }
    
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public JsonNode getJsonValue() {
        return jsonValue;
    }

    public void setJsonValue(JsonNode jsonValue) {
        this.jsonValue = jsonValue;
    }

    @Override
    public AdminSettings toData() {
        AdminSettings adminSettings = new AdminSettings(new AdminSettingsId(id));
        adminSettings.setCreatedTime(UUIDs.unixTimestamp(id));
        adminSettings.setKey(key);
        adminSettings.setJsonValue(jsonValue);
        return adminSettings;
    }

}