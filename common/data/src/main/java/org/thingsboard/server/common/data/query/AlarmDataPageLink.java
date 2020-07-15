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
package org.thingsboard.server.common.data.query;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import org.thingsboard.server.common.data.alarm.AlarmSearchStatus;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.alarm.AlarmStatus;

import java.util.List;

@Data
@AllArgsConstructor
public class AlarmDataPageLink extends EntityDataPageLink {

    private long startTs;
    private long endTs;
    //TODO: handle this;
    private long timeWindow;
    private List<String> typeList;
    private List<AlarmSearchStatus> statusList;
    private List<AlarmSeverity> severityList;
    private boolean searchPropagatedAlarms;

    public AlarmDataPageLink() {
        super();
    }

    public AlarmDataPageLink(int pageSize, int page, String textSearch, EntityDataSortOrder sortOrder, boolean dynamic,
                             boolean searchPropagatedAlarms,
                             long startTs, long endTs, long timeWindow,
                             List<String> typeList, List<AlarmSearchStatus> statusList, List<AlarmSeverity> severityList) {
        super(pageSize, page, textSearch, sortOrder, dynamic);
        this.searchPropagatedAlarms = searchPropagatedAlarms;
        this.startTs = startTs;
        this.endTs = endTs;
        this.timeWindow = timeWindow;
        this.typeList = typeList;
        this.statusList = statusList;
        this.severityList = severityList;
    }

    @JsonIgnore
    public AlarmDataPageLink nextPageLink() {
        return new AlarmDataPageLink(this.getPageSize(), this.getPage() + 1, this.getTextSearch(), this.getSortOrder(), this.isDynamic(),
                this.searchPropagatedAlarms,
                this.startTs, this.endTs, this.timeWindow,
                this.typeList, this.statusList, this.severityList
        );
    }
}
