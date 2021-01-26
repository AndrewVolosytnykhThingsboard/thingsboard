///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
///
/// NOTICE: All information contained herein is, and remains
/// the property of ThingsBoard, Inc. and its suppliers,
/// if any.  The intellectual and technical concepts contained
/// herein are proprietary to ThingsBoard, Inc.
/// and its suppliers and may be covered by U.S. and Foreign Patents,
/// patents in process, and are protected by trade secret or copyright law.
///
/// Dissemination of this information or reproduction of this material is strictly forbidden
/// unless prior written permission is obtained from COMPANY.
///
/// Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
/// managers or contractors who have executed Confidentiality and Non-disclosure agreements
/// explicitly covering such access.
///
/// The copyright notice above does not evidence any actual or intended publication
/// or disclosure  of  this source code, which includes
/// information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
/// ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
/// OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
/// THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
/// AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
/// THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
/// DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
/// OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
///

export const JSON_ALL_CONFIG = 'jsonAllConfig';
export const END_POINT = 'endPoint';
export const DEFAULT_END_POINT = 'default_client_lwm2m_end_point_no_sec';
export const BOOTSTRAP_SERVERS = 'servers';
export const BOOTSTRAP_SERVER = 'bootstrapServer';
export const LWM2M_SERVER = 'lwm2mServer';
export const JSON_OBSERVE = 'jsonObserve';
export const LEN_MAX_PSK = 64;
export const LEN_MAX_PRIVATE_KEY = 134;
export const LEN_MAX_PUBLIC_KEY_RPK = 182;
export const LEN_MAX_PUBLIC_KEY_X509 = 3000;
export const KEY_IDENT_REGEXP_PSK = /^[0-9a-fA-F]{64,64}$/;
export const KEY_PRIVATE_REGEXP = /^[0-9a-fA-F]{134,134}$/;
export const KEY_PUBLIC_REGEXP_PSK = /^[0-9a-fA-F]{182,182}$/;
export const KEY_PUBLIC_REGEXP_X509 = /^[0-9a-fA-F]{0,3000}$/;

export interface DeviceCredentialsDialogLwm2mData {
  jsonAllConfig?: SecurityConfigModels;
  endPoint?: string;
  isNew?: boolean;
}

export enum SECURITY_CONFIG_MODE {
  PSK = 'PSK',
  RPK = 'RPK',
  X509 = 'X509',
  NO_SEC = 'NO_SEC'
}

export const SECURITY_CONFIG_MODE_NAMES = new Map<SECURITY_CONFIG_MODE, string>(
  [
    [SECURITY_CONFIG_MODE.PSK, 'Pre-Shared Key'],
    [SECURITY_CONFIG_MODE.RPK, 'Raw Public Key'],
    [SECURITY_CONFIG_MODE.X509, 'X.509 Certificate'],
    [SECURITY_CONFIG_MODE.NO_SEC, 'No Security'],
  ]
);

export type ClientSecurityConfigType =
  ClientSecurityConfigPSK
  | ClientSecurityConfigRPK
  | ClientSecurityConfigX509
  | ClientSecurityConfigNO_SEC;

export interface ClientSecurityConfigPSK {
  securityConfigClientMode: string,
  endpoint: string,
  identity: string,
  key: string
}

export interface ClientSecurityConfigRPK {
  securityConfigClientMode: string,
  key: string
}

export interface ClientSecurityConfigX509 {
  securityConfigClientMode: string,
  x509: boolean
}

export interface ClientSecurityConfigNO_SEC {
  securityConfigClientMode: string
}

export interface ServerSecurityConfig {
  securityMode: string,
  clientPublicKeyOrId?: string,
  clientSecretKey?: string
}

interface BootstrapSecurityConfig {
  bootstrapServer: ServerSecurityConfig,
  lwm2mServer: ServerSecurityConfig
}

export interface SecurityConfigModels {
  client: ClientSecurityConfigType,
  bootstrap: BootstrapSecurityConfig
}

export function getDefaultClientSecurityConfigType(securityConfigMode: SECURITY_CONFIG_MODE, endPoint?: string): ClientSecurityConfigType {
  let security: ClientSecurityConfigType;
  switch (securityConfigMode) {
    case SECURITY_CONFIG_MODE.PSK:
      security = {
        securityConfigClientMode: '',
        endpoint: endPoint,
        identity: endPoint,
        key: ''
      }
      break;
    case SECURITY_CONFIG_MODE.RPK:
      security = {
        securityConfigClientMode: '',
        key: ''
      }
      break;
    case SECURITY_CONFIG_MODE.X509:
      security = {
        securityConfigClientMode: '',
        x509: true
      }
      break;
    case SECURITY_CONFIG_MODE.NO_SEC:
      security = {
        securityConfigClientMode: ''
      }
      break;
  }
  security.securityConfigClientMode = securityConfigMode.toString();
  return security;
}

export function getDefaultServerSecurityConfig(): ServerSecurityConfig {
  return {
    securityMode: SECURITY_CONFIG_MODE.NO_SEC.toString(),
    clientPublicKeyOrId: '',
    clientSecretKey: ''
  }
}

function getDefaultBootstrapSecurityConfig(): BootstrapSecurityConfig {
  return {
    bootstrapServer: getDefaultServerSecurityConfig(),
    lwm2mServer:  getDefaultServerSecurityConfig()
  }
}

export function getDefaultSecurityConfig(): SecurityConfigModels {
  const securityConfigModels = {
    client: getDefaultClientSecurityConfigType(SECURITY_CONFIG_MODE.NO_SEC),
    bootstrap: getDefaultBootstrapSecurityConfig()
  };
  return securityConfigModels;
}


