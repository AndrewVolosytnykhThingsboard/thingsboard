/*
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
import 'angular-websocket';
import thingsboardTypes from '../common/types.constant';

export default angular.module('thingsboard.api.telemetryWebsocket', [thingsboardTypes])
    .factory('telemetryWebsocketService', TelemetryWebsocketService)
    .name;

const RECONNECT_INTERVAL = 2000;
const WS_IDLE_TIMEOUT = 90000;

const MAX_PUBLISH_COMMANDS = 10;

/*@ngInject*/
function TelemetryWebsocketService($rootScope, $websocket, $timeout, $window, $mdUtil, $log, toast, types, userService) {

    var isOpening = false,
        isOpened = false,
        isActive = false,
        isReconnect = false,
        reconnectSubscribers = [],
        lastCmdId = 0,
        subscribers = {},
        subscribersCount = 0,
        commands = {},
        cmdsWrapper = {
            tsSubCmds: [],
            historyCmds: [],
            attrSubCmds: []
        },
        telemetryUri,
        dataStream,
        location = $window.location,
        socketCloseTimer,
        reconnectTimer;

    var port = location.port;
    if (location.protocol === "https:") {
        if (!port) {
            port = "443";
        }
        telemetryUri = "wss:";
    } else {
        if (!port) {
            port = "80";
        }
        telemetryUri = "ws:";
    }
    telemetryUri += "//" + location.hostname + ":" + port;
    telemetryUri += "/api/ws/plugins/telemetry";

    var service = {
        subscribe: subscribe,
        batchSubscribe: batchSubscribe,
        unsubscribe: unsubscribe,
        batchUnsubscribe: batchUnsubscribe,
        publishCommands: publishCommands
    }

    $rootScope.telemetryWsLogoutHandle = $rootScope.$on('unauthenticated', function (event, doLogout) {
        if (doLogout) {
            reset(true);
        }
    });

    return service;

    function publishCommands () {
        while(isOpened && hasCommands()) {
            dataStream.send(preparePublishCommands()).then(function () {
                checkToClose();
            });
        }
        tryOpenSocket();
    }

    function hasCommands() {
        return cmdsWrapper.tsSubCmds.length > 0 ||
            cmdsWrapper.historyCmds.length > 0 ||
            cmdsWrapper.attrSubCmds.length > 0;
    }

    function preparePublishCommands() {
        var preparedWrapper = {};
        var leftCount = MAX_PUBLISH_COMMANDS;
        preparedWrapper.tsSubCmds = popCmds(cmdsWrapper.tsSubCmds, leftCount);
        leftCount -= preparedWrapper.tsSubCmds.length;
        preparedWrapper.historyCmds = popCmds(cmdsWrapper.historyCmds, leftCount);
        leftCount -= preparedWrapper.historyCmds.length;
        preparedWrapper.attrSubCmds = popCmds(cmdsWrapper.attrSubCmds, leftCount);
        return preparedWrapper;
    }

    function popCmds(cmds, leftCount) {
        var toPublish = Math.min(cmds.length, leftCount);
        if (toPublish > 0) {
            return cmds.splice(0, toPublish);
        } else {
            return [];
        }
    }

    function onError (errorEvent) {
        if (errorEvent) {
            //showWsError(0, errorEvent);
            $log.warn('WebSocket error event', errorEvent);
        }
        isOpening = false;
    }

    function onOpen () {
        isOpening = false;
        isOpened = true;
        if (reconnectTimer) {
            $timeout.cancel(reconnectTimer);
            reconnectTimer = null;
        }
        if (isReconnect) {
            isReconnect = false;
            for (var r=0; r<reconnectSubscribers.length;r++) {
                var reconnectSubscriber = reconnectSubscribers[r];
                if (reconnectSubscriber.onReconnected) {
                    reconnectSubscriber.onReconnected();
                }
                subscribe(reconnectSubscriber);
            }
            reconnectSubscribers = [];
        } else {
            publishCommands();
        }
    }

    function onClose (closeEvent) {
        if (closeEvent && closeEvent.code > 1000 && closeEvent.code !== 1006) {
            showWsError(closeEvent.code, closeEvent.reason);
        }
        isOpening = false;
        isOpened = false;
        if (isActive) {
            if (!isReconnect) {
                reconnectSubscribers = [];
                for (var id in subscribers) {
                    var subscriber = subscribers[id];
                    if (reconnectSubscribers.indexOf(subscriber) === -1) {
                        reconnectSubscribers.push(subscriber);
                    }
                }
                reset(false);
                isReconnect = true;
            }
            if (reconnectTimer) {
                $timeout.cancel(reconnectTimer);
            }
            reconnectTimer = $timeout(tryOpenSocket, RECONNECT_INTERVAL, false);
        }
    }

    function onMessage (message) {
        if (message.data) {
            var data = angular.fromJson(message.data);
            if (data.errorCode) {
                showWsError(data.errorCode, data.errorMsg);
            } else if (data.subscriptionId) {
                var subscriber = subscribers[data.subscriptionId];
                if (subscriber && data) {
                    var keys = fetchKeys(data.subscriptionId);
                    if (!data.data) {
                        data.data = {};
                    }
                    for (var k = 0; k < keys.length; k++) {
                        var key = keys[k];
                        if (!data.data[key]) {
                            data.data[key] = [];
                        }
                    }
                    subscriber.onData(data, data.subscriptionId);
                }
            }
        }
        checkToClose();
    }

    function showWsError(errorCode, errorMsg) {
        var message = 'WebSocket Error: ';
        if (errorMsg) {
            message += errorMsg;
        } else {
            message += "error code - " + errorCode + ".";
        }
        $mdUtil.nextTick(function () {
            toast.showError(message);
        });
    }

    function fetchKeys(subscriptionId) {
        var command = commands[subscriptionId];
        if (command && command.keys && command.keys.length > 0) {
            return command.keys.split(",");
        } else {
            return [];
        }
    }

    function nextCmdId () {
        lastCmdId++;
        return lastCmdId;
    }

    function subscribe (subscriber, skipPublish) {
        isActive = true;
        var cmdId;
        if (angular.isDefined(subscriber.subscriptionCommands)) {
            for (var i=0;i<subscriber.subscriptionCommands.length;i++) {
                var subscriptionCommand = subscriber.subscriptionCommands[i];
                cmdId = nextCmdId();
                subscribers[cmdId] = subscriber;
                subscriptionCommand.cmdId = cmdId;
                commands[cmdId] = subscriptionCommand;
                if (subscriber.type === types.dataKeyType.timeseries) {
                    cmdsWrapper.tsSubCmds.push(subscriptionCommand);
                } else if (subscriber.type === types.dataKeyType.attribute) {
                    cmdsWrapper.attrSubCmds.push(subscriptionCommand);
                }
            }
        }
        if (angular.isDefined(subscriber.historyCommands)) {
            for (i=0;i<subscriber.historyCommands.length;i++) {
                var historyCommand = subscriber.historyCommands[i];
                cmdId = nextCmdId();
                subscribers[cmdId] = subscriber;
                historyCommand.cmdId = cmdId;
                commands[cmdId] = historyCommand;
                cmdsWrapper.historyCmds.push(historyCommand);
            }
        }
        subscribersCount++;
        if (!skipPublish) {
            publishCommands();
        }
    }

    function batchSubscribe(subscribers) {
        for (var i = 0; i < subscribers.length; i++) {
            var subscriber = subscribers[i];
            subscribe(subscriber, true);
        }
    }

    function unsubscribe (subscriber, skipPublish) {
        if (isActive) {
            var cmdId = null;
            if (subscriber.subscriptionCommands) {
                for (var i = 0; i < subscriber.subscriptionCommands.length; i++) {
                    var subscriptionCommand = subscriber.subscriptionCommands[i];
                    subscriptionCommand.unsubscribe = true;
                    if (subscriber.type === types.dataKeyType.timeseries) {
                        cmdsWrapper.tsSubCmds.push(subscriptionCommand);
                    } else if (subscriber.type === types.dataKeyType.attribute) {
                        cmdsWrapper.attrSubCmds.push(subscriptionCommand);
                    }
                    cmdId = subscriptionCommand.cmdId;
                    if (cmdId) {
                        if (subscribers[cmdId]) {
                            delete subscribers[cmdId];
                        }
                        if (commands[cmdId]) {
                            delete commands[cmdId];
                        }
                    }
                }
            }
            if (subscriber.historyCommands) {
                for (i = 0; i < subscriber.historyCommands.length; i++) {
                    var historyCommand = subscriber.historyCommands[i];
                    cmdId = historyCommand.cmdId;
                    if (cmdId) {
                        if (subscribers[cmdId]) {
                            delete subscribers[cmdId];
                        }
                        if (commands[cmdId]) {
                            delete commands[cmdId];
                        }
                    }
                }
            }
            var index = reconnectSubscribers.indexOf(subscriber);
            if (index > -1) {
                reconnectSubscribers.splice(index, 1);
            }
            subscribersCount--;
            if (!skipPublish) {
                publishCommands();
            }
        }
    }

    function batchUnsubscribe(subscribers) {
        for (var i=0;i<subscribers.length;i++) {
            var subscriber = subscribers[i];
            unsubscribe(subscriber, true);
        }
    }

    function checkToClose () {
        if (subscribersCount === 0 && isOpened) {
            if (!socketCloseTimer) {
                socketCloseTimer = $timeout(closeSocket, WS_IDLE_TIMEOUT, false);
            }
        }
    }

    function tryOpenSocket () {
        if (isActive) {
            if (!isOpened && !isOpening) {
                isOpening = true;
                if (userService.isJwtTokenValid()) {
                    openSocket(userService.getJwtToken());
                } else {
                    userService.refreshJwtToken().then(function success() {
                        openSocket(userService.getJwtToken());
                    }, function fail() {
                        isOpening = false;
                        $rootScope.$broadcast('unauthenticated');
                    });
                }
            }
            if (socketCloseTimer) {
                $timeout.cancel(socketCloseTimer);
                socketCloseTimer = null;
            }
        }
    }

    function openSocket(token) {
        dataStream = $websocket(telemetryUri + '?token=' + token);
        dataStream.onError(onError);
        dataStream.onOpen(onOpen);
        dataStream.onClose(onClose);
        dataStream.onMessage(onMessage, {autoApply: false});
    }

    function closeSocket() {
        isActive = false;
        if (isOpened) {
            dataStream.close();
        }
    }

    function reset(close) {
        if (socketCloseTimer) {
            $timeout.cancel(socketCloseTimer);
            socketCloseTimer = null;
        }
        lastCmdId = 0;
        subscribers = {};
        subscribersCount = 0;
        commands = {};
        cmdsWrapper.tsSubCmds = [];
        cmdsWrapper.historyCmds = [];
        cmdsWrapper.attrSubCmds = [];
        if (close) {
            closeSocket();
        }
    }
}
