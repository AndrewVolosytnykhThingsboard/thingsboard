/**
 * Copyright © 2016-2017 The Thingsboard Authors
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

package org.thingsboard.server.common.data;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.thingsboard.server.common.data.group.EntityField;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.HasId;

import java.util.HashMap;
import java.util.Map;

public class EntityView implements HasId<EntityId>, HasName {

    private final EntityId id;
    private Map<String, String> properties = new HashMap<>();

    public EntityView(EntityId id) {
        super();
        this.id = id;
    }

    @Override
    public EntityId getId() {
        return id;
    }

    @JsonAnyGetter
    public Map<String, String> properties() {
        return this.properties;
    }

    @JsonAnySetter
    public void put(String name, String value) {
        this.properties.put(name, value);
    }

    @Override
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getName() {
        return this.properties.get(EntityField.NAME.name().toLowerCase());
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (id != null ? id.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        EntityView that = (EntityView) o;

        return id != null ? id.equals(that.id) : that.id == null;

    }

}
