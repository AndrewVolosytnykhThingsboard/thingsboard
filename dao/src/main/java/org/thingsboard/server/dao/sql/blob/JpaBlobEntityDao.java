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
package org.thingsboard.server.dao.sql.blob;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.blob.BlobEntity;
import org.thingsboard.server.dao.blob.BlobEntityDao;
import org.thingsboard.server.dao.model.sql.BlobEntityEntity;
import org.thingsboard.server.dao.sql.JpaAbstractSearchTextDao;

import java.util.UUID;

@Component
public class JpaBlobEntityDao extends JpaAbstractSearchTextDao<BlobEntityEntity, BlobEntity> implements BlobEntityDao {

    @Autowired
    BlobEntityRepository blobEntityRepository;

    @Override
    protected Class<BlobEntityEntity> getEntityClass() {
        return BlobEntityEntity.class;
    }

    @Override
    protected CrudRepository<BlobEntityEntity, UUID> getCrudRepository() {
        return blobEntityRepository;
    }
}
