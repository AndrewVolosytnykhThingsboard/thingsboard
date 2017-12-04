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
package org.thingsboard.server.service.integration.oc;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.service.converter.UplinkData;
import org.thingsboard.server.service.converter.UplinkMetaData;
import org.thingsboard.server.service.integration.http.AbstractHttpIntegration;
import org.thingsboard.server.service.integration.http.HttpIntegrationMsg;

import java.util.Collections;
import java.util.List;

/**
 * Created by ashvayka on 02.12.17.
 */
@Slf4j
public class OceanConnectIntegration extends AbstractHttpIntegration {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void process(HttpIntegrationMsg msg) {
        List<UplinkData> uplinkDataList = convertToUplinkDataList(msg);

        if (uplinkDataList != null) {
            for (UplinkData data : uplinkDataList) {
                log.info("[{}] Processing uplink data", data);
            }
        }
    }

    private List<UplinkData> convertToUplinkDataList(HttpIntegrationMsg msg) {
        List<UplinkData> uplinkDataList = null;
        try {
            byte[] data = mapper.writeValueAsBytes(msg.getMsg());
            UplinkMetaData md = new UplinkMetaData(Collections.singletonMap("integrationName", configuration.getName()));
            uplinkDataList = this.converter.convertUplink(data, md);
        } catch (Exception e) {
            log.warn("Failed to apply data converter function", e);
        }
        return uplinkDataList;
    }
}
