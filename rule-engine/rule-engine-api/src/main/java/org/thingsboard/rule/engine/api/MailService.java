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
package org.thingsboard.rule.engine.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.thingsboard.server.common.data.ApiFeature;
import org.thingsboard.server.common.data.ApiUsageStateMailMessage;
import org.thingsboard.server.common.data.ApiUsageStateValue;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.BlobEntityId;
import org.thingsboard.server.common.data.id.TenantId;

import java.util.List;

public interface MailService {

    void sendEmail(TenantId tenantId, String email, String subject, String message) throws ThingsboardException;
    
    void sendTestMail(TenantId tenantId, JsonNode config, String email) throws ThingsboardException;
    
    void sendActivationEmail(TenantId tenantId, String activationLink, String email) throws ThingsboardException;
    
    void sendAccountActivatedEmail(TenantId tenantId, String loginLink, String email) throws ThingsboardException;
    
    void sendResetPasswordEmail(TenantId tenantId, String passwordResetLink, String email) throws ThingsboardException;
    
    void sendPasswordWasResetEmail(TenantId tenantId, String loginLink, String email) throws ThingsboardException;

    void sendUserActivatedEmail(TenantId tenantId, String userFullName, String userEmail, String email) throws ThingsboardException;

    void sendUserRegisteredEmail(TenantId tenantId, String userFullName, String userEmail, String targetEmail) throws ThingsboardException;

    void send(TenantId tenantId, String from, String to, String cc, String bcc, String subject, String body, List<BlobEntityId> attachments) throws ThingsboardException;

    void sendAccountLockoutEmail(TenantId tenantId, String lockoutEmail, String email, Integer maxFailedLoginAttempts) throws ThingsboardException;

    void sendApiFeatureStateEmail(TenantId tenantId, ApiFeature apiFeature, ApiUsageStateValue stateValue, String email, ApiUsageStateMailMessage msg) throws ThingsboardException;

}
