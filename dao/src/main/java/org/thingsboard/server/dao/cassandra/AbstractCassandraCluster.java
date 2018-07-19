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
package org.thingsboard.server.dao.cassandra;


import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.ProtocolOptions.Compression;
import com.datastax.driver.core.Session;
import com.datastax.driver.mapping.DefaultPropertyMapper;
import com.datastax.driver.mapping.Mapper;
import com.datastax.driver.mapping.MappingConfiguration;
import com.datastax.driver.mapping.MappingManager;
import com.datastax.driver.mapping.PropertyAccessStrategy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public abstract class AbstractCassandraCluster {

    private static final String COMMA = ",";
    private static final String COLON = ":";

    @Value("${cassandra.cluster_name}")
    private String clusterName;
    @Value("${cassandra.url}")
    private String url;
    @Value("${cassandra.compression}")
    private String compression;
    @Value("${cassandra.ssl}")
    private Boolean ssl;
    @Value("${cassandra.jmx}")
    private Boolean jmx;
    @Value("${cassandra.metrics}")
    private Boolean metrics;
    @Value("${cassandra.credentials}")
    private Boolean credentials;
    @Value("${cassandra.username}")
    private String username;
    @Value("${cassandra.password}")
    private String password;
    @Value("${cassandra.init_timeout_ms}")
    private long initTimeout;
    @Value("${cassandra.init_retry_interval_ms}")
    private long initRetryInterval;
    @Value("${cassandra.max_requests_per_connection_local:32768}")
    private int max_requests_local;
    @Value("${cassandra.max_requests_per_connection_remote:32768}")
    private int max_requests_remote;

    @Autowired
    private CassandraSocketOptions socketOpts;

    @Autowired
    private CassandraQueryOptions queryOpts;

    @Autowired
    private Environment environment;

    private Cluster cluster;
    private Cluster.Builder clusterBuilder;

    private Session session;

    private MappingManager mappingManager;

    public <T> Mapper<T> getMapper(Class<T> clazz) {
        return mappingManager.mapper(clazz);
    }

    private String keyspaceName;

    protected void init(String keyspaceName) {
        this.keyspaceName = keyspaceName;
        this.clusterBuilder = Cluster.builder()
                .addContactPointsWithPorts(getContactPoints(url))
                .withClusterName(clusterName)
                .withSocketOptions(socketOpts.getOpts())
                .withPoolingOptions(new PoolingOptions()
                        .setMaxRequestsPerConnection(HostDistance.LOCAL, max_requests_local)
                        .setMaxRequestsPerConnection(HostDistance.REMOTE, max_requests_remote));
        this.clusterBuilder.withQueryOptions(queryOpts.getOpts());
        this.clusterBuilder.withCompression(StringUtils.isEmpty(compression) ? Compression.NONE : Compression.valueOf(compression.toUpperCase()));
        if (ssl) {
            this.clusterBuilder.withSSL();
        }
        if (!jmx) {
            this.clusterBuilder.withoutJMXReporting();
        }
        if (!metrics) {
            this.clusterBuilder.withoutMetrics();
        }
        if (credentials) {
            this.clusterBuilder.withCredentials(username, password);
        }
        if (!isInstall()) {
            initSession();
        }
    }

    public Cluster getCluster() {
        return cluster;
    }

    public Session getSession() {
        if (!isInstall()) {
            return session;
        } else {
            if (session == null) {
                initSession();
            }
            return session;
        }
    }

    public String getKeyspaceName() {
        return keyspaceName;
    }

    private boolean isInstall() {
        return environment.acceptsProfiles("install");
    }

    private void initSession() {
        long endTime = System.currentTimeMillis() + initTimeout;
        while (System.currentTimeMillis() < endTime) {
            try {
                cluster = clusterBuilder.build();
                cluster.init();
                if (this.keyspaceName != null) {
                    session = cluster.connect(keyspaceName);
                } else {
                    session = cluster.connect();
                }
//                For Cassandra Driver version 3.5.0
                DefaultPropertyMapper propertyMapper = new DefaultPropertyMapper();
                propertyMapper.setPropertyAccessStrategy(PropertyAccessStrategy.FIELDS);
                MappingConfiguration configuration = MappingConfiguration.builder().withPropertyMapper(propertyMapper).build();
                mappingManager = new MappingManager(session, configuration);
//                For Cassandra Driver version 3.0.0
//                mappingManager = new MappingManager(session);
                break;
            } catch (Exception e) {
                log.warn("Failed to initialize cassandra cluster due to {}. Will retry in {} ms", e.getMessage(), initRetryInterval);
                try {
                    Thread.sleep(initRetryInterval);
                } catch (InterruptedException ie) {
                    log.warn("Failed to wait until retry", ie);
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @PreDestroy
    public void close() {
        if (cluster != null) {
            cluster.close();
        }
    }

    private List<InetSocketAddress> getContactPoints(String url) {
        List<InetSocketAddress> result;
        if (StringUtils.isBlank(url)) {
            result = Collections.emptyList();
        } else {
            result = new ArrayList<>();
            for (String hostPort : url.split(COMMA)) {
                String host = hostPort.split(COLON)[0];
                Integer port = Integer.valueOf(hostPort.split(COLON)[1]);
                result.add(new InetSocketAddress(host, port));
            }
        }
        return result;
    }

    public ConsistencyLevel getDefaultReadConsistencyLevel() {
        return queryOpts.getDefaultReadConsistencyLevel();
    }

    public ConsistencyLevel getDefaultWriteConsistencyLevel() {
        return queryOpts.getDefaultWriteConsistencyLevel();
    }

}
