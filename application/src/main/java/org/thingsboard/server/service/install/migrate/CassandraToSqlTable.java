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
package org.thingsboard.server.service.install.migrate;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.Statement;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.internal.util.JdbcExceptionHelper;
import org.postgresql.util.PSQLException;
import org.thingsboard.server.common.data.UUIDConverter;
import org.thingsboard.server.dao.exception.DataValidationException;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Data
@Slf4j
public class CassandraToSqlTable {

    private static final int DEFAULT_BATCH_SIZE = 10000;

    private String cassandraCf;
    private String sqlTableName;

    private List<CassandraToSqlColumn> columns;

    private int batchSize = DEFAULT_BATCH_SIZE;

    private PreparedStatement sqlInsertStatement;

    public CassandraToSqlTable(String tableName, CassandraToSqlColumn... columns) {
        this(tableName, tableName, DEFAULT_BATCH_SIZE, columns);
    }

    public CassandraToSqlTable(String tableName, String sqlTableName, CassandraToSqlColumn... columns) {
        this(tableName, sqlTableName, DEFAULT_BATCH_SIZE, columns);
    }

    public CassandraToSqlTable(String tableName, int batchSize, CassandraToSqlColumn... columns) {
        this(tableName, tableName, batchSize, columns);
    }

    public CassandraToSqlTable(String cassandraCf, String sqlTableName, int batchSize, CassandraToSqlColumn... columns) {
        this.cassandraCf = cassandraCf;
        this.sqlTableName = sqlTableName;
        this.batchSize = batchSize;
        this.columns = Arrays.asList(columns);
        for (int i=0;i<columns.length;i++) {
            this.columns.get(i).setIndex(i);
            this.columns.get(i).setSqlIndex(i+1);
        }
    }

    public void migrateToSql(Session session, Connection conn) throws SQLException {
        log.info("[{}] Migrating data from cassandra '{}' Column Family to '{}' SQL table...", this.sqlTableName, this.cassandraCf, this.sqlTableName);
        DatabaseMetaData metadata = conn.getMetaData();
        java.sql.ResultSet resultSet = metadata.getColumns(null, null, this.sqlTableName, null);
        while (resultSet.next()) {
            String name = resultSet.getString("COLUMN_NAME");
            int sqlType = resultSet.getInt("DATA_TYPE");
            int size = resultSet.getInt("COLUMN_SIZE");
            CassandraToSqlColumn column = this.getColumn(name);
            column.setSize(size);
            column.setSqlType(sqlType);
        }
        this.sqlInsertStatement = createSqlInsertStatement(conn);
        Statement cassandraSelectStatement = createCassandraSelectStatement();
        cassandraSelectStatement.setFetchSize(100);
        ResultSet rs = session.execute(cassandraSelectStatement);
        Iterator<Row> iter = rs.iterator();
        int rowCounter = 0;
        List<CassandraToSqlColumnData[]> batchData;
        boolean hasNext;
        do {
            batchData = this.extractBatchData(iter);
            hasNext = batchData.size() == this.batchSize;
            this.batchInsert(batchData, conn);
            rowCounter += batchData.size();
            log.info("[{}] {} records migrated so far...", this.sqlTableName, rowCounter);
        } while (hasNext);
        this.sqlInsertStatement.close();
        log.info("[{}] {} total records migrated.", this.sqlTableName, rowCounter);
        log.info("[{}] Finished migration data from cassandra '{}' Column Family to '{}' SQL table.",
                this.sqlTableName, this.cassandraCf, this.sqlTableName);
    }

    private List<CassandraToSqlColumnData[]> extractBatchData(Iterator<Row> iter) {
        List<CassandraToSqlColumnData[]> batchData = new ArrayList<>();
        while (iter.hasNext() && batchData.size() < this.batchSize) {
            Row row = iter.next();
            if (row != null) {
                CassandraToSqlColumnData[] data = this.extractRowData(row);
                batchData.add(data);
            }
        }
        return batchData;
    }

    private CassandraToSqlColumnData[] extractRowData(Row row) {
        CassandraToSqlColumnData[] data = new CassandraToSqlColumnData[this.columns.size()];
        for (CassandraToSqlColumn column: this.columns) {
            String value = column.getColumnValue(row);
            data[column.getIndex()] = new CassandraToSqlColumnData(value);
        }
        return this.validateColumnData(data);
    }

    private CassandraToSqlColumnData[] validateColumnData(CassandraToSqlColumnData[] data) {
        for (int i=0;i<data.length;i++) {
            CassandraToSqlColumn column = this.columns.get(i);
            if (column.getType() == CassandraToSqlColumnType.STRING) {
                CassandraToSqlColumnData columnData = data[i];
                String value = columnData.getValue();
                if (value != null && value.length() > column.getSize()) {
                    log.warn("[{}] Value size [{}] exceeds maximum size [{}] of column [{}] and will be truncated!",
                            this.sqlTableName,
                            value.length(), column.getSize(), column.getSqlColumnName());
                    log.warn("[{}] Affected data:\n{}", this.sqlTableName, this.dataToString(data));
                    value = value.substring(0, column.getSize());
                    columnData.setOriginalValue(value);
                    columnData.setValue(value);
                }
            }
        }
        return data;
    }

    private void batchInsert(List<CassandraToSqlColumnData[]> batchData, Connection conn) throws SQLException {
        boolean retry = false;
        for (CassandraToSqlColumnData[] data : batchData) {
            for (CassandraToSqlColumn column: this.columns) {
                column.setColumnValue(this.sqlInsertStatement, data[column.getIndex()].getValue());
            }
            try {
                this.sqlInsertStatement.executeUpdate();
            } catch (SQLException e) {
                if (this.handleInsertException(batchData, data, conn, e)) {
                    retry = true;
                    break;
                } else {
                    throw e;
                }
            }
        }
        if (retry) {
            this.batchInsert(batchData, conn);
        } else {
            conn.commit();
        }
    }

    private boolean handleInsertException(List<CassandraToSqlColumnData[]> batchData,
                                          CassandraToSqlColumnData[] data,
                                          Connection conn, SQLException ex) throws SQLException {
        conn.commit();
        String constraint = extractConstraintName(ex).orElse(null);
        if (constraint != null) {
            if (this.onConstraintViolation(batchData, data, constraint)) {
                return true;
            } else {
                log.error("[{}] Unhandled constraint violation [{}] during insert!", this.sqlTableName, constraint);
                log.error("[{}] Affected data:\n{}", this.sqlTableName, this.dataToString(data));
            }
        } else {
            log.error("[{}] Unhandled exception during insert!", this.sqlTableName);
            log.error("[{}] Affected data:\n{}", this.sqlTableName, this.dataToString(data));
        }
        return false;
    }

    private String dataToString(CassandraToSqlColumnData[] data) {
        StringBuffer stringData = new StringBuffer("{\n");
        for (int i=0;i<data.length;i++) {
            String columnName = this.columns.get(i).getSqlColumnName();
            String value = data[i].getLogValue();
            stringData.append("\"").append(columnName).append("\": ").append("[").append(value).append("]\n");
        }
        stringData.append("}");
        return stringData.toString();
    }

    protected boolean onConstraintViolation(List<CassandraToSqlColumnData[]> batchData,
                                            CassandraToSqlColumnData[] data, String constraint) {
        return false;
    }

    protected void handleUniqueNameViolation(CassandraToSqlColumnData[] data, String entityType) {
        CassandraToSqlColumn nameColumn = this.getColumn("name");
        CassandraToSqlColumn searchTextColumn = this.getColumn("search_text");
        CassandraToSqlColumnData nameColumnData = data[nameColumn.getIndex()];
        CassandraToSqlColumnData searchTextColumnData = data[searchTextColumn.getIndex()];
        String prevName = nameColumnData.getValue();
        String newName = nameColumnData.getNextConstraintStringValue(nameColumn);
        nameColumnData.setValue(newName);
        searchTextColumnData.setValue(searchTextColumnData.getNextConstraintStringValue(searchTextColumn));
        String id = UUIDConverter.fromString(this.getColumnData(data, "id").getValue()).toString();
        log.warn("Found {} with duplicate name [id:[{}]]. Attempting to rename {} from '{}' to '{}'...", entityType, id, entityType, prevName, newName);
    }

    protected void handleUniqueEmailViolation(CassandraToSqlColumnData[] data) {
        CassandraToSqlColumn emailColumn = this.getColumn("email");
        CassandraToSqlColumn searchTextColumn = this.getColumn("search_text");
        CassandraToSqlColumnData emailColumnData = data[emailColumn.getIndex()];
        CassandraToSqlColumnData searchTextColumnData = data[searchTextColumn.getIndex()];
        String prevEmail = emailColumnData.getValue();
        String newEmail = emailColumnData.getNextConstraintEmailValue(emailColumn);
        emailColumnData.setValue(newEmail);
        searchTextColumnData.setValue(searchTextColumnData.getNextConstraintEmailValue(searchTextColumn));
        String id = UUIDConverter.fromString(this.getColumnData(data, "id").getValue()).toString();
        log.warn("Found user with duplicate email [id:[{}]]. Attempting to rename email from '{}' to '{}'...", id, prevEmail, newEmail);
    }

    protected void ignoreRecord(List<CassandraToSqlColumnData[]> batchData, CassandraToSqlColumnData[] data) {
        log.warn("[{}] Affected data:\n{}", this.sqlTableName, this.dataToString(data));
        int index = batchData.indexOf(data);
        if (index > 0) {
            batchData.remove(index);
        }
    }

    protected CassandraToSqlColumn getColumn(String sqlColumnName) {
        return this.columns.stream().filter(col -> col.getSqlColumnName().equals(sqlColumnName)).findFirst().get();
    }

    protected CassandraToSqlColumnData getColumnData(CassandraToSqlColumnData[] data, String sqlColumnName) {
        CassandraToSqlColumn column = this.getColumn(sqlColumnName);
        return data[column.getIndex()];
    }

    private Optional<String> extractConstraintName(SQLException ex) {
        final String sqlState = JdbcExceptionHelper.extractSqlState( ex );
        if (sqlState != null) {
            String sqlStateClassCode = JdbcExceptionHelper.determineSqlStateClassCode( sqlState );
            if ( sqlStateClassCode != null ) {
                if (Arrays.asList(
                        "23",	// "integrity constraint violation"
                        "27",	// "triggered data change violation"
                        "44"	// "with check option violation"
                ).contains(sqlStateClassCode)) {
                    if (ex instanceof PSQLException) {
                        return Optional.of(((PSQLException)ex).getServerErrorMessage().getConstraint());
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Statement createCassandraSelectStatement() {
        StringBuilder selectStatementBuilder = new StringBuilder();
        selectStatementBuilder.append("SELECT ");
        for (CassandraToSqlColumn column : columns) {
            selectStatementBuilder.append(column.getCassandraColumnName()).append(",");
        }
        selectStatementBuilder.deleteCharAt(selectStatementBuilder.length() - 1);
        selectStatementBuilder.append(" FROM ").append(cassandraCf);
        return new SimpleStatement(selectStatementBuilder.toString());
    }

    private PreparedStatement createSqlInsertStatement(Connection conn) throws SQLException {
        StringBuilder insertStatementBuilder = new StringBuilder();
        insertStatementBuilder.append("INSERT INTO ").append(this.sqlTableName).append(" (");
        for (CassandraToSqlColumn column : columns) {
            insertStatementBuilder.append(column.getSqlColumnName()).append(",");
        }
        insertStatementBuilder.deleteCharAt(insertStatementBuilder.length() - 1);
        insertStatementBuilder.append(") VALUES (");
        for (CassandraToSqlColumn column : columns) {
            if (column.getType() == CassandraToSqlColumnType.JSON) {
                insertStatementBuilder.append("cast(? AS json)");
            } else {
                insertStatementBuilder.append("?");
            }
            insertStatementBuilder.append(",");
        }
        insertStatementBuilder.deleteCharAt(insertStatementBuilder.length() - 1);
        insertStatementBuilder.append(")");
        return conn.prepareStatement(insertStatementBuilder.toString());
    }

}
