/**
 * Thingsboard OÜ ("COMPANY") CONFIDENTIAL
 * <p>
 * Copyright © 2016-2017 Thingsboard OÜ. All Rights Reserved.
 * <p>
 * NOTICE: All information contained herein is, and remains
 * the property of Thingsboard OÜ and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Thingsboard OÜ
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * <p>
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 * <p>
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 * <p>
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
package org.thingsboard.server.service.integration.thingpark;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.thingsboard.server.service.converter.UplinkData;
import org.thingsboard.server.service.converter.UplinkMetaData;
import org.thingsboard.server.service.integration.IntegrationContext;
import org.thingsboard.server.service.integration.http.AbstractHttpIntegration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ashvayka on 02.12.17.
 */
@Slf4j
public class ThingParkIntegration extends AbstractHttpIntegration<ThingParkIntegrationMsg> {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void process(IntegrationContext context, ThingParkIntegrationMsg msg) {
        try {
            if (checkSecurity(msg)) {
                List<UplinkData> uplinkDataList = convertToUplinkDataList(msg);
                if (uplinkDataList != null) {
                    for (UplinkData data : uplinkDataList) {
                        processUplinkData(context, data);
                        log.info("[{}] Processing uplink data", data);
                    }
                }
                msg.getCallback().setResult(new ResponseEntity<>(HttpStatus.OK));
            } else {
                msg.getCallback().setResult(new ResponseEntity<>(HttpStatus.FORBIDDEN));
            }
        } catch (Exception e) {
            msg.getCallback().setResult(new ResponseEntity<>(e, HttpStatus.INTERNAL_SERVER_ERROR));
            log.warn("Failed to apply data converter function", e);
        }
    }

    private boolean checkSecurity(ThingParkIntegrationMsg msg) {
        return true;
//        return !StringUtils.isEmpty(msg.getParams().getAsId());
    }

    private List<UplinkData> convertToUplinkDataList(ThingParkIntegrationMsg msg) throws Exception {
        byte[] data = mapper.writeValueAsBytes(msg.getMsg());
        Map<String, String> mdMap = new HashMap<>(metadata.getKvMap());
        ThingParkRequestParameters params = msg.getParams();
        mdMap.put("LrnDevEui", params.getLrnDevEui());
        mdMap.put("LrnFPort", params.getLrnFPort());
        return this.converter.convertUplink(data, new UplinkMetaData(mdMap));
    }

}
