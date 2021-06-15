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
package org.thingsboard.server.dao.rpc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.cfg.ConstraintMapping;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.rpc.Rpc;
import org.thingsboard.server.common.data.validation.NoXss;
import org.thingsboard.server.dao.device.DeviceDao;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.service.NoXssValidator;
import org.thingsboard.server.dao.tenant.TenantDao;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class RpcValidator {
    private final TenantDao tenantDao;
    private final DeviceDao deviceDao;
    private final RpcDao rpcDao;

    private static Validator fieldsValidator;

    static {
        initializeFieldsValidator();
    }

    public void validate(Rpc data) {
        try {
            if (data == null) {
                throw new DataValidationException("Data object can't be null!");
            }

            List<String> validationErrors = validateFields(data);
            if (!validationErrors.isEmpty()) {
                throw new IllegalArgumentException("Validation error: " + String.join(", ", validationErrors));
            }

            TenantId tenantId = data.getTenantId();

            Rpc old = rpcDao.findById(tenantId, data.getId().getId());
            if (old == null) {
                validateCreate(tenantId, data);
            } else {
                validateUpdate(tenantId, data);
            }
        } catch (DataValidationException e) {
            log.error("Data object is invalid: [{}]", e.getMessage());
            throw e;
        }
    }

    protected void validateCreate(TenantId tenantId, Rpc data) {
        if(tenantId == null) {
            throw new DataValidationException("Tenant ID should be specified!");
        } else {
            Tenant tenant = tenantDao.findById(tenantId, tenantId.getId());
            if(tenant == null) {
                throw new DataValidationException("RPC is referencing to non-existing tenant!");
            }
        }
        if(data.getDeviceId() == null) {
            throw new DataValidationException("Device ID should be specified!");
        } else {
            Device device = deviceDao.findById(null, data.getDeviceId().getId());
            if(device == null) {
                throw new DataValidationException("RPC is referencing to non-existing device!");
            }
        }
        if(data.getRequest() == null) {
            throw new DataValidationException("RPC request should be not empty!");
        }
        if(StringUtils.isEmpty(data.getStatus())) {
            throw new DataValidationException("RPC status should be not empty!");
        }
        if(data.getExpirationTime() <= 0) {
            throw new DataValidationException("Expiration time should be more than 0!");
        }
    }

    protected void validateUpdate(TenantId tenantId, Rpc data) {
        Rpc old = rpcDao.findById(tenantId, data.getId().getId());
        if(old != null) {
            if (old.getResponse() != null && !old.getResponse().equals(data.getResponse())) {
                throw new DataValidationException("Can't update RPC response!");
            }
            if(!old.getRequest().equals(data.getRequest())) {
                throw new DataValidationException("Can't update RPC request!");
            }
            if(old.getExpirationTime() != data.getExpirationTime()) {
                throw new DataValidationException("Can't update RPC expiration time!");
            }
            if(!old.getDeviceId().equals(data.getDeviceId())) {
                throw new DataValidationException("Can't update Device id!");
            }
            if(!old.getTenantId().equals(data.getTenantId())) {
                throw new DataValidationException("Can't update Tenant id!");
            }
        }
    }

    private List<String> validateFields(Rpc data) {
        Set<ConstraintViolation<Rpc>> constraintsViolations = fieldsValidator.validate(data);
        return constraintsViolations.stream()
                .map(ConstraintViolation::getMessage)
                .distinct()
                .collect(Collectors.toList());
    }

    private static void initializeFieldsValidator() {
        HibernateValidatorConfiguration validatorConfiguration = Validation.byProvider(HibernateValidator.class).configure();
        ConstraintMapping constraintMapping = validatorConfiguration.createConstraintMapping();
        constraintMapping.constraintDefinition(NoXss.class).validatedBy(NoXssValidator.class);
        validatorConfiguration.addMapping(constraintMapping);

        fieldsValidator = validatorConfiguration.buildValidatorFactory().getValidator();
    }
}
