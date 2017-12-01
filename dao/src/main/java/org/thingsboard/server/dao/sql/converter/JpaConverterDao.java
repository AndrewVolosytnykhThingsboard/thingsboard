/**
 * Thingsboard OÜ ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2017 Thingsboard OÜ. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of Thingsboard OÜ and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Thingsboard OÜ
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
package org.thingsboard.server.dao.sql.converter;

import com.google.common.util.concurrent.ListenableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.EntitySubtype;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.converter.Converter;
import org.thingsboard.server.common.data.converter.ConverterType;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.converter.ConverterDao;
import org.thingsboard.server.dao.model.sql.ConverterEntity;
import org.thingsboard.server.dao.sql.JpaAbstractSearchTextDao;
import org.thingsboard.server.dao.util.SqlDao;

import java.util.*;

import static org.thingsboard.server.common.data.UUIDConverter.fromTimeUUID;
import static org.thingsboard.server.common.data.UUIDConverter.fromTimeUUIDs;
import static org.thingsboard.server.dao.model.ModelConstants.NULL_UUID_STR;

@Component
@SqlDao
public class JpaConverterDao extends JpaAbstractSearchTextDao<ConverterEntity, Converter> implements ConverterDao {

    @Autowired
    private ConverterRepository converterRepository;

    @Override
    public List<Converter> findConvertersByTenantId(UUID tenantId, TextPageLink pageLink) {
        return DaoUtil.convertDataList(converterRepository
                .findByTenantId(
                        fromTimeUUID(tenantId),
                        Objects.toString(pageLink.getTextSearch(), ""),
                        pageLink.getIdOffset() == null ? NULL_UUID_STR : fromTimeUUID(pageLink.getIdOffset()),
                        new PageRequest(0, pageLink.getLimit())));
    }

    @Override
    public List<Converter> findConvertersByTenantIdAndType(UUID tenantId, ConverterType type, TextPageLink pageLink) {
        return DaoUtil.convertDataList(converterRepository
                .findByTenantIdAndType(
                        fromTimeUUID(tenantId),
                        type,
                        Objects.toString(pageLink.getTextSearch(), ""),
                        pageLink.getIdOffset() == null ? NULL_UUID_STR : fromTimeUUID(pageLink.getIdOffset()),
                        new PageRequest(0, pageLink.getLimit())));
    }

    @Override
    public ListenableFuture<List<Converter>> findConvertersByTenantIdAndIdsAsync(UUID tenantId, List<UUID> converterIds) {
        return service.submit(() ->
                DaoUtil.convertDataList(converterRepository.findByTenantIdAndIdIn(fromTimeUUID(tenantId), fromTimeUUIDs(converterIds))));
    }

    @Override
    public Optional<Converter> findConvertersByTenantIdAndName(UUID tenantId, String name) {
        Converter converter = DaoUtil.getData(converterRepository.findByTenantIdAndName(fromTimeUUID(tenantId), name));
        return Optional.ofNullable(converter);
    }

    @Override
    public ListenableFuture<List<EntitySubtype>> findTenantConverterTypesAsync(UUID tenantId) {
        return service.submit(() -> convertTenantConverterTypesToDto(tenantId, converterRepository.findTenantConverterTypes(fromTimeUUID(tenantId))));
    }

    @Override
    protected Class<ConverterEntity> getEntityClass() {
        return ConverterEntity.class;
    }

    @Override
    protected CrudRepository<ConverterEntity, String> getCrudRepository() {
        return converterRepository;
    }

    private List<EntitySubtype> convertTenantConverterTypesToDto(UUID tenantId, List<ConverterType> types) {
        List<EntitySubtype> list = Collections.emptyList();
        if (types != null && !types.isEmpty()) {
            list = new ArrayList<>();
            for (ConverterType type : types) {
                list.add(new EntitySubtype(new TenantId(tenantId), EntityType.CONVERTER, type.toString()));
            }
        }
        return list;
    }
}
