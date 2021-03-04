/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.rule.engine.api.msg.DeviceNameOrTypeUpdateMsg;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.lwm2m.LwM2mObject;
import org.thingsboard.server.common.data.lwm2m.ServerSecurityConfig;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.plugin.ComponentLifecycleEvent;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.queue.util.TbCoreComponent;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@TbCoreComponent
@RequestMapping("/api")
public class DeviceLwm2mController extends BaseController {

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/lwm2m/deviceProfile",  params = {"sortOrder", "sortProperty"},  method = RequestMethod.GET)
    @ResponseBody
    public List<LwM2mObject> getLwm2mListObjects(@RequestParam String sortOrder,
                                                 @RequestParam String sortProperty,
                                                 @RequestParam(required = false) int[] objectIds,
                                                 @RequestParam(required = false) String searchText)
                                                 throws ThingsboardException {
        try {
            return lwM2MModelsRepository.getLwm2mObjects(objectIds, searchText, sortProperty, sortOrder);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/lwm2m/deviceProfile/objects", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<LwM2mObject> getLwm2mListObjects(@RequestParam int pageSize,
                                                     @RequestParam int page,
                                                     @RequestParam(required = false) String searchText,
                                                     @RequestParam(required = false) String sortProperty,
                                                     @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        try {
            PageLink pageLink = createPageLink(pageSize, page, searchText, sortProperty, sortOrder);
            return checkNotNull(lwM2MModelsRepository.findDeviceLwm2mObjects(getTenantId(), pageLink));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/lwm2m/deviceProfile/bootstrap/{securityMode}/{bootstrapServerIs}", method = RequestMethod.GET)
    @ResponseBody
    public ServerSecurityConfig getLwm2mBootstrapSecurityInfo(@PathVariable("securityMode") String securityMode,
                                                              @PathVariable("bootstrapServerIs") boolean bootstrapServerIs) throws ThingsboardException {
        try {
            return lwM2MModelsRepository.getBootstrapSecurityInfo(securityMode, bootstrapServerIs);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/lwm2m/device-credentials", method = RequestMethod.POST)
    @ResponseBody
    public Device saveDeviceWithCredentials(@RequestBody (required=false) Map<Class<?>, Object> deviceWithDeviceCredentials,
                                            @RequestParam(name = "entityGroupId", required = false) String strEntityGroupId) throws ThingsboardException {
        ObjectMapper mapper = new ObjectMapper();
        Device device = checkNotNull(mapper.convertValue(deviceWithDeviceCredentials.get(Device.class), Device.class));
        DeviceCredentials credentials = checkNotNull(mapper.convertValue( deviceWithDeviceCredentials.get(DeviceCredentials.class), DeviceCredentials.class));
        try {
            Device savedDevice = saveGroupEntity(device, strEntityGroupId,
                    device1 -> deviceService.saveDeviceWithCredentials(device1, credentials));

            tbClusterService.onDeviceChange(savedDevice, null);
            tbClusterService.pushMsgToCore(new DeviceNameOrTypeUpdateMsg(savedDevice.getTenantId(),
                    savedDevice.getId(), savedDevice.getName(), savedDevice.getType()), null);
            tbClusterService.onEntityStateChange(savedDevice.getTenantId(), savedDevice.getId(),
                    device.getId() == null ? ComponentLifecycleEvent.CREATED : ComponentLifecycleEvent.UPDATED);

            if (device.getId() == null) {
                deviceStateService.onDeviceAdded(savedDevice);
            } else {
                deviceStateService.onDeviceUpdated(savedDevice);
            }
            return savedDevice;
        } catch (Exception e) {
            throw handleException(e);
        }
    }
}
