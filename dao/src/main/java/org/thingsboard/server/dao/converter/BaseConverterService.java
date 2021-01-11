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
package org.thingsboard.server.dao.converter;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.converter.Converter;
import org.thingsboard.server.common.data.id.ConverterId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.tenant.profile.DefaultTenantProfileConfiguration;
import org.thingsboard.server.dao.entity.AbstractEntityService;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.integration.IntegrationService;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.service.PaginatedRemover;
import org.thingsboard.server.dao.tenant.TbTenantProfileCache;
import org.thingsboard.server.dao.tenant.TenantDao;

import java.util.List;
import java.util.UUID;

import static org.thingsboard.server.dao.DaoUtil.toUUIDs;
import static org.thingsboard.server.dao.service.Validator.validateId;
import static org.thingsboard.server.dao.service.Validator.validateIds;
import static org.thingsboard.server.dao.service.Validator.validatePageLink;

@Service
@Slf4j
public class BaseConverterService extends AbstractEntityService implements ConverterService {

    public static final String INCORRECT_TENANT_ID = "Incorrect tenantId ";
    public static final String INCORRECT_PAGE_LINK = "Incorrect page link ";
    public static final String INCORRECT_CONVERTER_ID = "Incorrect converterId ";

    @Autowired
    @Lazy
    private TbTenantProfileCache tenantProfileCache;

    @Autowired
    private TenantDao tenantDao;

    @Autowired
    private ConverterDao converterDao;

    @Autowired
    private IntegrationService integrationService;

    @Override
    public Converter saveConverter(Converter converter) {
        log.trace("Executing saveConverter [{}]", converter);
        converterValidator.validate(converter, Converter::getTenantId);
        return converterDao.save(converter.getTenantId(), converter);
    }

    @Override
    public Converter findConverterById(TenantId tenantId, ConverterId converterId) {
        log.trace("Executing findConverterById [{}]", converterId);
        validateId(converterId, INCORRECT_CONVERTER_ID + converterId);
        return converterDao.findById(tenantId, converterId.getId());
    }

    @Override
    public ListenableFuture<Converter> findConverterByIdAsync(TenantId tenantId, ConverterId converterId) {
        log.trace("Executing findConverterById [{}]", converterId);
        validateId(converterId, INCORRECT_CONVERTER_ID + converterId);
        return converterDao.findByIdAsync(tenantId, converterId.getId());
    }

    @Override
    public ListenableFuture<List<Converter>> findConvertersByIdsAsync(TenantId tenantId, List<ConverterId> converterIds) {
        log.trace("Executing findConvertersByIdsAsync, tenantId [{}], converterIds [{}]", tenantId, converterIds);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateIds(converterIds, "Incorrect converterIds " + converterIds);
        return converterDao.findConvertersByTenantIdAndIdsAsync(tenantId.getId(), toUUIDs(converterIds));
    }

    @Override
    public PageData<Converter> findTenantConverters(TenantId tenantId, PageLink pageLink) {
        log.trace("Executing findTenantConverters, tenantId [{}], pageLink [{}]", tenantId, pageLink);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validatePageLink(pageLink);
        return converterDao.findByTenantId(tenantId.getId(), pageLink);
    }

    @Override
    public void deleteConverter(TenantId tenantId, ConverterId converterId) {
        log.trace("Executing deleteConverter [{}]", converterId);
        validateId(converterId, INCORRECT_CONVERTER_ID + converterId);
        checkIntegrationsAndDelete(tenantId, converterId);
    }

    private void checkIntegrationsAndDelete(TenantId tenantId, ConverterId converterId) {
        List<Integration> affectedIntegrations = integrationService.findIntegrationsByConverterId(tenantId, converterId);
        if (affectedIntegrations.isEmpty()) {
            deleteEntityRelations(tenantId, converterId);
            converterDao.removeById(tenantId, converterId.getId());
        } else {
            throw new DataValidationException("Converter deletion will affect existing integrations!");
        }
    }

    @Override
    public void deleteConvertersByTenantId(TenantId tenantId) {
        log.trace("Executing deleteConvertersByTenantId, tenantId [{}]", tenantId);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        tenantConvertersRemover.removeEntities(tenantId, tenantId);
    }

    private DataValidator<Converter> converterValidator =
            new DataValidator<Converter>() {

                @Override
                protected void validateCreate(TenantId tenantId, Converter converter) {
                    DefaultTenantProfileConfiguration profileConfiguration =
                            (DefaultTenantProfileConfiguration) tenantProfileCache.get(tenantId).getProfileData().getConfiguration();
                    long maxConverters = profileConfiguration.getMaxConverters();
                    validateNumberOfEntitiesPerTenant(tenantId, converterDao, maxConverters, EntityType.CONVERTER);

                    converterDao.findConverterByTenantIdAndName(converter.getTenantId().getId(), converter.getName()).ifPresent(
                            d -> {
                                throw new DataValidationException("Converter with such name already exists!");
                            }
                    );
                }

                @Override
                protected void validateUpdate(TenantId tenantId, Converter converter) {
                    converterDao.findConverterByTenantIdAndName(converter.getTenantId().getId(), converter.getName()).ifPresent(
                            d -> {
                                if (!d.getId().equals(converter.getId())) {
                                    throw new DataValidationException("Converter with such name already exists!");
                                }
                            }
                    );
                }

                @Override
                protected void validateDataImpl(TenantId tenantId, Converter converter) {
                    if (StringUtils.isEmpty(converter.getType())) {
                        throw new DataValidationException("Converter type should be specified!");
                    }
                    if (StringUtils.isEmpty(converter.getName())) {
                        throw new DataValidationException("Converter name should be specified!");
                    }
                    if (converter.getTenantId() == null || converter.getTenantId().isNullUid()) {
                        throw new DataValidationException("Converter should be assigned to tenant!");
                    } else {
                        Tenant tenant = tenantDao.findById(tenantId, converter.getTenantId().getId());
                        if (tenant == null) {
                            throw new DataValidationException("Converter is referencing to non-existent tenant!");
                        }
                    }
                }
            };

    private PaginatedRemover<TenantId, Converter> tenantConvertersRemover =
            new PaginatedRemover<TenantId, Converter>() {

                @Override
                protected PageData<Converter> findEntities(TenantId tenantId, TenantId id, PageLink pageLink) {
                    return converterDao.findByTenantId(id.getId(), pageLink);
                }

                @Override
                protected void removeEntity(TenantId tenantId, Converter entity) {
                    deleteConverter(tenantId, new ConverterId(entity.getId().getId()));
                }
            };
}
