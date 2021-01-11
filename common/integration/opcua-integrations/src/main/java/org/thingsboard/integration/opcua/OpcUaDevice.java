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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class OpcUaDevice {

    private final static ObjectMapper mapper = new ObjectMapper();

    private final NodeId nodeId;
    private final DeviceMapping mapping;
    private final Map<String,String> deviceMetadata;
    private final Map<String, NodeId> tagKeysMap = new HashMap<>();
    private final Map<NodeId, String> tagIdsMap = new HashMap<>();
    private final Map<String, String> tagValues = new HashMap<>();
    private final Map<NodeId, List<SubscriptionTag>> subscriptionTagsMap = new HashMap<>();

    private long scanTs;

    public OpcUaDevice(OpcUaNode node, DeviceMapping mapping) {
        this.nodeId = node.getNodeId();
        this.mapping = mapping;
        this.deviceMetadata = new HashMap<>();
        this.deviceMetadata.put("opcUaNode_identifier", node.getNodeId().getIdentifier().toString());
        this.deviceMetadata.put("opcUaNode_namespaceIndex", node.getNodeId().getNamespaceIndex().toString());
        this.deviceMetadata.put("opcUaNode_name", node.getName());
        this.deviceMetadata.put("opcUaNode_fqn", node.getFqn());
    }

    public Map<String, NodeId> registerTags(Map<String, NodeId> newTagMap) {
        Map<String, NodeId> newTags = new HashMap<>();
        for (Map.Entry<String, NodeId> kv : newTagMap.entrySet()) {
            NodeId old = registerTag(kv);
            if (old == null) {
                newTags.put(kv.getKey(), kv.getValue());
            }
        }
        return newTags;
    }

    private NodeId registerTag(Map.Entry<String, NodeId> kv) {
        String tag = kv.getKey();
        NodeId tagId = kv.getValue();
        mapping.getSubscriptionTags().stream().filter(subscriptionTag -> subscriptionTag.getPath().equals(tag))
                .forEach(subscriptionTag -> subscriptionTagsMap.computeIfAbsent(tagId, key -> new ArrayList<>()).add(subscriptionTag));
        tagIdsMap.putIfAbsent(kv.getValue(), kv.getKey());
        return tagKeysMap.put(kv.getKey(), kv.getValue());
    }

    public void updateTag(NodeId tagId, DataValue dataValue) {
        String tag = tagIdsMap.get(tagId);
        tagValues.put(tag, dataValue.getValue().getValue().toString());
    }

    public void updateScanTs() {
        scanTs = System.currentTimeMillis();
    }

    public OpcUaIntegrationMsg prepareMsg(NodeId affectedTagId) {
        JsonNode payload = preparePayload(affectedTagId);
        return new OpcUaIntegrationMsg(payload, this.deviceMetadata);
    }

    private JsonNode preparePayload(NodeId affectedTagId) {
        String affectedTagName = null;
        if (affectedTagId != null) {
            affectedTagName = tagIdsMap.get(affectedTagId);
        }
        return preparePayload(affectedTagName);
    }

    private JsonNode preparePayload(String affectedTagName) {
        ObjectNode payload = mapper.createObjectNode();
        mapping.getSubscriptionTags().forEach(subscriptionTag -> {
            if (affectedTagName == null ||
                    subscriptionTag.isRequired() ||
                    (affectedTagName != null && subscriptionTag.getPath().equals(affectedTagName))) {
                String key = subscriptionTag.getKey();
                String value = tagValues.get(subscriptionTag.getPath());
                payload.put(key, value);
            }
        });
        return payload;
    }
}
