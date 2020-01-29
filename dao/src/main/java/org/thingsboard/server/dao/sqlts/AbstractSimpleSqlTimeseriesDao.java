/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.dao.sqlts;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.Aggregation;
import org.thingsboard.server.common.data.kv.ReadTsKvQuery;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.dao.model.sql.AbstractTsKvEntity;
import org.thingsboard.server.dao.sql.TbSqlBlockingQueue;
import org.thingsboard.server.dao.sql.TbSqlBlockingQueueParams;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractSimpleSqlTimeseriesDao<T extends AbstractTsKvEntity> extends AbstractSqlTimeseriesDao {

    @Autowired
    private InsertTsRepository<T> insertRepository;

    @Value("${sql.ts.batch_size:1000}")
    private int tsBatchSize;

    @Value("${sql.ts.batch_max_delay:100}")
    private long tsMaxDelay;

    @Value("${sql.ts.stats_print_interval_ms:1000}")
    private long tsStatsPrintIntervalMs;

    protected TbSqlBlockingQueue<EntityContainer<T>> tsQueue;

    @PostConstruct
    protected void init() {
        super.init();
        TbSqlBlockingQueueParams tsParams = TbSqlBlockingQueueParams.builder()
                .logName("TS")
                .batchSize(tsBatchSize)
                .maxDelay(tsMaxDelay)
                .statsPrintIntervalMs(tsStatsPrintIntervalMs)
                .build();
        tsQueue = new TbSqlBlockingQueue<>(tsParams);
        tsQueue.init(logExecutor, v -> insertRepository.saveOrUpdate(v));
    }

    @PreDestroy
    protected void destroy() {
        super.init();
        if (tsQueue != null) {
            tsQueue.destroy();
        }
    }

    protected ListenableFuture<List<TsKvEntry>> findAllAsync(TenantId tenantId, EntityId entityId, ReadTsKvQuery query) {
        if (query.getAggregation() == Aggregation.NONE) {
            return findAllAsyncWithLimit(entityId, query);
        } else {
            long stepTs = query.getStartTs();
            List<ListenableFuture<Optional<TsKvEntry>>> futures = new ArrayList<>();
            while (stepTs < query.getEndTs()) {
                long startTs = stepTs;
                long endTs = stepTs + query.getInterval();
                long ts = startTs + (endTs - startTs) / 2;
                futures.add(findAndAggregateAsync(entityId, query.getKey(), startTs, endTs, ts, query.getAggregation()));
                stepTs = endTs;
            }
            return getTskvEntriesFuture(Futures.allAsList(futures));
        }
    }

    protected abstract ListenableFuture<Optional<TsKvEntry>> findAndAggregateAsync(EntityId entityId, String key, long startTs, long endTs, long ts, Aggregation aggregation);

    protected abstract ListenableFuture<List<TsKvEntry>> findAllAsyncWithLimit(EntityId entityId, ReadTsKvQuery query);

    protected SettableFuture<T> setFutures(List<CompletableFuture<T>> entitiesFutures) {
        SettableFuture<T> listenableFuture = SettableFuture.create();
        CompletableFuture<List<T>> entities =
                CompletableFuture.allOf(entitiesFutures.toArray(new CompletableFuture[entitiesFutures.size()]))
                        .thenApply(v -> entitiesFutures.stream()
                                .map(CompletableFuture::join)
                                .collect(Collectors.toList()));

        entities.whenComplete((tsKvEntities, throwable) -> {
            if (throwable != null) {
                listenableFuture.setException(throwable);
            } else {
                T result = null;
                for (T entity : tsKvEntities) {
                    if (entity.isNotEmpty()) {
                        result = entity;
                        break;
                    }
                }
                listenableFuture.set(result);
            }
        });
        return listenableFuture;
    }

    protected void switchAgregation(EntityId entityId, String key, long startTs, long endTs, Aggregation aggregation, List<CompletableFuture<T>> entitiesFutures) {
        switch (aggregation) {
            case AVG:
                findAvg(entityId, key, startTs, endTs, entitiesFutures);
                break;
            case MAX:
                findMax(entityId, key, startTs, endTs, entitiesFutures);
                break;
            case MIN:
                findMin(entityId, key, startTs, endTs, entitiesFutures);
                break;
            case SUM:
                findSum(entityId, key, startTs, endTs, entitiesFutures);
                break;
            case COUNT:
                findCount(entityId, key, startTs, endTs, entitiesFutures);
                break;
            default:
                throw new IllegalArgumentException("Not supported aggregation type: " + aggregation);
        }
    }


    protected abstract void findCount(EntityId entityId, String key, long startTs, long endTs, List<CompletableFuture<T>> entitiesFutures);

    protected abstract void findSum(EntityId entityId, String key, long startTs, long endTs, List<CompletableFuture<T>> entitiesFutures);

    protected abstract void findMin(EntityId entityId, String key, long startTs, long endTs, List<CompletableFuture<T>> entitiesFutures);

    protected abstract void findMax(EntityId entityId, String key, long startTs, long endTs, List<CompletableFuture<T>> entitiesFutures);

    protected abstract void findAvg(EntityId entityId, String key, long startTs, long endTs, List<CompletableFuture<T>> entitiesFutures);
}