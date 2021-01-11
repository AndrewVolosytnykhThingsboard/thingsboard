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
package org.thingsboard.integration.opcua;

import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.codec.binary.Base64;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

/**
 * Created by ashvayka on 16.01.17.
 */
@Slf4j
public class OpcUaConfigurationTools {

    public static CertificateInfo loadCertificate(KeystoreConfiguration configuration) throws GeneralSecurityException, IOException {
        try {
            KeyStore keyStore = KeyStore.getInstance(configuration.getType());
            keyStore.load(getResourceAsStream(configuration.getFileContent()), configuration.getPassword().toCharArray());
            Key key = keyStore.getKey(configuration.getAlias(), configuration.getKeyPassword().toCharArray());
            if (key instanceof PrivateKey) {
                X509Certificate certificate = (X509Certificate) keyStore.getCertificate(configuration.getAlias());
                PublicKey publicKey = certificate.getPublicKey();
                KeyPair keyPair = new KeyPair(publicKey, (PrivateKey) key);
                return new CertificateInfo(certificate, keyPair);
            } else {
                throw new GeneralSecurityException(configuration.getAlias() + " is not a private key!");
            }
        } catch (IOException | GeneralSecurityException e) {
            log.error("Keystore configuration: [{}] is invalid!", configuration, e);
            throw new RuntimeException("Failed to load certificate using provided configuration!", e);
        }
    }

    private static InputStream getResourceAsStream(String fileContent) {
        byte[] decoded = Base64.decodeBase64(fileContent);
        return new ByteArrayInputStream(decoded);
    }

}
