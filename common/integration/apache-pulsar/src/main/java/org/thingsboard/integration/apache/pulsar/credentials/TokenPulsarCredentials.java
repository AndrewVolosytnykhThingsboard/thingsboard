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
package org.thingsboard.integration.apache.pulsar.credentials;

import lombok.Setter;
import org.apache.pulsar.client.api.Authentication;
import org.apache.pulsar.client.impl.auth.AuthenticationToken;

public class TokenPulsarCredentials implements PulsarCredentials {

    @Setter
    private String token;

    @Override
    public Authentication getAuthentication() {
        return new AuthenticationToken(token);
    }
}
