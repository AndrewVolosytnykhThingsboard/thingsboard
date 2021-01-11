/**
 * Copyright © 2016-2021 The Thingsboard Authors
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
package org.thingsboard.server.dao.selfregistration;

import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.selfregistration.SelfRegistrationParams;

public interface SelfRegistrationService {

    SelfRegistrationParams saveTenantSelfRegistrationParams(TenantId tenantId, SelfRegistrationParams selfRegistrationParams);

    SelfRegistrationParams getTenantSelfRegistrationParams(TenantId tenantId);

    SelfRegistrationParams getSelfRegistrationParams(TenantId sysTenantId, String domainName);

    String getPrivacyPolicy(TenantId sysTenantId, String domainName);

    String getTenantPrivacyPolicy(TenantId tenantId);

    TenantId getTenantIdByDomainName(TenantId sysTenantId, String domainName);

}
