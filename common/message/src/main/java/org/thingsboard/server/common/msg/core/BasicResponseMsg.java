/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2018 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.common.msg.core;

import java.io.Serializable;
import java.util.Optional;

import org.thingsboard.server.common.msg.session.SessionMsgType;
import org.thingsboard.server.common.msg.session.SessionMsgType;


public class BasicResponseMsg<T extends Serializable> implements ResponseMsg<T> {

    private static final long serialVersionUID = 1L;

    private final SessionMsgType requestMsgType;
    private final Integer requestId;
    private final SessionMsgType sessionMsgType;
    private final boolean success;
    private final T data;
    private final Exception error;

    protected BasicResponseMsg(SessionMsgType requestMsgType, Integer requestId, SessionMsgType sessionMsgType, boolean success, Exception error, T data) {
        super();
        this.requestMsgType = requestMsgType;
        this.requestId = requestId;
        this.sessionMsgType = sessionMsgType;
        this.success = success;
        this.error = error;
        this.data = data;
    }

    @Override
    public SessionMsgType getRequestMsgType() {
        return requestMsgType;
    }

    @Override
    public Integer getRequestId() {
        return requestId;
    }

    @Override
    public boolean isSuccess() {
        return success;
    }

    @Override
    public Optional<Exception> getError() {
        return Optional.ofNullable(error);
    }

    @Override
    public Optional<T> getData() {
        return Optional.ofNullable(data);
    }

    @Override
    public String toString() {
        return "BasicResponseMsg [success=" + success + ", data=" + data + ", error=" + error + "]";
    }

    public SessionMsgType getSessionMsgType() {
        return sessionMsgType;
    }
}
