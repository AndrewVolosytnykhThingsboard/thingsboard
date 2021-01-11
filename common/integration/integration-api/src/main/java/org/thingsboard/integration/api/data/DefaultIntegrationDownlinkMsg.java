/**
 * Copyright © 2016-2021 ThingsBoard, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.integration.api.data;

import lombok.Data;
import org.thingsboard.integration.api.data.IntegrationDownlinkMsg;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.IntegrationId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.msg.TbMsg;

/**
 * Created by ashvayka on 22.02.18.
 */
@Data
public class DefaultIntegrationDownlinkMsg implements IntegrationDownlinkMsg {

    private final TenantId tenantId;
    private final IntegrationId integrationId;
    private final TbMsg tbMsg;
    private final String entityName;

    @Override
    public EntityId getEntityId() {
        return tbMsg.getOriginator();
    }
}
