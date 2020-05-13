///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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

import { IntegrationType, IntegrationTypeInfo } from '@shared/models/integration.models';
import { baseUrl } from '@app/core/utils';
import { FormGroup } from '@angular/forms';

export const handlerConfigurationTypes = {
  text: {
    value: 'TEXT',
    name: 'extension.text'
  },
  binary: {
    value: 'BINARY',
    name: 'extension.binary'
  },
  json: {
    value: 'JSON',
    name: 'extension.json'
  },
  hex: {
    value: 'HEX',
    name: 'extension.hex'
  }
}

export const tcpBinaryByteOrder = {
  littleEndian: {
    value: 'LITTLE_ENDIAN'
  },
  bigEndian: {
    value: 'BIG_ENDIAN'
  }
}

export const tcpTextMessageSeparator = {
  systemLineSeparator: {
    value: 'SYSTEM_LINE_SEPARATOR'
  },
  nulDelimiter: {
    value: 'NUL_DELIMITER'
  }
}

export const opcSecurityTypes = {
  Basic128Rsa15: 'Basic128Rsa15',
  Basic256: 'Basic256',
  Basic256Sha256: 'Basic256Sha256',
  None: 'None'
}

export type mqttCredentialType = 'anonymous' | 'basic' | 'cert.PEM';

export const mqttCredentialTypes = {
  anonymous: {
    value: 'anonymous',
    name: 'extension.anonymous'
  },
  basic: {
    value: 'basic',
    name: 'extension.basic'
  },
  'cert.PEM': {
    value: 'cert.PEM',
    name: 'extension.pem'
  }
}

export function updateIntegrationFormState(type: IntegrationType, info: IntegrationTypeInfo,
                                           integrationForm: FormGroup, disabled: boolean) {
  if (disabled) {
    integrationForm.disable({emitEvent: false});
  } else {
    integrationForm.enable({emitEvent: false});
    if (info.http) {
      integrationForm.get('httpEndpoint').disable({emitEvent: false});
    } else if (type === IntegrationType.TTN) {
      integrationForm.get('topicFilters').disable({emitEvent: false});
    }
  }
}

export const templates = {
  http: {
    baseUrl: baseUrl(),
    replaceNoContentToOk: '',
    enableSecurity: '',
    downlinkUrl: 'https://api.thingpark.com/thingpark/lrc/rest/downlink',
    enableSecurityNew: '',
    asId: '',
    asIdNew: '',
    asKey: '',
    clientIdNew: '',
    clientSecret: '',
    maxTimeDiffInSeconds: 60,
    httpEndpoint: '',
    headersFilter: '',
    ignoreNonPrimitiveFields: ['headersFilter']
  },
  [IntegrationType.MQTT]: {
    clientConfiguration: {
      host: 'localhost',
      port: 11883,
      cleanSession: true,
      ssl: false,
      connectTimeoutSec: 10,
      clientId: '',
      credentials: {
        type: mqttCredentialTypes.anonymous.value,
        username: '',
        password: '',
        caCertFileName: '',
        caCert: '',
        certFileName: '',
        cert: '',
        privateKeyFileName: '',
        privateKey: '',
        privateKeyPassword: ''
      },
    },
    downlinkTopicPattern: '${topic}',
    topicFilters: []
  },
  [IntegrationType.AWS_IOT]: {
    clientConfiguration: {
      host: '',
      port: 8883,
      clientId: '',
      connectTimeoutSec: 10,
      ssl: true,
      credentials: {
        type: 'cert.PEM',
        caCertFileName: '',
        caCert: '',
        certFileName: '',
        cert: '',
        privateKeyFileName: '',
        privateKey: '',
        password: ''
      }
    },
    downlinkTopicPattern: '${topic}',
    topicFilters: []
  },
  [IntegrationType.AWS_SQS]: {
    sqsConfiguration: {
      queueUrl: '',
      pollingPeriodSeconds: 5,
      region: 'us-west-2',
      accessKeyId: '',
      secretAccessKey: ''
    }
  },
  [IntegrationType.AWS_KINESIS]: {
    streamName: '',
    region: '',
    accessKeyId: '',
    secretAccessKey: '',
    useCredentialsFromInstanceMetadata: false,
    applicationName: '',
    initialPositionInStream: '',
    useConsumersWithEnhancedFanOut: false
  },
  [IntegrationType.IBM_WATSON_IOT]: {
    clientConfiguration: {
      connectTimeoutSec: 10,
      host: '',
      port: 8883,
      ssl: true,
      cleanSession: true,
      credentials: {
        type: 'basic',
        username: '',
        password: '',
      }
    },
    topicFilters: [{
      filter: 'iot-2/type/+/id/+/evt/+/fmt/+',
      qos: 0
    }],
    downlinkTopicPattern: 'iot-2/type/${device_type}/id/${device_id}/cmd/${command_id}/fmt/${format}'

  },
  [IntegrationType.TTN]: {
    clientConfiguration: {
      host: '',
      customHost: false,
      port: 8883,
      ssl: true,
      connectTimeoutSec: 10,
      credentials: {
        type: 'basic',
        username: '',
        password: ''
      },
    },
    topicFilters: [{
      filter: '+/devices/+/up',
      qos: 0
    }],
    downlinkTopicPattern: ''
  },
  [IntegrationType.AZURE_EVENT_HUB]: {
    connectTimeoutSec: 10,
    namespaceName: '',
    eventHubName: '',
    sasKeyName: '',
    sasKey: '',
    iotHubName: '',
  },
  [IntegrationType.OPC_UA]: {
    clientConfiguration: {
      applicationName: '',
      applicationUri: '',
      host: 'localhost',
      port: 49320,
      scanPeriodInSeconds: 10,
      timeoutInMillis: 5000,
      security: opcSecurityTypes.Basic128Rsa15,
      identity: {
        password: '',
        username: '',
        type: 'anonymous'
      },
      mapping: [],
      keystore: {
        location: '',
        type: '',
        fileContent: '',
        password: 'secret',
        alias: 'opc-ua-extension',
        keyPassword: 'secret',
      }
    }
  },
  [IntegrationType.UDP]: {
    clientConfiguration: {
      port: 11560,
      soBroadcast: true,
      soRcvBuf: 64,
      handlerConfiguration: {
        handlerType: handlerConfigurationTypes.binary.value,
        charsetName: 'UTF-8',
        maxFrameLength: 128
      }
    }
  },
  [IntegrationType.TCP]: {
    clientConfiguration: {
      port: 10560,
      soBacklogOption: 128,
      soRcvBuf: 64,
      soSndBuf: 64,
      soKeepaliveOption: false,
      tcpNoDelay: true,
      handlerConfiguration: {
        handlerType: handlerConfigurationTypes.binary.value,
        byteOrder: tcpBinaryByteOrder.littleEndian.value,
        maxFrameLength: 128,
        lengthFieldOffset: 0,
        lengthFieldLength: 2,
        lengthAdjustment: 0,
        initialBytesToStrip: 0,
        failFast: false,
        stripDelimiter: true,
        messageSeparator: tcpTextMessageSeparator.systemLineSeparator.value
      }
    }
  },
  [IntegrationType.KAFKA]: {
    clientConfiguration: {
      groupId: '',
      clientId: '',
      topics: 'my-topic-output',
      bootstrapServers: 'localhost:9092',
      pollInterval: 5000,
      autoCreateTopics: false,
      otherProperties: ''
    }
  },
  [IntegrationType.CUSTOM]: {
    clazz: '',
    configuration: ''
  }
}

export const opcUaMappingType = {
  ID: 'ID',
  FQN: 'Fully Qualified Name'
}

export const extensionKeystoreType = {
  PKCS12: 'PKCS12',
  JKS: 'JKS'
}

export const initialPositionInStream = {
  latest: 'LATEST',
  trim_horizon: 'TRIM_HORIZON',
  at_timestamp: 'AT_TIMESTAMP'
}

export const topicFilters = {}

export const identityType = {
  anonymous: 'extension.anonymous',
  username: 'extension.username'
}

export const mqttQoSTypes = [
  {
    value: 0,
    name: 'integration.mqtt-qos-at-most-once'
  },
  {
    value: 1,
    name: 'integration.mqtt-qos-at-least-once'
  },
  {
    value: 2,
    name: 'integration.mqtt-qos-exactly-once'
  }]
