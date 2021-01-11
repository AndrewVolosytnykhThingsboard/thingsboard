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
package org.thingsboard.rule.engine.analytics.incoming.state;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by ashvayka on 13.06.18.
 */
@Data
@NoArgsConstructor
public class TbCountIntervalState extends TbBaseIntervalState {

    private long count = 0L;

    public TbCountIntervalState(JsonElement stateJson) {
        this.count = stateJson.getAsJsonObject().get("count").getAsLong();
    }

    @Override
    protected boolean doUpdate(JsonElement data) {
        this.count++;
        return true;
    }

    @Override
    public String toValueJson(Gson gson, String outputValueKey) {
        JsonObject json = new JsonObject();
        json.addProperty(outputValueKey, count);
        return gson.toJson(json);
    }

    @Override
    public String toStateJson(Gson gson) {
        JsonObject object = new JsonObject();
        object.addProperty("count", Long.toString(count));
        return gson.toJson(object);
    }
}
