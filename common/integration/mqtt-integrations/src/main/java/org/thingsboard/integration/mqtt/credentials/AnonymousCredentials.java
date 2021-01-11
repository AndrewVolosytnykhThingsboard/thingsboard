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
package org.thingsboard.integration.mqtt.credentials;

import io.netty.handler.ssl.SslContext;
import org.thingsboard.mqtt.MqttClientConfig;

import java.util.Optional;

/**
 * Created by ashvayka on 23.01.17.
 */
public class AnonymousCredentials implements MqttClientCredentials {

    @Override
    public Optional<SslContext> initSslContext() {
        return Optional.empty();
    }

    @Override
    public void configure(MqttClientConfig config) {

    }
}
