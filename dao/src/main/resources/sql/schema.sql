--
-- Thingsboard OÜ ("COMPANY") CONFIDENTIAL
--
-- Copyright © 2016-2017 Thingsboard OÜ. All Rights Reserved.
--
-- NOTICE: All information contained herein is, and remains
-- the property of Thingsboard OÜ and its suppliers,
-- if any.  The intellectual and technical concepts contained
-- herein are proprietary to Thingsboard OÜ
-- and its suppliers and may be covered by U.S. and Foreign Patents,
-- patents in process, and are protected by trade secret or copyright law.
--
-- Dissemination of this information or reproduction of this material is strictly forbidden
-- unless prior written permission is obtained from COMPANY.
--
-- Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
-- managers or contractors who have executed Confidentiality and Non-disclosure agreements
-- explicitly covering such access.
--
-- The copyright notice above does not evidence any actual or intended publication
-- or disclosure  of  this source code, which includes
-- information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
-- ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
-- OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
-- THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
-- AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
-- THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
-- DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
-- OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
--


CREATE TABLE IF NOT EXISTS admin_settings (
    id varchar(31) NOT NULL CONSTRAINT admin_settings_pkey PRIMARY KEY,
    json_value varchar(10000000),
    key varchar(255)
);

CREATE TABLE IF NOT EXISTS alarm (
    id varchar(31) NOT NULL CONSTRAINT alarm_pkey PRIMARY KEY,
    ack_ts bigint,
    clear_ts bigint,
    additional_info varchar,
    end_ts bigint,
    originator_id varchar(31),
    originator_type integer,
    propagate boolean,
    severity varchar(255),
    start_ts bigint,
    status varchar(255),
    tenant_id varchar(31),
    type varchar(255)
);

CREATE TABLE IF NOT EXISTS asset (
    id varchar(31) NOT NULL CONSTRAINT asset_pkey PRIMARY KEY,
    additional_info varchar,
    customer_id varchar(31),
    name varchar(255),
    search_text varchar(255),
    tenant_id varchar(31),
    type varchar(255)
);

CREATE TABLE IF NOT EXISTS integration (
    id varchar(31) NOT NULL CONSTRAINT integration_pkey PRIMARY KEY,
    additional_info varchar,
    configuration varchar,
    converter_id varchar(31),
    routing_key varchar(255),
    search_text varchar(255),
    tenant_id varchar(31),
    type varchar(255)
);

CREATE TABLE IF NOT EXISTS converter (
    id varchar(31) NOT NULL CONSTRAINT converter_pkey PRIMARY KEY,
    additional_info varchar,
    configuration varchar,
    name varchar(255),
    search_text varchar(255),
    tenant_id varchar(31),
    type varchar(255)
);

CREATE TABLE IF NOT EXISTS attribute_kv (
  entity_type varchar(255),
  entity_id varchar(31),
  attribute_type varchar(255),
  attribute_key varchar(255),
  bool_v boolean,
  str_v varchar(10000000),
  long_v bigint,
  dbl_v double precision,
  last_update_ts bigint,
  CONSTRAINT attribute_kv_unq_key UNIQUE (entity_type, entity_id, attribute_type, attribute_key)
);

CREATE TABLE IF NOT EXISTS component_descriptor (
    id varchar(31) NOT NULL CONSTRAINT component_descriptor_pkey PRIMARY KEY,
    actions varchar(255),
    clazz varchar,
    configuration_descriptor varchar,
    name varchar(255),
    scope varchar(255),
    search_text varchar(255),
    type varchar(255)
);

CREATE TABLE IF NOT EXISTS customer (
    id varchar(31) NOT NULL CONSTRAINT customer_pkey PRIMARY KEY,
    additional_info varchar,
    address varchar,
    address2 varchar,
    city varchar(255),
    country varchar(255),
    email varchar(255),
    phone varchar(255),
    search_text varchar(255),
    state varchar(255),
    tenant_id varchar(31),
    title varchar(255),
    zip varchar(255)
);

CREATE TABLE IF NOT EXISTS dashboard (
    id varchar(31) NOT NULL CONSTRAINT dashboard_pkey PRIMARY KEY,
    configuration varchar(10000000),
    customer_id varchar(31),
    search_text varchar(255),
    tenant_id varchar(31),
    title varchar(255)
);

CREATE TABLE IF NOT EXISTS device (
    id varchar(31) NOT NULL CONSTRAINT device_pkey PRIMARY KEY,
    additional_info varchar,
    customer_id varchar(31),
    type varchar(255),
    name varchar(255),
    search_text varchar(255),
    tenant_id varchar(31)
);

CREATE TABLE IF NOT EXISTS device_credentials (
    id varchar(31) NOT NULL CONSTRAINT device_credentials_pkey PRIMARY KEY,
    credentials_id varchar,
    credentials_type varchar(255),
    credentials_value varchar,
    device_id varchar(31)
);

CREATE TABLE IF NOT EXISTS event (
    id varchar(31) NOT NULL CONSTRAINT event_pkey PRIMARY KEY,
    body varchar,
    entity_id varchar(31),
    entity_type varchar(255),
    event_type varchar(255),
    event_uid varchar(255),
    tenant_id varchar(31),
    CONSTRAINT event_unq_key UNIQUE (tenant_id, entity_type, entity_id, event_type, event_uid)
);

CREATE TABLE IF NOT EXISTS plugin (
    id varchar(31) NOT NULL CONSTRAINT plugin_pkey PRIMARY KEY,
    additional_info varchar,
    api_token varchar(255),
    plugin_class varchar(255),
    configuration varchar,
    name varchar(255),
    public_access boolean,
    search_text varchar(255),
    state varchar(255),
    tenant_id varchar(31)
);

CREATE TABLE IF NOT EXISTS relation (
    from_id varchar(31),
    from_type varchar(255),
    to_id varchar(31),
    to_type varchar(255),
    relation_type_group varchar(255),
    relation_type varchar(255),
    additional_info varchar,
    CONSTRAINT relation_unq_key UNIQUE (from_id, from_type, relation_type_group, relation_type, to_id, to_type)
);

CREATE TABLE IF NOT EXISTS rule (
    id varchar(31) NOT NULL CONSTRAINT rule_pkey PRIMARY KEY,
    action varchar,
    additional_info varchar,
    filters varchar,
    name varchar(255),
    plugin_token varchar(255),
    processor varchar,
    search_text varchar(255),
    state varchar(255),
    tenant_id varchar(31),
    weight integer
);

CREATE TABLE IF NOT EXISTS tb_user (
    id varchar(31) NOT NULL CONSTRAINT tb_user_pkey PRIMARY KEY,
    additional_info varchar,
    authority varchar(255),
    customer_id varchar(31),
    email varchar(255) UNIQUE,
    first_name varchar(255),
    last_name varchar(255),
    search_text varchar(255),
    tenant_id varchar(31)
);

CREATE TABLE IF NOT EXISTS tenant (
    id varchar(31) NOT NULL CONSTRAINT tenant_pkey PRIMARY KEY,
    additional_info varchar,
    address varchar,
    address2 varchar,
    city varchar(255),
    country varchar(255),
    email varchar(255),
    phone varchar(255),
    region varchar(255),
    search_text varchar(255),
    state varchar(255),
    title varchar(255),
    zip varchar(255)
);

CREATE TABLE IF NOT EXISTS ts_kv (
    entity_type varchar(255) NOT NULL,
    entity_id varchar(31) NOT NULL,
    key varchar(255) NOT NULL,
    ts bigint NOT NULL,
    bool_v boolean,
    str_v varchar(10000000),
    long_v bigint,
    dbl_v double precision,
    CONSTRAINT ts_kv_unq_key UNIQUE (entity_type, entity_id, key, ts)
);

CREATE TABLE IF NOT EXISTS ts_kv_latest (
    entity_type varchar(255) NOT NULL,
    entity_id varchar(31) NOT NULL,
    key varchar(255) NOT NULL,
    ts bigint NOT NULL,
    bool_v boolean,
    str_v varchar(10000000),
    long_v bigint,
    dbl_v double precision,
    CONSTRAINT ts_kv_latest_unq_key UNIQUE (entity_type, entity_id, key)
);

CREATE TABLE IF NOT EXISTS user_credentials (
    id varchar(31) NOT NULL CONSTRAINT user_credentials_pkey PRIMARY KEY,
    activate_token varchar(255) UNIQUE,
    enabled boolean,
    password varchar(255),
    reset_token varchar(255) UNIQUE,
    user_id varchar(31) UNIQUE
);

CREATE TABLE IF NOT EXISTS widget_type (
    id varchar(31) NOT NULL CONSTRAINT widget_type_pkey PRIMARY KEY,
    alias varchar(255),
    bundle_alias varchar(255),
    descriptor varchar(1000000),
    name varchar(255),
    tenant_id varchar(31)
);

CREATE TABLE IF NOT EXISTS widgets_bundle (
    id varchar(31) NOT NULL CONSTRAINT widgets_bundle_pkey PRIMARY KEY,
    alias varchar(255),
    search_text varchar(255),
    tenant_id varchar(31),
    title varchar(255)
);

CREATE TABLE IF NOT EXISTS entity_group (
    id varchar(31) NOT NULL CONSTRAINT entity_group_pkey PRIMARY KEY,
    type varchar(255) NOT NULL,
    name varchar(255),
    additional_info varchar,
    configuration varchar(10000000)
);