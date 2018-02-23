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
package org.thingsboard.server.service.integration.http.sigfox;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.service.converter.DownLinkMetaData;
import org.thingsboard.server.service.converter.DownlinkData;
import org.thingsboard.server.service.converter.UplinkData;
import org.thingsboard.server.service.integration.IntegrationContext;
import org.thingsboard.server.service.integration.TbIntegrationInitParams;
import org.thingsboard.server.service.integration.downlink.DownLinkMsg;
import org.thingsboard.server.service.integration.http.HttpIntegrationMsg;
import org.thingsboard.server.service.integration.http.basic.BasicHttpIntegration;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@Slf4j
public class SigFoxIntegration extends BasicHttpIntegration {

    @Override
    public void init(TbIntegrationInitParams params) throws Exception {
        super.init(params);
    }

    @Override
    protected ResponseEntity doProcess(IntegrationContext context, HttpIntegrationMsg msg) throws Exception {
        if (checkSecurity(msg)) {
            Map<Device, UplinkData> result = processUplinkData(context, msg);
            if (result.isEmpty()) {
                return fromStatus(HttpStatus.NO_CONTENT);
            } else if (result.size() > 1) {
                return fromStatus(HttpStatus.BAD_REQUEST);
            } else {
                Entry<Device, UplinkData> entry = result.entrySet().stream().findFirst().get();
                String deviceIdAttributeName = metadataTemplate.getKvMap().getOrDefault("SigFoxDeviceIdAttributeName", "device");
                String sigFoxDeviceId = msg.getMsg().get(deviceIdAttributeName).asText();
                return processDownLinkData(context, entry.getKey(), msg, sigFoxDeviceId);
            }
        } else {
            return fromStatus(HttpStatus.FORBIDDEN);
        }
    }

    private ResponseEntity processDownLinkData(IntegrationContext context, Device device, HttpIntegrationMsg msg, String sigFoxDeviceId) throws Exception {
        if (downlinkConverter != null) {
            DownLinkMsg pending = context.getDownlinkService().get(configuration.getId(), device.getId());
            if (pending != null) {
                Map<String, String> mdMap = new HashMap<>(metadataTemplate.getKvMap());
                msg.getRequestHeaders().forEach(
                        (header, value) -> {
                            mdMap.put("header:" + header, value);
                        }
                );
                List<DownlinkData> result = downlinkConverter.convertDownLink(context.getConverterContext(), Collections.singletonList(pending), new DownLinkMetaData(mdMap));
                context.getDownlinkService().remove(configuration.getId(), device.getId());
                if (result.size() == 1 && !result.get(0).isEmpty()) {
                    DownlinkData downlink = result.get(0);
                    ObjectNode json = mapper.createObjectNode();
                    json.putObject(sigFoxDeviceId).put("downlinkData", new String(downlink.getData(), StandardCharsets.UTF_8));
                    HttpHeaders responseHeaders = new HttpHeaders();
                    responseHeaders.add("Content-Type", "application/json");
                    ResponseEntity response = new ResponseEntity(json, responseHeaders, HttpStatus.OK);
                    logDownlink(context, "Downlink", response);
                    return response;
                }
            }
        }

        return fromStatus(HttpStatus.NO_CONTENT);
    }

}
