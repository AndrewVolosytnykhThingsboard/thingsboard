/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.dao.sql.resource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.thingsboard.server.dao.model.sql.ResourceCompositeKey;
import org.thingsboard.server.dao.model.sql.ResourceEntity;

import java.util.List;
import java.util.UUID;

public interface ResourceRepository extends CrudRepository<ResourceEntity, ResourceCompositeKey> {


    Page<ResourceEntity> findAllByTenantId(UUID tenantId, Pageable pageable);

    @Query("SELECT tr FROM ResourceEntity tr " +
            "WHERE tr.resourceType = :resourceType " +
            "AND LOWER(tr.textSearch) LIKE LOWER(CONCAT('%', :searchText, '%')) " +
            "AND (tr.tenantId = :tenantId " +
            "OR (tr.tenantId = :systemAdminId " +
            "AND NOT EXISTS " +
            "(SELECT sr FROM ResourceEntity sr " +
            "WHERE sr.tenantId = :tenantId " +
            "AND sr.resourceType = :resourceType " +
            "AND tr.resourceId = sr.resourceId)))")
    Page<ResourceEntity> findResourcesPage(
            @Param("tenantId") UUID tenantId,
            @Param("systemAdminId") UUID sysAdminId,
            @Param("resourceType") String resourceType,
            @Param("searchText") String search,
            Pageable pageable);

    List<ResourceEntity> findAllByTenantIdAndResourceType(UUID tenantId, String resourceType);

    void removeAllByTenantId(UUID tenantId);

    @Query("SELECT tr FROM ResourceEntity tr " +
            "WHERE tr.resourceType = :resourceType " +
            "AND LOWER(tr.textSearch) LIKE LOWER(CONCAT('%', :searchText, '%')) " +
            "AND (tr.tenantId = :tenantId " +
            "OR (tr.tenantId = :systemAdminId " +
            "AND NOT EXISTS " +
            "(SELECT sr FROM ResourceEntity sr " +
            "WHERE sr.tenantId = :tenantId " +
            "AND sr.resourceType = :resourceType " +
            "AND tr.resourceId = sr.resourceId)))")
    List<ResourceEntity> findResources(@Param("tenantId") UUID tenantId,
                                       @Param("systemAdminId") UUID sysAdminId,
                                       @Param("resourceType") String resourceType,
                                       @Param("searchText") String search);

    @Query("SELECT tr FROM ResourceEntity tr " +
            "WHERE tr.resourceType = :resourceType " +
            "AND tr.resourceId in (:resourceIds) " +
            "AND (tr.tenantId = :tenantId " +
            "OR (tr.tenantId = :systemAdminId " +
            "AND NOT EXISTS " +
            "(SELECT sr FROM ResourceEntity sr " +
            "WHERE sr.tenantId = :tenantId " +
            "AND sr.resourceType = :resourceType " +
            "AND tr.resourceId = sr.resourceId)))")
    List<ResourceEntity> findResourcesByIds(@Param("tenantId") UUID tenantId,
                                            @Param("systemAdminId") UUID sysAdminId,
                                            @Param("resourceType") String resourceType,
                                            @Param("resourceIds") String[] objectIds);
}
