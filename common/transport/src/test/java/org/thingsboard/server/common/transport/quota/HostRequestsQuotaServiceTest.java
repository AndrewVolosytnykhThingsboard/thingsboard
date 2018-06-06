/**
 * Thingsboard OÜ ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2018 Thingsboard OÜ. All Rights Reserved.
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
package org.thingsboard.server.common.transport.quota;

import org.junit.Before;
import org.junit.Test;
import org.thingsboard.server.common.transport.quota.host.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * @author Vitaliy Paromskiy
 * @version 1.0
 */
public class HostRequestsQuotaServiceTest {

    private HostRequestsQuotaService quotaService;

    private HostRequestIntervalRegistry requestRegistry = mock(HostRequestIntervalRegistry.class);
    private HostRequestLimitPolicy requestsPolicy = mock(HostRequestLimitPolicy.class);
    private HostIntervalRegistryCleaner registryCleaner = mock(HostIntervalRegistryCleaner.class);
    private HostIntervalRegistryLogger registryLogger = mock(HostIntervalRegistryLogger.class);

    @Before
    public void init() {
        quotaService = new HostRequestsQuotaService(requestRegistry, requestsPolicy, registryCleaner, registryLogger, true);
    }

    @Test
    public void quotaExceededIfRequestCountBiggerThanAllowed() {
        when(requestRegistry.tick("key")).thenReturn(10L);
        when(requestsPolicy.isValid(10L)).thenReturn(false);

        assertTrue(quotaService.isQuotaExceeded("key"));

        verify(requestRegistry).tick("key");
        verify(requestsPolicy).isValid(10L);
        verifyNoMoreInteractions(requestRegistry, requestsPolicy);
    }

    @Test
    public void quotaNotExceededIfRequestCountLessThanAllowed() {
        when(requestRegistry.tick("key")).thenReturn(10L);
        when(requestsPolicy.isValid(10L)).thenReturn(true);

        assertFalse(quotaService.isQuotaExceeded("key"));

        verify(requestRegistry).tick("key");
        verify(requestsPolicy).isValid(10L);
        verifyNoMoreInteractions(requestRegistry, requestsPolicy);
    }

    @Test
    public void serviceCanBeDisabled() {
        quotaService = new HostRequestsQuotaService(requestRegistry, requestsPolicy, registryCleaner, registryLogger, false);
        assertFalse(quotaService.isQuotaExceeded("key"));
        verifyNoMoreInteractions(requestRegistry, requestsPolicy);
    }
}