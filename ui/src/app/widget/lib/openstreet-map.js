/*
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
import 'leaflet/dist/leaflet.css';
import * as L from 'leaflet';
import 'leaflet-providers';

export default class TbOpenStreetMap {

    constructor($containerElement, initCallback, defaultZoomLevel, dontFitMapBounds, minZoomLevel, mapProvider) {

        this.defaultZoomLevel = defaultZoomLevel;
        this.dontFitMapBounds = dontFitMapBounds;
        this.minZoomLevel = minZoomLevel;
        this.tooltips = [];

        if (!mapProvider) {
            mapProvider = "OpenStreetMap.Mapnik";
        }

        this.map = L.map($containerElement[0]).setView([0, 0], this.defaultZoomLevel || 8);

        var tileLayer = L.tileLayer.provider(mapProvider);

        tileLayer.addTo(this.map);

        if (initCallback) {
            setTimeout(initCallback, 0); //eslint-disable-line
        }

    }

    inited() {
        return angular.isDefined(this.map);
    }

    updateMarkerLabel(marker, settings) {
        marker.unbindTooltip();
        marker.bindTooltip('<div style="color: '+ settings.labelColor +';"><b>'+settings.labelText+'</b></div>',
            { className: 'tb-marker-label', permanent: true, direction: 'top', offset: marker.tooltipOffset });
    }

    updateMarkerColor(marker, color) {
        this.createDefaultMarkerIcon(marker, color, (iconInfo) => {
            marker.setIcon(iconInfo.icon);
        });
    }

    updateMarkerIcon(marker, settings) {
        this.createMarkerIcon(marker, settings, (iconInfo) => {
            marker.setIcon(iconInfo.icon);
            if (settings.showLabel) {
                marker.unbindTooltip();
                marker.tooltipOffset = [0, -iconInfo.size[1] + 10];
                marker.bindTooltip('<div style="color: '+ settings.labelColor +';"><b>'+settings.labelText+'</b></div>',
                    { className: 'tb-marker-label', permanent: true, direction: 'top', offset: marker.tooltipOffset });
            }
        });
    }

    createMarkerIcon(marker, settings, onMarkerIconReady) {
        var currentImage = settings.currentImage;
        var opMap = this;
        if (currentImage && currentImage.url) {
            var testImage = document.createElement('img'); // eslint-disable-line
            testImage.style.visibility = 'hidden';
            testImage.onload = function() {
                var width;
                var height;
                var aspect = testImage.width / testImage.height;
                document.body.removeChild(testImage); //eslint-disable-line
                if (aspect > 1) {
                    width = currentImage.size;
                    height = currentImage.size / aspect;
                } else {
                    width = currentImage.size * aspect;
                    height = currentImage.size;
                }
                var icon = L.icon({
                    iconUrl: currentImage.url,
                    iconSize: [width, height],
                    iconAnchor: [width/2, height],
                    popupAnchor: [0, -height]
                });
                var iconInfo = {
                    size: [width, height],
                    icon: icon
                };
                onMarkerIconReady(iconInfo);
            };
            testImage.onerror = function() {
                opMap.createDefaultMarkerIcon(marker, settings.color, onMarkerIconReady);
            };
            document.body.appendChild(testImage); //eslint-disable-line
            testImage.src = currentImage.url;
        } else {
            this.createDefaultMarkerIcon(marker, settings.color, onMarkerIconReady);
        }
    }

    createDefaultMarkerIcon(marker, color, onMarkerIconReady) {
        var pinColor = color.substr(1);
        var icon = L.icon({
            iconUrl: 'https://chart.apis.google.com/chart?chst=d_map_pin_letter&chld=%E2%80%A2|' + pinColor,
            iconSize: [21, 34],
            iconAnchor: [10, 34],
            popupAnchor: [0, -34],
            shadowUrl: 'https://chart.apis.google.com/chart?chst=d_map_pin_shadow',
            shadowSize: [40, 37],
            shadowAnchor: [12, 35]
        });
        var iconInfo = {
            size: [21, 34],
            icon: icon
        };
        onMarkerIconReady(iconInfo);
    }

    createMarker(location, settings, onClickListener, markerArgs) {
        var marker = L.marker(location, {});
        var opMap = this;
        this.createMarkerIcon(marker, settings, (iconInfo) => {
            marker.setIcon(iconInfo.icon);
            if (settings.showLabel) {
                marker.tooltipOffset = [0, -iconInfo.size[1] + 10];
                marker.bindTooltip('<div style="color: '+ settings.labelColor +';"><b>'+settings.labelText+'</b></div>',
                    { className: 'tb-marker-label', permanent: true, direction: 'top', offset: marker.tooltipOffset });
            }
            marker.addTo(opMap.map);
        });

        if (settings.displayTooltip) {
            this.createTooltip(marker, settings.tooltipPattern, settings.tooltipReplaceInfo, settings.autocloseTooltip, markerArgs);
        }

        if (onClickListener) {
            marker.on('click', onClickListener);
        }

        return marker;
    }

    removeMarker(marker) {
        this.map.removeLayer(marker);
    }

    createTooltip(marker, pattern, replaceInfo, autoClose, markerArgs) {
        var popup = L.popup();
        popup.setContent('');
        marker.bindPopup(popup, {autoClose: autoClose, closeOnClick: false});
        this.tooltips.push( {
            markerArgs: markerArgs,
            popup: popup,
            pattern: pattern,
            replaceInfo: replaceInfo
        });
    }

    updatePolylineColor(polyline, settings, color) {
        var style = {
            color: color,
            opacity: settings.strokeOpacity,
            weight: settings.strokeWeight
        };
        polyline.setStyle(style);
    }

    createPolyline(locations, settings) {
        var polyline = L.polyline(locations,
            {
                color: settings.color,
                opacity: settings.strokeOpacity,
                weight: settings.strokeWeight
            }
        ).addTo(this.map);
        return polyline;
    }

    removePolyline(polyline) {
        this.map.removeLayer(polyline);
    }

    fitBounds(bounds) {
        if (bounds.isValid()) {
            if (this.dontFitMapBounds && this.defaultZoomLevel) {
                this.map.setZoom(this.defaultZoomLevel, {animate: false});
                this.map.panTo(bounds.getCenter(), {animate: false});
            } else {
                var tbMap = this;
                this.map.once('zoomend', function() {
                    if (!tbMap.defaultZoomLevel && tbMap.map.getZoom() > tbMap.minZoomLevel) {
                        tbMap.map.setZoom(tbMap.minZoomLevel, {animate: false});
                    }
                });
                this.map.fitBounds(bounds, {padding: [50, 50], animate: false});
            }
        }
    }

    createLatLng(lat, lng) {
        return L.latLng(lat, lng);
    }

    extendBoundsWithMarker(bounds, marker) {
        bounds.extend(marker.getLatLng());
    }

    getMarkerPosition(marker) {
        return marker.getLatLng();
    }

    setMarkerPosition(marker, latLng) {
        marker.setLatLng(latLng);
    }

    getPolylineLatLngs(polyline) {
        return polyline.getLatLngs();
    }

    setPolylineLatLngs(polyline, latLngs) {
        polyline.setLatLngs(latLngs);
    }

    createBounds() {
        return L.latLngBounds();
    }

    extendBounds(bounds, polyline) {
        if (polyline && polyline.getLatLngs()) {
            bounds.extend(polyline.getBounds());
        }
    }

    invalidateSize() {
        this.map.invalidateSize(true);
    }

    getTooltips() {
        return this.tooltips;
    }

}
