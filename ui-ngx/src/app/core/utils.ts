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

import _ from 'lodash';
import { Observable, Subject, from, fromEvent, of } from 'rxjs';
import { finalize, share, map } from 'rxjs/operators';
import base64js from 'base64-js';
import { Type } from '@angular/core';
import { CustomSchedulerEventConfigComponent } from '@home/components/scheduler/config/custom-scheduler-event-config.component';

export function onParentScrollOrWindowResize(el: Node): Observable<Event> {
  const scrollSubject = new Subject<Event>();
  const scrollParentNodes = scrollParents(el);
  const eventListenerObject: EventListenerObject = {
    handleEvent(evt: Event) {
      scrollSubject.next(evt);
    }
  };
  scrollParentNodes.forEach((scrollParentNode) => {
    scrollParentNode.addEventListener('scroll', eventListenerObject);
  });
  window.addEventListener('resize', eventListenerObject);
  const shared = scrollSubject.pipe(
    finalize(() => {
      scrollParentNodes.forEach((scrollParentNode) => {
        scrollParentNode.removeEventListener('scroll', eventListenerObject);
      });
      window.removeEventListener('resize', eventListenerObject);
    }),
    share()
  );
  return shared;
}

export function isLocalUrl(url: string): boolean {
  const parser = document.createElement('a');
  parser.href = url;
  const host = parser.hostname;
  if (host === 'localhost' || host === '127.0.0.1') {
    return true;
  } else {
    return false;
  }
}

export function animatedScroll(element: HTMLElement, scrollTop: number, delay?: number) {
  let currentTime = 0;
  const increment = 20;
  const start = element.scrollTop;
  const to = scrollTop;
  const duration = delay ? delay : 0;
  const remaining = to - start;
  const animateScroll = () => {
    if (duration === 0) {
      element.scrollTop = to;
    } else {
      currentTime += increment;
      const val = easeInOut(currentTime, start, remaining, duration);
      element.scrollTop = val;
      if (currentTime < duration) {
        setTimeout(animateScroll, increment);
      }
    }
  };
  animateScroll();
}

export function isUndefined(value: any): boolean {
  return typeof value === 'undefined';
}

export function isUndefinedOrNull(value: any): boolean {
  return typeof value === 'undefined' || value === null;
}

export function isDefined(value: any): boolean {
  return typeof value !== 'undefined';
}

export function isDefinedAndNotNull(value: any): boolean {
  return typeof value !== 'undefined' && value !== null;
}

export function isFunction(value: any): boolean {
  return typeof value === 'function';
}

export function isObject(value: any): boolean {
  return value !== null && typeof value === 'object';
}

export function isNumber(value: any): boolean {
  return typeof value === 'number';
}

export function isNumeric(value: any): boolean {
  return (value - parseFloat(value) + 1) >= 0;
}

export function isString(value: any): boolean {
  return typeof value === 'string';
}

export function isArray(value: any): boolean {
  return Array.isArray(value);
}

export function formatValue(value: any, dec?: number, units?: string, showZeroDecimals?: boolean): string | undefined {
  if (isDefined(value) &&
    value !== null && isNumeric(value)) {
    let formatted: string | number = Number(value);
    if (isDefined(dec)) {
      formatted = formatted.toFixed(dec);
    }
    if (!showZeroDecimals) {
      formatted = (Number(formatted) * 1);
    }
    formatted = formatted.toString();
    if (isDefined(units) && units.length > 0) {
      formatted += ' ' + units;
    }
    return formatted;
  } else {
    return value !== null ? value : '';
  }
}

export function objectValues(obj: any): any[] {
  return Object.keys(obj).map(e => obj[e]);
}

export function deleteNullProperties(obj: any) {
  if (isUndefined(obj) || obj == null) {
    return;
  }
  Object.keys(obj).forEach((propName) => {
    if (obj[propName] === null || isUndefined(obj[propName])) {
      delete obj[propName];
    } else if (isObject(obj[propName])) {
      deleteNullProperties(obj[propName]);
    } else if (obj[propName] instanceof Array) {
      (obj[propName] as any[]).forEach((elem) => {
        deleteNullProperties(elem);
      });
    }
  });
}

export function objToBase64(obj: any): string {
  const json = JSON.stringify(obj);
  const encoded = utf8Encode(json);
  const b64Encoded: string = base64js.fromByteArray(encoded);
  return b64Encoded;
}

export function base64toObj(b64Encoded: string): any {
  const encoded: Uint8Array | number[] = base64js.toByteArray(b64Encoded);
  const json = utf8Decode(encoded);
  const obj = JSON.parse(json);
  return obj;
}

export function stringToBase64(value: string): string {
  const encoded = utf8Encode(value);
  const b64Encoded: string = base64js.fromByteArray(encoded);
  return b64Encoded;
}

export function base64toString(b64Encoded: string): string {
  const encoded: Uint8Array | number[] = base64js.toByteArray(b64Encoded);
  const value = utf8Decode(encoded);
  return value;
}

function utf8Encode(str: string): Uint8Array | number[] {
  let result: Uint8Array | number[];
  if (isUndefined(Uint8Array)) {
    result = utf8ToBytes(str);
  } else {
    result = new Uint8Array(utf8ToBytes(str));
  }
  return result;
}

function utf8Decode(bytes: Uint8Array | number[]): string {
  return utf8Slice(bytes, 0, bytes.length);
}

const scrollRegex = /(auto|scroll)/;

function parentNodes(node: Node, nodes: Node[]): Node[] {
  if (node.parentNode === null) {
    return nodes;
  }
  return parentNodes(node.parentNode, nodes.concat([node]));
}

function style(el: Element, prop: string): string {
  return getComputedStyle(el, null).getPropertyValue(prop);
}

function overflow(el: Element): string {
  return style(el, 'overflow') + style(el, 'overflow-y') + style(el, 'overflow-x');
}

function isScrollNode(node: Node): boolean {
  if (node instanceof Element) {
    return scrollRegex.test(overflow(node));
  } else {
    return false;
  }
}

function scrollParents(node: Node): Node[] {
  if (!(node instanceof HTMLElement || node instanceof SVGElement)) {
    return [];
  }
  const scrollParentNodes = [];
  const nodeParents = parentNodes(node, []);
  nodeParents.forEach((nodeParent) => {
    if (isScrollNode(nodeParent)) {
      scrollParentNodes.push(nodeParent);
    }
  });
  if (document.scrollingElement) {
    scrollParentNodes.push(document.scrollingElement);
  } else if (document.documentElement) {
    scrollParentNodes.push(document.documentElement);
  }
  return scrollParentNodes;
}

function hashCode(str) {
  let hash = 0;
  let i, char;
  if (str.length == 0) return hash;
  for (i = 0; i < str.length; i++) {
    char = str.charCodeAt(i);
    hash = ((hash << 5) - hash) + char;
    hash = hash & hash; // Convert to 32bit integer
  }
  return hash;
}

function easeInOut(
  currentTime: number,
  startTime: number,
  remainingTime: number,
  duration: number) {
  currentTime /= duration / 2;

  if (currentTime < 1) {
    return (remainingTime / 2) * currentTime * currentTime + startTime;
  }

  currentTime--;
  return (
    (-remainingTime / 2) * (currentTime * (currentTime - 2) - 1) + startTime
  );
}

function utf8Slice(buf: Uint8Array | number[], start: number, end: number): string {
  let res = '';
  let tmp = '';
  end = Math.min(buf.length, end || Infinity);
  start = start || 0;

  for (let i = start; i < end; i++) {
    if (buf[i] <= 0x7F) {
      res += decodeUtf8Char(tmp) + String.fromCharCode(buf[i]);
      tmp = '';
    } else {
      tmp += '%' + buf[i].toString(16);
    }
  }
  return res + decodeUtf8Char(tmp);
}

function decodeUtf8Char(str: string): string {
  try {
    return decodeURIComponent(str);
  } catch (err) {
    return String.fromCharCode(0xFFFD); // UTF 8 invalid char
  }
}

function utf8ToBytes(input: string, units?: number): number[] {
  units = units || Infinity;
  let codePoint: number;
  const length = input.length;
  let leadSurrogate: number = null;
  const bytes: number[] = [];
  let i = 0;

  for (; i < length; i++) {
    codePoint = input.charCodeAt(i);

    // is surrogate component
    if (codePoint > 0xD7FF && codePoint < 0xE000) {
      // last char was a lead
      if (leadSurrogate) {
        // 2 leads in a row
        if (codePoint < 0xDC00) {
          units -= 3;
          if (units > -1) { bytes.push(0xEF, 0xBF, 0xBD); }
          leadSurrogate = codePoint;
          continue;
        } else {
          // valid surrogate pair
          // tslint:disable-next-line:no-bitwise
          codePoint = leadSurrogate - 0xD800 << 10 | codePoint - 0xDC00 | 0x10000;
          leadSurrogate = null;
        }
      } else {
        // no lead yet

        if (codePoint > 0xDBFF) {
          // unexpected trail
          units -= 3;
          if (units > -1) { bytes.push(0xEF, 0xBF, 0xBD); }
          continue;
        } else if (i + 1 === length) {
          // unpaired lead
          units -= 3;
          if (units > -1) { bytes.push(0xEF, 0xBF, 0xBD); }
          continue;
        } else {
          // valid lead
          leadSurrogate = codePoint;
          continue;
        }
      }
    } else if (leadSurrogate) {
      // valid bmp char, but last char was a lead
      units -= 3;
      if (units > -1) { bytes.push(0xEF, 0xBF, 0xBD); }
      leadSurrogate = null;
    }

    // encode utf8
    if (codePoint < 0x80) {
      units -= 1;
      if (units < 0) { break; }
      bytes.push(codePoint);
    } else if (codePoint < 0x800) {
      units -= 2;
      if (units < 0) { break; }
      bytes.push(
        // tslint:disable-next-line:no-bitwise
        codePoint >> 0x6 | 0xC0,
        // tslint:disable-next-line:no-bitwise
        codePoint & 0x3F | 0x80
      );
    } else if (codePoint < 0x10000) {
      units -= 3;
      if (units < 0) { break; }
      bytes.push(
        // tslint:disable-next-line:no-bitwise
        codePoint >> 0xC | 0xE0,
        // tslint:disable-next-line:no-bitwise
        codePoint >> 0x6 & 0x3F | 0x80,
        // tslint:disable-next-line:no-bitwise
        codePoint & 0x3F | 0x80
      );
    } else if (codePoint < 0x200000) {
      units -= 4;
      if (units < 0) { break; }
      bytes.push(
        // tslint:disable-next-line:no-bitwise
        codePoint >> 0x12 | 0xF0,
        // tslint:disable-next-line:no-bitwise
        codePoint >> 0xC & 0x3F | 0x80,
        // tslint:disable-next-line:no-bitwise
        codePoint >> 0x6 & 0x3F | 0x80,
        // tslint:disable-next-line:no-bitwise
        codePoint & 0x3F | 0x80
      );
    } else {
      throw new Error('Invalid code point');
    }
  }
  return bytes;
}

export function deepClone<T>(target: T, ignoreFields?: string[]): T {
  if (target === null) {
    return target;
  }
  if (target instanceof Date) {
    return new Date(target.getTime()) as any;
  }
  if (target instanceof Array) {
    const cp = [] as any[];
    (target as any[]).forEach((v) => { cp.push(v); });
    return cp.map((n: any) => deepClone<any>(n)) as any;
  }
  if (typeof target === 'object' && target !== {}) {
    const cp = { ...(target as { [key: string]: any }) } as { [key: string]: any };
    Object.keys(cp).forEach(k => {
      if (!ignoreFields || ignoreFields.indexOf(k) === -1) {
        cp[k] = deepClone<any>(cp[k]);
      }
    });
    return cp as T;
  }
  return target;
}

export function isEqual(a: any, b: any): boolean {
  return _.isEqual(a, b);
}

export function mergeDeep<T>(target: T, ...sources: T[]): T {
  return _.merge(target, ...sources);
}

export function guid(): string {
  function s4(): string {
    return Math.floor((1 + Math.random()) * 0x10000)
      .toString(16)
      .substring(1);
  }
  return s4() + s4() + '-' + s4() + '-' + s4() + '-' +
    s4() + '-' + s4() + s4() + s4();
}

const PROP_METADATA = '__prop__metadata__';

export function cloneMetadata<S, T>(sourceType: Type<S>, targetType: Type<T>) {
  const sourceMeta = sourceType.prototype.constructor[PROP_METADATA];
  const targetMeta = Object.defineProperty(targetType.prototype.constructor,
    PROP_METADATA, { value: {} })[PROP_METADATA];
  for (const field of Object.keys(sourceMeta)) {
    if (sourceMeta.hasOwnProperty(field)) {
      targetMeta[field] = sourceMeta[field];
    }
  }
}

const SNAKE_CASE_REGEXP = /[A-Z]/g;

export function snakeCase(name: string, separator: string): string {
  separator = separator || '_';
  return name.replace(SNAKE_CASE_REGEXP, (letter, pos) => {
    return (pos ? separator : '') + letter.toLowerCase();
  });
}

export function getDescendantProp(obj: any, path: string): any {
  return path.split('.').reduce((acc, part) => acc && acc[part], obj);
}

export function imageLoader(imageUrl: string): Observable<HTMLImageElement> {
  const image = new Image();
  const imageLoad$ = fromEvent(image, 'load').pipe(map(event => image));
  image.src = imageUrl;
  return imageLoad$;
}

const imageAspectMap = {};

export function aspectCache(imageUrl: string): Observable<number> {
  if (imageUrl?.length) {
    const hash = hashCode(imageUrl);
    let aspect = imageAspectMap[hash];
    if (aspect) {
      return of(aspect);
    }
    else return imageLoader(imageUrl).pipe(map(image => {
      aspect = image.width / image.height;
      imageAspectMap[hash] = aspect;
      return aspect;
    }))
  }
}


export function parseArray(input: any[]): any[] {
  return _(input).groupBy(el => el?.datasource?.entityName)
    .values().value().map((entityArray, dsIndex) =>
      entityArray[0].data.map((el, i) => {
        const obj = {
          entityName: entityArray[0]?.datasource?.entityName,
          $datasource: entityArray[0]?.datasource,
          dsIndex,
          time: el[0],
          deviceType: null
        };
        entityArray.forEach(entity => {
          obj[entity?.dataKey?.label] = entity?.data[i][1];
          obj[entity?.dataKey?.label + '|ts'] = entity?.data[0][0];
          if (entity?.dataKey?.label === 'type') {
            obj.deviceType = entity?.data[0][1];
          }
        });
        return obj;
      })
    );
}

export function parseData(input: any[]): any[] {
  return _(input).groupBy(el => el?.datasource?.entityName)
    .values().value().map((entityArray, i) => {
      const obj = {
        entityName: entityArray[0]?.datasource?.entityName,
        $datasource: entityArray[0]?.datasource,
        dsIndex: i,
        deviceType: null
      };
      entityArray.forEach(el => {
        obj[el?.dataKey?.label] = el?.data[0][1];
        obj[el?.dataKey?.label + '|ts'] = el?.data[0][0];
        if (el?.dataKey?.label === 'type') {
          obj.deviceType = el?.data[0][1];
        }
      });
      return obj;
    });
}

export function safeExecute(func: Function, params = []) {
  let res = null;
  if (func && typeof (func) === 'function') {
    try {
      res = func(...params);
    }
    catch (err) {
      console.log('error in external function:', err);
      res = null;
    }
  }
  return res;
}

export function parseFunction(source: any, params: string[] = []): Function {
  let res = null;
  if (source?.length) {
    try {
      res = new Function(...params, source);
    }
    catch (err) {
      console.error(err);
      res = null;
    }
  }
  return res;
}

export function parseTemplate(template: string, data: object) {
  let res = '';
  try {
    let variables = '';
    const expressions = template
      .match(/\{(.*?)\}/g) // find expressions
      .map(exp => exp.replace(/{|}/g, '')) // remove brackets
      .map(exp => exp.split(':'))
      .map(arr => {
        variables += `let ${arr[0]} = ''; `;
        return arr;
      })
      .filter(arr => !!arr[1]) // filter expressions without format
      .reduce((res, current) => {
        res[current[0]] = current[1];
        return res;
      }, {});

    for (const key in data) {
      if (!key.includes('|'))
        variables += `${key} = '${expressions[key] ? padValue(data[key], +expressions[key]) : data[key]}';`;
    }
    template = template.replace(/:\d+}/g, '}');
    res = safeExecute(parseFunction(variables + 'return' + '`' + template + '`'));
  }
  catch (ex) {
  }
  return res;
}

export function padValue(val: any, dec: number): string {
  let strVal;
  let n;

  val = parseFloat(val);
  n = (val < 0);
  val = Math.abs(val);

  if (dec > 0) {
    strVal = val.toFixed(dec).toString()
  } else {
    strVal = Math.round(val).toString();
  }
  strVal = (n ? '-' : '') + strVal;
  return strVal;
}

export function removeEmptyObjects(obj: object): object {
  console.log("functionremoveEmptyObjects -> obj", obj)
  for (const key of Object.keys(obj)) {
    if (!obj[key]) delete obj[key]
    else
      if (Array.isArray(obj[key]))
        obj[key] = obj[key].filter(el => !!removeEmptyObjects(el))
      else
        if (typeof (obj[key]) === 'object')
          removeEmptyObjects(obj[key]);
  }
  return obj;
}


export function baseUrl(): string {
  let url = window.location.protocol + '//' + window.location.hostname;
  const port = window.location.port;
  if (port !== '80' && port !== '443') {
    url += ':' + port;
  }
  return url;
}