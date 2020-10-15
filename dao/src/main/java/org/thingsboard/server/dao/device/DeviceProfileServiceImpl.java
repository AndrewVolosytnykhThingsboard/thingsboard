/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
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
package org.thingsboard.server.dao.device;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.DeviceProfileInfo;
import org.thingsboard.server.common.data.DeviceProfileProvisionType;
import org.thingsboard.server.common.data.DeviceProfileType;
import org.thingsboard.server.common.data.DeviceTransportType;
import org.thingsboard.server.common.data.EntitySubtype;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.device.profile.DefaultDeviceProfileConfiguration;
import org.thingsboard.server.common.data.device.profile.DefaultDeviceProfileTransportConfiguration;
import org.thingsboard.server.common.data.device.profile.DeviceProfileData;
import org.thingsboard.server.common.data.device.profile.DisabledDeviceProfileProvisionConfiguration;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.entity.AbstractEntityService;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.service.PaginatedRemover;
import org.thingsboard.server.dao.service.Validator;
import org.thingsboard.server.dao.tenant.TenantDao;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.thingsboard.server.common.data.CacheConstants.DEVICE_PROFILE_CACHE;
import static org.thingsboard.server.dao.service.Validator.validateId;

@Service
@Slf4j
public class DeviceProfileServiceImpl extends AbstractEntityService implements DeviceProfileService {

    private static final String INCORRECT_TENANT_ID = "Incorrect tenantId ";
    private static final String INCORRECT_DEVICE_PROFILE_ID = "Incorrect deviceProfileId ";
    private static final String INCORRECT_DEVICE_PROFILE_NAME = "Incorrect deviceProfileName ";

    @Autowired
    private DeviceProfileDao deviceProfileDao;

    @Autowired
    private DeviceDao deviceDao;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private TenantDao tenantDao;

    @Autowired
    private CacheManager cacheManager;

    @Cacheable(cacheNames = DEVICE_PROFILE_CACHE, key = "{#deviceProfileId.id}")
    @Override
    public DeviceProfile findDeviceProfileById(TenantId tenantId, DeviceProfileId deviceProfileId) {
        log.trace("Executing findDeviceProfileById [{}]", deviceProfileId);
        Validator.validateId(deviceProfileId, INCORRECT_DEVICE_PROFILE_ID + deviceProfileId);
        return deviceProfileDao.findById(tenantId, deviceProfileId.getId());
    }

    @Override
    public DeviceProfile findDeviceProfileByName(TenantId tenantId, String profileName) {
        log.trace("Executing findDeviceProfileByName [{}][{}]", tenantId, profileName);
        Validator.validateString(profileName, INCORRECT_DEVICE_PROFILE_NAME + profileName);
        return deviceProfileDao.findByName(tenantId, profileName);
    }

    @Cacheable(cacheNames = DEVICE_PROFILE_CACHE, key = "{'info', #deviceProfileId.id}")
    @Override
    public DeviceProfileInfo findDeviceProfileInfoById(TenantId tenantId, DeviceProfileId deviceProfileId) {
        log.trace("Executing findDeviceProfileById [{}]", deviceProfileId);
        Validator.validateId(deviceProfileId, INCORRECT_DEVICE_PROFILE_ID + deviceProfileId);
        return deviceProfileDao.findDeviceProfileInfoById(tenantId, deviceProfileId.getId());
    }

    @Override
    public DeviceProfile saveDeviceProfile(DeviceProfile deviceProfile) {
        log.trace("Executing saveDeviceProfile [{}]", deviceProfile);
        deviceProfileValidator.validate(deviceProfile, DeviceProfile::getTenantId);
        DeviceProfile oldDeviceProfile = null;
        if (deviceProfile.getId() != null) {
            oldDeviceProfile = deviceProfileDao.findById(deviceProfile.getTenantId(), deviceProfile.getId().getId());
        }
        DeviceProfile savedDeviceProfile;
        try {
            savedDeviceProfile = deviceProfileDao.save(deviceProfile.getTenantId(), deviceProfile);
        } catch (Exception t) {
            ConstraintViolationException e = extractConstraintViolationException(t).orElse(null);
            if (e != null && e.getConstraintName() != null && e.getConstraintName().equalsIgnoreCase("device_profile_name_unq_key")) {
                throw new DataValidationException("Device profile with such name already exists!");
            } else if (e != null && e.getConstraintName() != null && e.getConstraintName().equalsIgnoreCase("device_provision_key_unq_key")) {
                    throw new DataValidationException("Device profile with such provision device key already exists!");
            } else {
                throw t;
            }
        }
        Cache cache = cacheManager.getCache(DEVICE_PROFILE_CACHE);
        cache.evict(Collections.singletonList(savedDeviceProfile.getId().getId()));
        cache.evict(Arrays.asList("info", savedDeviceProfile.getId().getId()));
        cache.evict(Arrays.asList(deviceProfile.getTenantId().getId(), deviceProfile.getName()));
        if (savedDeviceProfile.isDefault()) {
            cache.evict(Arrays.asList("default", savedDeviceProfile.getTenantId().getId()));
            cache.evict(Arrays.asList("default", "info", savedDeviceProfile.getTenantId().getId()));
        }
        if (oldDeviceProfile != null && !oldDeviceProfile.getName().equals(deviceProfile.getName())) {
            PageLink pageLink = new PageLink(100);
            PageData<Device> pageData;
            do {
                pageData = deviceDao.findDevicesByTenantIdAndProfileId(deviceProfile.getTenantId().getId(), deviceProfile.getUuidId(), pageLink);
                for (Device device : pageData.getData()) {
                    device.setType(deviceProfile.getName());
                    deviceService.saveDevice(device);
                }
                pageLink = pageLink.nextPageLink();
            } while (pageData.hasNext());
        }
        return savedDeviceProfile;
    }

    @Override
    public void deleteDeviceProfile(TenantId tenantId, DeviceProfileId deviceProfileId) {
        log.trace("Executing deleteDeviceProfile [{}]", deviceProfileId);
        Validator.validateId(deviceProfileId, INCORRECT_DEVICE_PROFILE_ID + deviceProfileId);
        DeviceProfile deviceProfile = deviceProfileDao.findById(tenantId, deviceProfileId.getId());
        if (deviceProfile != null && deviceProfile.isDefault()) {
            throw new DataValidationException("Deletion of Default Device Profile is prohibited!");
        }
        this.removeDeviceProfile(tenantId, deviceProfile);
    }

    private void removeDeviceProfile(TenantId tenantId, DeviceProfile deviceProfile) {
        DeviceProfileId deviceProfileId = deviceProfile.getId();
        try {
            deviceProfileDao.removeById(tenantId, deviceProfileId.getId());
        } catch (Exception t) {
            ConstraintViolationException e = extractConstraintViolationException(t).orElse(null);
            if (e != null && e.getConstraintName() != null && e.getConstraintName().equalsIgnoreCase("fk_device_profile")) {
                throw new DataValidationException("The device profile referenced by the devices cannot be deleted!");
            } else {
                throw t;
            }
        }
        deleteEntityRelations(tenantId, deviceProfileId);
        Cache cache = cacheManager.getCache(DEVICE_PROFILE_CACHE);
        cache.evict(Collections.singletonList(deviceProfileId.getId()));
        cache.evict(Arrays.asList("info", deviceProfileId.getId()));
        cache.evict(Arrays.asList(tenantId.getId(), deviceProfile.getName()));
    }

    @Override
    public PageData<DeviceProfile> findDeviceProfiles(TenantId tenantId, PageLink pageLink) {
        log.trace("Executing findDeviceProfiles tenantId [{}], pageLink [{}]", tenantId, pageLink);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        Validator.validatePageLink(pageLink);
        return deviceProfileDao.findDeviceProfiles(tenantId, pageLink);
    }

    @Override
    public PageData<DeviceProfileInfo> findDeviceProfileInfos(TenantId tenantId, PageLink pageLink, String transportType) {
        log.trace("Executing findDeviceProfileInfos tenantId [{}], pageLink [{}]", tenantId, pageLink);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        Validator.validatePageLink(pageLink);
        return deviceProfileDao.findDeviceProfileInfos(tenantId, pageLink, transportType);
    }

    @Cacheable(cacheNames = DEVICE_PROFILE_CACHE, key = "{#tenantId.id, #name}")
    @Override
    public DeviceProfile findOrCreateDeviceProfile(TenantId tenantId, String name) {
        log.trace("Executing findOrCreateDefaultDeviceProfile");
        DeviceProfile deviceProfile = findDeviceProfileByName(tenantId, name);
        if (deviceProfile == null) {
            deviceProfile = this.doCreateDefaultDeviceProfile(tenantId, name, name.equals("default"));
        }
        return deviceProfile;
    }

    @Override
    public DeviceProfile createDefaultDeviceProfile(TenantId tenantId) {
        log.trace("Executing createDefaultDeviceProfile tenantId [{}]", tenantId);
        return doCreateDefaultDeviceProfile(tenantId, "default", true);
    }

    private DeviceProfile doCreateDefaultDeviceProfile(TenantId tenantId, String profileName, boolean defaultProfile) {
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        DeviceProfile deviceProfile = new DeviceProfile();
        deviceProfile.setTenantId(tenantId);
        deviceProfile.setDefault(defaultProfile);
        deviceProfile.setName(profileName);
        deviceProfile.setType(DeviceProfileType.DEFAULT);
        deviceProfile.setTransportType(DeviceTransportType.DEFAULT);
        deviceProfile.setProvisionType(DeviceProfileProvisionType.DISABLED);
        deviceProfile.setDescription("Default device profile");
        DeviceProfileData deviceProfileData = new DeviceProfileData();
        DefaultDeviceProfileConfiguration configuration = new DefaultDeviceProfileConfiguration();
        DefaultDeviceProfileTransportConfiguration transportConfiguration = new DefaultDeviceProfileTransportConfiguration();
        DisabledDeviceProfileProvisionConfiguration provisionConfiguration = new DisabledDeviceProfileProvisionConfiguration(null);
        deviceProfileData.setConfiguration(configuration);
        deviceProfileData.setTransportConfiguration(transportConfiguration);
        deviceProfileData.setProvisionConfiguration(provisionConfiguration);
        deviceProfile.setProfileData(deviceProfileData);
        return saveDeviceProfile(deviceProfile);
    }

    @Cacheable(cacheNames = DEVICE_PROFILE_CACHE, key = "{'default', #tenantId.id}")
    @Override
    public DeviceProfile findDefaultDeviceProfile(TenantId tenantId) {
        log.trace("Executing findDefaultDeviceProfile tenantId [{}]", tenantId);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        return deviceProfileDao.findDefaultDeviceProfile(tenantId);
    }

    @Cacheable(cacheNames = DEVICE_PROFILE_CACHE, key = "{'default', 'info', #tenantId.id}")
    @Override
    public DeviceProfileInfo findDefaultDeviceProfileInfo(TenantId tenantId) {
        log.trace("Executing findDefaultDeviceProfileInfo tenantId [{}]", tenantId);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        return deviceProfileDao.findDefaultDeviceProfileInfo(tenantId);
    }

    @Override
    public boolean setDefaultDeviceProfile(TenantId tenantId, DeviceProfileId deviceProfileId) {
        log.trace("Executing setDefaultDeviceProfile [{}]", deviceProfileId);
        Validator.validateId(deviceProfileId, INCORRECT_DEVICE_PROFILE_ID + deviceProfileId);
        DeviceProfile deviceProfile = deviceProfileDao.findById(tenantId, deviceProfileId.getId());
        if (!deviceProfile.isDefault()) {
            Cache cache = cacheManager.getCache(DEVICE_PROFILE_CACHE);
            deviceProfile.setDefault(true);
            DeviceProfile previousDefaultDeviceProfile = findDefaultDeviceProfile(tenantId);
            boolean changed = false;
            if (previousDefaultDeviceProfile == null) {
                deviceProfileDao.save(tenantId, deviceProfile);
                changed = true;
            } else if (!previousDefaultDeviceProfile.getId().equals(deviceProfile.getId())) {
                previousDefaultDeviceProfile.setDefault(false);
                deviceProfileDao.save(tenantId, previousDefaultDeviceProfile);
                deviceProfileDao.save(tenantId, deviceProfile);
                cache.evict(Collections.singletonList(previousDefaultDeviceProfile.getId().getId()));
                cache.evict(Arrays.asList("info", previousDefaultDeviceProfile.getId().getId()));
                cache.evict(Arrays.asList(tenantId.getId(), previousDefaultDeviceProfile.getName()));
                changed = true;
            }
            if (changed) {
                cache.evict(Collections.singletonList(deviceProfile.getId().getId()));
                cache.evict(Arrays.asList("info", deviceProfile.getId().getId()));
                cache.evict(Arrays.asList("default", tenantId.getId()));
                cache.evict(Arrays.asList("default", "info", tenantId.getId()));
                cache.evict(Arrays.asList(tenantId.getId(), deviceProfile.getName()));
            }
            return changed;
        }
        return false;
    }

    @Override
    public void deleteDeviceProfilesByTenantId(TenantId tenantId) {
        log.trace("Executing deleteDeviceProfilesByTenantId, tenantId [{}]", tenantId);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        tenantDeviceProfilesRemover.removeEntities(tenantId, tenantId);
    }

    private DataValidator<DeviceProfile> deviceProfileValidator =
            new DataValidator<DeviceProfile>() {
                @Override
                protected void validateDataImpl(TenantId tenantId, DeviceProfile deviceProfile) {
                    if (StringUtils.isEmpty(deviceProfile.getName())) {
                        throw new DataValidationException("Device profile name should be specified!");
                    }
                    if (deviceProfile.getType() == null) {
                        throw new DataValidationException("Device profile type should be specified!");
                    }
                    if (deviceProfile.getTransportType() == null) {
                        throw new DataValidationException("Device profile transport type should be specified!");
                    }
                    if (deviceProfile.getTenantId() == null) {
                        throw new DataValidationException("Device profile should be assigned to tenant!");
                    } else {
                        Tenant tenant = tenantDao.findById(deviceProfile.getTenantId(), deviceProfile.getTenantId().getId());
                        if (tenant == null) {
                            throw new DataValidationException("Device profile is referencing to non-existent tenant!");
                        }
                    }
                    if (deviceProfile.isDefault()) {
                        DeviceProfile defaultDeviceProfile = findDefaultDeviceProfile(tenantId);
                        if (defaultDeviceProfile != null && !defaultDeviceProfile.getId().equals(deviceProfile.getId())) {
                            throw new DataValidationException("Another default device profile is present in scope of current tenant!");
                        }
                    }
                }

                @Override
                protected void validateUpdate(TenantId tenantId, DeviceProfile deviceProfile) {
                    DeviceProfile old = deviceProfileDao.findById(deviceProfile.getTenantId(), deviceProfile.getId().getId());
                    if (old == null) {
                        throw new DataValidationException("Can't update non existing device profile!");
                    }
                    boolean profileTypeChanged = !old.getType().equals(deviceProfile.getType());
                    boolean transportTypeChanged = !old.getTransportType().equals(deviceProfile.getTransportType());
                    if (profileTypeChanged || transportTypeChanged) {
                        Long profileDeviceCount = deviceDao.countDevicesByDeviceProfileId(deviceProfile.getTenantId(), deviceProfile.getId().getId());
                        if (profileDeviceCount > 0) {
                            String message = null;
                            if (profileTypeChanged) {
                                message = "Can't change device profile type because devices referenced it!";
                            } else if (transportTypeChanged) {
                                message = "Can't change device profile transport type because devices referenced it!";
                            }
                            throw new DataValidationException(message);
                        }
                    }
                }
            };

    private PaginatedRemover<TenantId, DeviceProfile> tenantDeviceProfilesRemover =
            new PaginatedRemover<TenantId, DeviceProfile>() {

                @Override
                protected PageData<DeviceProfile> findEntities(TenantId tenantId, TenantId id, PageLink pageLink) {
                    return deviceProfileDao.findDeviceProfiles(id, pageLink);
                }

                @Override
                protected void removeEntity(TenantId tenantId, DeviceProfile entity) {
                    removeDeviceProfile(tenantId, entity);
                }
            };

}
