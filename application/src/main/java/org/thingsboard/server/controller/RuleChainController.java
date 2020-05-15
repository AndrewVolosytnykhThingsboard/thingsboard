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
package org.thingsboard.server.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.js.api.JsInvokeService;
import org.thingsboard.rule.engine.api.ScriptEngine;
import org.thingsboard.server.actors.ActorSystemContext;
import org.thingsboard.server.actors.tenant.DebugTbRateLimits;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Edge;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.Event;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.RuleNodeId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.page.TimePageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.permission.Resource;
import org.thingsboard.server.common.data.plugin.ComponentLifecycleEvent;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.rule.RuleChainType;
import org.thingsboard.server.common.data.rule.RuleNode;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgDataType;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.dao.event.EventService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.script.RuleNodeJsScriptEngine;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Slf4j
@RestController
@TbCoreComponent
@RequestMapping("/api")
public class RuleChainController extends BaseController {

    public static final String RULE_CHAIN_ID = "ruleChainId";
    public static final String RULE_NODE_ID = "ruleNodeId";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private EventService eventService;

    @Autowired
    private JsInvokeService jsInvokeService;

    @Autowired(required = false)
    private ActorSystemContext actorContext;

    @Value("${actors.rule.chain.debug_mode_rate_limits_per_tenant.enabled}")
    private boolean debugPerTenantEnabled;

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/ruleChain/{ruleChainId}", method = RequestMethod.GET)
    @ResponseBody
    public RuleChain getRuleChainById(@PathVariable(RULE_CHAIN_ID) String strRuleChainId) throws ThingsboardException {
        checkParameter(RULE_CHAIN_ID, strRuleChainId);
        try {
            RuleChainId ruleChainId = new RuleChainId(toUUID(strRuleChainId));
            return checkRuleChain(ruleChainId, Operation.READ);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/ruleChain/{ruleChainId}/metadata", method = RequestMethod.GET)
    @ResponseBody
    public RuleChainMetaData getRuleChainMetaData(@PathVariable(RULE_CHAIN_ID) String strRuleChainId) throws ThingsboardException {
        checkParameter(RULE_CHAIN_ID, strRuleChainId);
        try {
            RuleChainId ruleChainId = new RuleChainId(toUUID(strRuleChainId));
            checkRuleChain(ruleChainId, Operation.READ);
            return ruleChainService.loadRuleChainMetaData(getTenantId(), ruleChainId);
        } catch (Exception e) {
            throw handleException(e);
        }
    }


    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/ruleChain", method = RequestMethod.POST)
    @ResponseBody
    public RuleChain saveRuleChain(@RequestBody RuleChain ruleChain) throws ThingsboardException {
        try {
            boolean created = ruleChain.getId() == null;
            ruleChain.setTenantId(getCurrentUser().getTenantId());

            Operation operation = created ? Operation.CREATE : Operation.WRITE;

            accessControlService.checkPermission(getCurrentUser(), Resource.RULE_CHAIN, operation,
                    ruleChain.getId(), ruleChain);

            RuleChain savedRuleChain = checkNotNull(ruleChainService.saveRuleChain(ruleChain));

            tbClusterService.onEntityStateChange(ruleChain.getTenantId(), savedRuleChain.getId(),
                    created ? ComponentLifecycleEvent.CREATED : ComponentLifecycleEvent.UPDATED);

            logEntityAction(savedRuleChain.getId(), savedRuleChain,
                    null,
                    created ? ActionType.ADDED : ActionType.UPDATED, null);

            return savedRuleChain;
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.RULE_CHAIN), ruleChain,
                    null, ruleChain.getId() == null ? ActionType.ADDED : ActionType.UPDATED, e);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/ruleChain/{ruleChainId}/root", method = RequestMethod.POST)
    @ResponseBody
    public RuleChain setRootRuleChain(@PathVariable(RULE_CHAIN_ID) String strRuleChainId) throws ThingsboardException {
        checkParameter(RULE_CHAIN_ID, strRuleChainId);
        try {
            RuleChainId ruleChainId = new RuleChainId(toUUID(strRuleChainId));
            RuleChain ruleChain = checkRuleChain(ruleChainId, Operation.WRITE);
            TenantId tenantId = getCurrentUser().getTenantId();
            RuleChain previousRootRuleChain = ruleChainService.getRootTenantRuleChain(tenantId);
            if (ruleChainService.setRootRuleChain(getTenantId(), ruleChainId)) {
                if (previousRootRuleChain != null) {
                    previousRootRuleChain = ruleChainService.findRuleChainById(getTenantId(), previousRootRuleChain.getId());

                    tbClusterService.onEntityStateChange(previousRootRuleChain.getTenantId(), previousRootRuleChain.getId(),
                            ComponentLifecycleEvent.UPDATED);

                    logEntityAction(previousRootRuleChain.getId(), previousRootRuleChain,
                            null, ActionType.UPDATED, null);
                }
                ruleChain = ruleChainService.findRuleChainById(getTenantId(), ruleChainId);

                tbClusterService.onEntityStateChange(ruleChain.getTenantId(), ruleChain.getId(),
                        ComponentLifecycleEvent.UPDATED);

                logEntityAction(ruleChain.getId(), ruleChain,
                        null, ActionType.UPDATED, null);

            }
            return ruleChain;
        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.RULE_CHAIN),
                    null,
                    null,
                    ActionType.UPDATED, e, strRuleChainId);
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/ruleChain/metadata", method = RequestMethod.POST)
    @ResponseBody
    public RuleChainMetaData saveRuleChainMetaData(@RequestBody RuleChainMetaData ruleChainMetaData) throws ThingsboardException {
        try {
            TenantId tenantId = getTenantId();
            if (debugPerTenantEnabled) {
                ConcurrentMap<TenantId, DebugTbRateLimits> debugPerTenantLimits = actorContext.getDebugPerTenantLimits();
                DebugTbRateLimits debugTbRateLimits = debugPerTenantLimits.getOrDefault(tenantId, null);
                if (debugTbRateLimits != null) {
                    debugPerTenantLimits.remove(tenantId, debugTbRateLimits);
                }
            }

            RuleChain ruleChain = checkRuleChain(ruleChainMetaData.getRuleChainId(), Operation.WRITE);
            RuleChainMetaData savedRuleChainMetaData = checkNotNull(ruleChainService.saveRuleChainMetaData(tenantId, ruleChainMetaData));

            tbClusterService.onEntityStateChange(ruleChain.getTenantId(), ruleChain.getId(), ComponentLifecycleEvent.UPDATED);

            logEntityAction(ruleChain.getId(), ruleChain,
                    null,
                    ActionType.UPDATED, null, ruleChainMetaData);

            return savedRuleChainMetaData;
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.RULE_CHAIN), null,
                    null, ActionType.UPDATED, e, ruleChainMetaData);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/ruleChains", params = {"limit"}, method = RequestMethod.GET)
    @ResponseBody
    public TextPageData<RuleChain> getRuleChains(
            @RequestParam int limit,
            @RequestParam(value = "type", required = false) String typeStr,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String idOffset,
            @RequestParam(required = false) String textOffset) throws ThingsboardException {
        try {
            accessControlService.checkPermission(getCurrentUser(), Resource.RULE_CHAIN, Operation.READ);
            TenantId tenantId = getCurrentUser().getTenantId();
            TextPageLink pageLink = createPageLink(limit, textSearch, idOffset, textOffset);
            if (typeStr != null && typeStr.trim().length() > 0) {
                RuleChainType type = RuleChainType.valueOf(typeStr);
                return checkNotNull(ruleChainService.findTenantRuleChainsByType(tenantId, type, pageLink));
            } else {
                return checkNotNull(ruleChainService.findTenantRuleChainsByType(tenantId, RuleChainType.SYSTEM, pageLink));
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/ruleChain/{ruleChainId}", method = RequestMethod.DELETE)
    @ResponseStatus(value = HttpStatus.OK)
    public void deleteRuleChain(@PathVariable(RULE_CHAIN_ID) String strRuleChainId) throws ThingsboardException {
        checkParameter(RULE_CHAIN_ID, strRuleChainId);
        try {
            RuleChainId ruleChainId = new RuleChainId(toUUID(strRuleChainId));
            RuleChain ruleChain = checkRuleChain(ruleChainId, Operation.DELETE);

            List<RuleNode> referencingRuleNodes = ruleChainService.getReferencingRuleChainNodes(getTenantId(), ruleChainId);

            Set<RuleChainId> referencingRuleChainIds = referencingRuleNodes.stream().map(RuleNode::getRuleChainId).collect(Collectors.toSet());

            ruleChainService.deleteRuleChainById(getTenantId(), ruleChainId);

            referencingRuleChainIds.remove(ruleChain.getId());

            referencingRuleChainIds.forEach(referencingRuleChainId ->
                    tbClusterService.onEntityStateChange(ruleChain.getTenantId(), referencingRuleChainId, ComponentLifecycleEvent.UPDATED));

            tbClusterService.onEntityStateChange(ruleChain.getTenantId(), ruleChain.getId(), ComponentLifecycleEvent.DELETED);

            logEntityAction(ruleChainId, ruleChain,
                    null,
                    ActionType.DELETED, null, strRuleChainId);

        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.RULE_CHAIN),
                    null,
                    null,
                    ActionType.DELETED, e, strRuleChainId);
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/ruleNode/{ruleNodeId}/debugIn", method = RequestMethod.GET)
    @ResponseBody
    public JsonNode getLatestRuleNodeDebugInput(@PathVariable(RULE_NODE_ID) String strRuleNodeId) throws ThingsboardException {
        checkParameter(RULE_NODE_ID, strRuleNodeId);
        try {
            RuleNodeId ruleNodeId = new RuleNodeId(toUUID(strRuleNodeId));
            checkRuleNode(ruleNodeId, Operation.READ);
            TenantId tenantId = getCurrentUser().getTenantId();
            List<Event> events = eventService.findLatestEvents(tenantId, ruleNodeId, DataConstants.DEBUG_RULE_NODE, 2);
            JsonNode result = null;
            if (events != null) {
                for (Event event : events) {
                    JsonNode body = event.getBody();
                    if (body.has("type") && body.get("type").asText().equals("IN")) {
                        result = body;
                        break;
                    }
                }
            }
            return result;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/ruleChain/testScript", method = RequestMethod.POST)
    @ResponseBody
    public JsonNode testScript(@RequestBody JsonNode inputParams) throws ThingsboardException {
        try {
            String script = inputParams.get("script").asText();
            String scriptType = inputParams.get("scriptType").asText();
            JsonNode argNamesJson = inputParams.get("argNames");
            String[] argNames = objectMapper.treeToValue(argNamesJson, String[].class);

            String data = inputParams.get("msg").asText();
            JsonNode metadataJson = inputParams.get("metadata");
            Map<String, String> metadata = objectMapper.convertValue(metadataJson, new TypeReference<Map<String, String>>() {
            });
            String msgType = inputParams.get("msgType").asText();
            String output = "";
            String errorText = "";
            ScriptEngine engine = null;
            try {
                engine = new RuleNodeJsScriptEngine(jsInvokeService, getCurrentUser().getId(), script, argNames);
                TbMsg inMsg = TbMsg.newMsg(msgType, null, new TbMsgMetaData(metadata), TbMsgDataType.JSON, data);
                switch (scriptType) {
                    case "update":
                        output = msgToOutput(engine.executeUpdate(inMsg));
                        break;
                    case "generate":
                        output = msgToOutput(engine.executeGenerate(inMsg));
                        break;
                    case "filter":
                        boolean result = engine.executeFilter(inMsg);
                        output = Boolean.toString(result);
                        break;
                    case "switch":
                        Set<String> states = engine.executeSwitch(inMsg);
                        output = objectMapper.writeValueAsString(states);
                        break;
                    case "json":
                        JsonNode json = engine.executeJson(inMsg);
                        output = objectMapper.writeValueAsString(json);
                        break;
                    case "string":
                        output = engine.executeToString(inMsg);
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported script type: " + scriptType);
                }
            } catch (Exception e) {
                log.error("Error evaluating JS function", e);
                errorText = e.getMessage();
            } finally {
                if (engine != null) {
                    engine.destroy();
                }
            }
            ObjectNode result = objectMapper.createObjectNode();
            result.put("output", output);
            result.put("error", errorText);
            return result;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private String msgToOutput(TbMsg msg) throws Exception {
        ObjectNode msgData = objectMapper.createObjectNode();
        if (!StringUtils.isEmpty(msg.getData())) {
            msgData.set("msg", objectMapper.readTree(msg.getData()));
        }
        Map<String, String> metadata = msg.getMetaData().getData();
        msgData.set("metadata", objectMapper.valueToTree(metadata));
        msgData.put("msgType", msg.getType());
        return objectMapper.writeValueAsString(msgData);
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/edge/{edgeId}/ruleChain/{ruleChainId}", method = RequestMethod.POST)
    @ResponseBody
    public RuleChain assignRuleChainToEdge(@PathVariable("edgeId") String strEdgeId,
                                                @PathVariable(RULE_CHAIN_ID) String strRuleChainId) throws ThingsboardException {
        checkParameter("edgeId", strEdgeId);
        checkParameter(RULE_CHAIN_ID, strRuleChainId);
        try {
            EdgeId edgeId = new EdgeId(toUUID(strEdgeId));
            Edge edge = checkEdgeId(edgeId, Operation.READ);

            RuleChainId ruleChainId = new RuleChainId(toUUID(strRuleChainId));
            checkRuleChain(ruleChainId, Operation.ASSIGN_TO_EDGE);

            RuleChain savedRuleChain = checkNotNull(ruleChainService.assignRuleChainToEdge(getCurrentUser().getTenantId(), ruleChainId, edgeId));

            logEntityAction(ruleChainId, savedRuleChain,
                    null,
                    ActionType.ASSIGNED_TO_EDGE, null, strRuleChainId, savedRuleChain.getName(), strEdgeId, edge.getName());


            return savedRuleChain;
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.RULE_CHAIN), null,
                    null,
                    ActionType.ASSIGNED_TO_EDGE, e, strRuleChainId, strEdgeId);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/edge/{edgeId}/ruleChain/{ruleChainId}", method = RequestMethod.DELETE)
    @ResponseBody
    public RuleChain unassignRuleChainFromEdge(@PathVariable("edgeId") String strEdgeId,
                                                    @PathVariable(RULE_CHAIN_ID) String strRuleChainId) throws ThingsboardException {
        checkParameter("edgeId", strEdgeId);
        checkParameter(RULE_CHAIN_ID, strRuleChainId);
        try {
            EdgeId edgeId = new EdgeId(toUUID(strEdgeId));
            Edge edge = checkEdgeId(edgeId, Operation.READ);
            RuleChainId ruleChainId = new RuleChainId(toUUID(strRuleChainId));
            RuleChain ruleChain = checkRuleChain(ruleChainId, Operation.UNASSIGN_FROM_EDGE);

            RuleChain savedRuleChain = checkNotNull(ruleChainService.unassignRuleChainFromEdge(getCurrentUser().getTenantId(), ruleChainId, edgeId, false));

            logEntityAction(ruleChainId, ruleChain,
                    null,
                    ActionType.UNASSIGNED_FROM_EDGE, null, strRuleChainId, savedRuleChain.getName(), strEdgeId, edge.getName());

            return savedRuleChain;
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.RULE_CHAIN), null,
                    null,
                    ActionType.UNASSIGNED_FROM_EDGE, e, strRuleChainId);

            throw handleException(e);
        }
    }

//    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
//    @RequestMapping(value = "/ruleChain/{ruleChainId}/edges", method = RequestMethod.POST)
//    @ResponseBody
//    public RuleChain updateRuleChainEdges(@PathVariable(RULE_CHAIN_ID) String strRuleChainId,
//                                               @RequestBody String[] strEdgeIds) throws ThingsboardException {
//        checkParameter(RULE_CHAIN_ID, strRuleChainId);
//        try {
//            RuleChainId ruleChainId = new RuleChainId(toUUID(strRuleChainId));
//            RuleChain ruleChain = checkRuleChain(ruleChainId, Operation.ASSIGN_TO_EDGE);
//
//            Set<EntityGroupId> edgeIds = new HashSet<>();
//            if (strEdgeIds != null) {
//                for (String strEdgeId : strEdgeIds) {
//                    edgeIds.add(new EntityGroupId(toUUID(strEdgeId)));
//                }
//            }
//
//            Set<EntityGroupId> addedEdgeIds = new HashSet<>();
//            Set<EntityGroupId> removedEdgeIds = new HashSet<>();
//            for (EntityGroupId edgeId : edgeIds) {
//                if (!ruleChain.isAssignedToEdge(edgeId)) {
//                    addedEdgeIds.add(edgeId);
//                }
//            }
//
//            Set<ShortEdgeInfo> assignedEdges = ruleChain.getAssignedEdges();
//            if (assignedEdges != null) {
//                for (ShortEdgeInfo edgeInfo : assignedEdges) {
//                    if (!edgeIds.contains(edgeInfo.getEntityGroupId())) {
//                        removedEdgeIds.add(edgeInfo.getEntityGroupId());
//                    }
//                }
//            }
//
//            if (addedEdgeIds.isEmpty() && removedEdgeIds.isEmpty()) {
//                return ruleChain;
//            } else {
//                RuleChain savedRuleChain = null;
//                for (EntityGroupId edgeId : addedEdgeIds) {
//                    savedRuleChain = checkNotNull(ruleChainService.assignRuleChainToEdge(getCurrentUser().getTenantId(), ruleChainId, edgeId));
//                    ShortEdgeInfo edgeInfo = savedRuleChain.getAssignedEdgeInfo(edgeId);
//                    logEntityAction(ruleChainId, savedRuleChain,
//                            null,
//                            ActionType.ASSIGNED_TO_EDGE, null, strRuleChainId, savedRuleChain.getName(), edgeId.getId().toString(), edgeInfo.getName());
//                }
//                for (EntityGroupId edgeId : removedEdgeIds) {
//                    ShortEdgeInfo edgeInfo = ruleChain.getAssignedEdgeInfo(edgeId);
//                    savedRuleChain = checkNotNull(ruleChainService.unassignRuleChainFromEdge(getCurrentUser().getTenantId(), ruleChainId, edgeId, false));
//                    logEntityAction(ruleChainId, ruleChain,
//                            null,
//                            ActionType.UNASSIGNED_FROM_EDGE, null, strRuleChainId, savedRuleChain.getName(), edgeId.getId().toString(), edgeInfo.getName());
//
//                }
//                return savedRuleChain;
//            }
//        } catch (Exception e) {
//
//            logEntityAction(emptyId(EntityType.RULE_CHAIN), null,
//                    null,
//                    ActionType.ASSIGNED_TO_EDGE, e, strRuleChainId);
//
//            throw handleException(e);
//        }
//    }

//    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
//    @RequestMapping(value = "/ruleChain/{ruleChainId}/edges/add", method = RequestMethod.POST)
//    @ResponseBody
//    public RuleChain addRuleChainEdges(@PathVariable(RULE_CHAIN_ID) String strRuleChainId,
//                                            @RequestBody String[] strEdgeIds) throws ThingsboardException {
//        checkParameter(RULE_CHAIN_ID, strRuleChainId);
//        try {
//            RuleChainId ruleChainId = new RuleChainId(toUUID(strRuleChainId));
//            RuleChain ruleChain = checkRuleChain(ruleChainId, Operation.ASSIGN_TO_EDGE);
//
//            Set<EntityGroupId> edgeIds = new HashSet<>();
//            if (strEdgeIds != null) {
//                for (String strEdgeId : strEdgeIds) {
//                    EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEdgeId));
//                    if (!ruleChain.isAssignedToEdge(entityGroupId)) {
//                        edgeIds.add(entityGroupId);
//                    }
//                }
//            }
//
//            if (edgeIds.isEmpty()) {
//                return ruleChain;
//            } else {
//                RuleChain savedRuleChain = null;
//                for (EntityGroupId edgeId : edgeIds) {
//                    savedRuleChain = checkNotNull(ruleChainService.assignRuleChainToEdge(getCurrentUser().getTenantId(), ruleChainId, edgeId));
//                    ShortEdgeInfo edgeInfo = savedRuleChain.getAssignedEdgeInfo(edgeId);
//                    logEntityAction(ruleChainId, savedRuleChain,
//                            null,
//                            ActionType.ASSIGNED_TO_EDGE, null, strRuleChainId, savedRuleChain.getName(), edgeId.getId().toString(), edgeInfo.getName());
//                }
//                return savedRuleChain;
//            }
//        } catch (Exception e) {
//
//            logEntityAction(emptyId(EntityType.RULE_CHAIN), null,
//                    null,
//                    ActionType.ASSIGNED_TO_EDGE, e, strRuleChainId);
//
//            throw handleException(e);
//        }
//    }
//
//    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
//    @RequestMapping(value = "/ruleChain/{ruleChainId}/edges/remove", method = RequestMethod.POST)
//    @ResponseBody
//    public RuleChain removeRuleChainEdges(@PathVariable(RULE_CHAIN_ID) String strRuleChainId,
//                                               @RequestBody String[] strEdgeIds) throws ThingsboardException {
//        checkParameter(RULE_CHAIN_ID, strRuleChainId);
//        try {
//            RuleChainId ruleChainId = new RuleChainId(toUUID(strRuleChainId));
//            RuleChain ruleChain = checkRuleChain(ruleChainId, Operation.UNASSIGN_FROM_EDGE);
//
//            Set<EntityGroupId> edgeIds = new HashSet<>();
//            if (strEdgeIds != null) {
//                for (String strEdgeId : strEdgeIds) {
//                    EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEdgeId));
//                    if (ruleChain.isAssignedToEdge(entityGroupId)) {
//                        edgeIds.add(entityGroupId);
//                    }
//                }
//            }
//
//            if (edgeIds.isEmpty()) {
//                return ruleChain;
//            } else {
//                RuleChain savedRuleChain = null;
//                for (EntityGroupId entityGroupId : edgeIds) {
//                    ShortEdgeInfo edgeInfo = ruleChain.getAssignedEdgeInfo(entityGroupId);
//                    savedRuleChain = checkNotNull(ruleChainService.unassignRuleChainFromEdge(getCurrentUser().getTenantId(), ruleChainId, entityGroupId, false));
//                    logEntityAction(ruleChainId, ruleChain,
//                            null,
//                            ActionType.UNASSIGNED_FROM_EDGE, null, strRuleChainId, savedRuleChain.getName(), entityGroupId.getId().toString(), edgeInfo.getName());
//
//                }
//                return savedRuleChain;
//            }
//        } catch (Exception e) {
//
//            logEntityAction(emptyId(EntityType.RULE_CHAIN), null,
//                    null,
//                    ActionType.UNASSIGNED_FROM_EDGE, e, strRuleChainId);
//
//            throw handleException(e);
//        }
//    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/edge/{edgeId}/ruleChains", params = { "limit" }, method = RequestMethod.GET)
    @ResponseBody
    public TimePageData<RuleChain> getEdgeRuleChains(
            @PathVariable("edgeId") String strEdgeId,
            @RequestParam int limit,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false, defaultValue = "false") boolean ascOrder,
            @RequestParam(required = false) String offset) throws ThingsboardException {
        checkParameter("edgeId", strEdgeId);
        try {
            TenantId tenantId = getCurrentUser().getTenantId();
            EdgeId edgeId = new EdgeId(toUUID(strEdgeId));
            checkEdgeId(edgeId, Operation.READ);
            TimePageLink pageLink = createPageLink(limit, startTime, endTime, ascOrder, offset);
            return checkNotNull(ruleChainService.findRuleChainsByTenantIdAndEdgeId(tenantId, edgeId, pageLink).get());
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/ruleChain/{ruleChainId}/defaultRootEdge", method = RequestMethod.POST)
    @ResponseBody
    public RuleChain setDefaultRootEdgeRuleChain(@PathVariable(RULE_CHAIN_ID) String strRuleChainId) throws ThingsboardException {
        checkParameter(RULE_CHAIN_ID, strRuleChainId);
        try {
            RuleChainId ruleChainId = new RuleChainId(toUUID(strRuleChainId));
            RuleChain ruleChain = checkRuleChain(ruleChainId, Operation.WRITE);
            ruleChainService.setDefaultRootEdgeRuleChain(getTenantId(), ruleChainId);
            return ruleChain;
        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.RULE_CHAIN),
                    null,
                    null,
                    ActionType.UPDATED, e, strRuleChainId);
            throw handleException(e);
        }
    }
}
