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
package org.thingsboard.integration.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.thingsboard.integration.api.data.IntegrationDownlinkMsg;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.integration.Integration;

/**
 * Created by ashvayka on 02.12.17.
 */
public interface ThingsboardPlatformIntegration<T> {

    Integration getConfiguration();

    void validateConfiguration(Integration configuration, boolean allowLocalNetworkHosts);

    void checkConnection(Integration integration, IntegrationContext ctx) throws ThingsboardException;

    void init(TbIntegrationInitParams params) throws Exception;

    void update(TbIntegrationInitParams params) throws Exception;

    void destroy();

    void process(T msg);

    void onDownlinkMsg(IntegrationDownlinkMsg msg);

    IntegrationStatistics popStatistics();

}
