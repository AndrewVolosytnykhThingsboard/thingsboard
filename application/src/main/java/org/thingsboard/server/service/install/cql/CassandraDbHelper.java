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
package org.thingsboard.server.service.install.cql;

import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.protocol.internal.ProtocolConstants;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.thingsboard.server.dao.cassandra.guava.GuavaSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static org.thingsboard.server.service.install.DatabaseHelper.CSV_DUMP_FORMAT;

public class CassandraDbHelper {

    public static Path dumpCfIfExists(KeyspaceMetadata ks, GuavaSession session, String cfName,
                                      String[] columns, String[] defaultValues, String dumpPrefix) throws Exception {
        return dumpCfIfExists(ks, session, cfName, columns, defaultValues, dumpPrefix, false);
    }

    public static Path dumpCfIfExists(KeyspaceMetadata ks, GuavaSession session, String cfName,
                                      String[] columns, String[] defaultValues, String dumpPrefix, boolean printHeader) throws Exception {
        if (ks.getTable(cfName) != null) {
            Path dumpFile = Files.createTempFile(dumpPrefix, null);
            Files.deleteIfExists(dumpFile);
            CSVFormat csvFormat = CSV_DUMP_FORMAT;
            if (printHeader) {
                csvFormat = csvFormat.withHeader(columns);
            }
            try (CSVPrinter csvPrinter = new CSVPrinter(Files.newBufferedWriter(dumpFile), csvFormat)) {
                Statement stmt = SimpleStatement.newInstance("SELECT * FROM " + cfName);
                stmt.setPageSize(1000);
                ResultSet rs = session.execute(stmt);
                Iterator<Row> iter = rs.iterator();
                while (iter.hasNext()) {
                    Row row = iter.next();
                    if (row != null) {
                        dumpRow(row, columns, defaultValues, csvPrinter);
                    }
                }
            }
            return dumpFile;
        } else {
            return null;
        }
    }

    public static List<String[]> loadData(KeyspaceMetadata ks, GuavaSession session, String cfName, String statement, String[] columns) throws Exception {
        List<String[]> result = new ArrayList<>();
        if (ks.getTable(cfName) != null) {
            Statement stmt = SimpleStatement.newInstance(statement);
            stmt.setPageSize(1000);
            ResultSet rs = session.execute(stmt);
            Iterator<Row> iter = rs.iterator();
            while (iter.hasNext()) {
                Row row = iter.next();
                if (row != null) {
                    String[] values = new String[columns.length];
                    for (int i=0;i<columns.length;i++) {
                        String column = columns[i];
                        values[i] = getColumnValue(column, "", row);
                    }
                    result.add(values);
                }
            }
        }
        return result;
    }

    public static void appendToEndOfLine(Path targetDumpFile, String toAppend) throws Exception {
        Path tmp = Files.createTempFile(null, null);
        try (CSVParser csvParser = new CSVParser(Files.newBufferedReader(targetDumpFile), CSV_DUMP_FORMAT)) {
            try (CSVPrinter csvPrinter = new CSVPrinter(Files.newBufferedWriter(tmp), CSV_DUMP_FORMAT)) {
                csvParser.forEach(record -> {
                    List<String> newRecord = new ArrayList<>();
                    record.forEach(val -> newRecord.add(val));
                    newRecord.add(toAppend);
                    try {
                        csvPrinter.printRecord(newRecord);
                    } catch (IOException e) {
                        throw new RuntimeException("Error appending to EOL", e);
                    }
                });
            }
        }
        Files.move(tmp, targetDumpFile, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void loadCf(KeyspaceMetadata ks, GuavaSession session, String cfName, String[] columns, Path sourceFile) throws Exception {
        loadCf(ks, session, cfName, columns, sourceFile, false);
    }

    public static void loadCf(KeyspaceMetadata ks, GuavaSession session, String cfName, String[] columns, Path sourceFile, boolean parseHeader) throws Exception {
        TableMetadata tableMetadata = ks.getTable(cfName).get();
        PreparedStatement prepared = session.prepare(createInsertStatement(cfName, columns));
        CSVFormat csvFormat = CSV_DUMP_FORMAT;
        if (parseHeader) {
            csvFormat = csvFormat.withFirstRecordAsHeader();
        } else {
            csvFormat = CSV_DUMP_FORMAT.withHeader(columns);
        }
        try (CSVParser csvParser = new CSVParser(Files.newBufferedReader(sourceFile), csvFormat)) {
            csvParser.forEach(record -> {
                BoundStatementBuilder boundStatementBuilder = new BoundStatementBuilder(prepared.bind());
                for (String column : columns) {
                    setColumnValue(tableMetadata, column, record, boundStatementBuilder);
                }
                session.execute(boundStatementBuilder.build());
            });
        }
    }

    private static void dumpRow(Row row, String[] columns, String[] defaultValues, CSVPrinter csvPrinter) throws Exception {
        List<String> record = new ArrayList<>();
        for (int i=0;i<columns.length;i++) {
            String column = columns[i];
            String defaultValue;
            if (defaultValues != null && i < defaultValues.length) {
                defaultValue = defaultValues[i];
            } else {
                defaultValue = "";
            }
            record.add(getColumnValue(column, defaultValue, row));
        }
        csvPrinter.printRecord(record);
    }

    private static String getColumnValue(String column, String defaultValue, Row row) {
        int index = row.getColumnDefinitions().firstIndexOf(column);
        if (index > -1) {
            String str;
            DataType type = row.getColumnDefinitions().get(index).getType();
            try {
                if (row.isNull(index)) {
                    return null;
                } else if (type.getProtocolCode() == ProtocolConstants.DataType.DOUBLE) {
                    str = new Double(row.getDouble(index)).toString();
                } else if (type.getProtocolCode() == ProtocolConstants.DataType.INT) {
                    str = new Integer(row.getInt(index)).toString();
                } else if (type.getProtocolCode() == ProtocolConstants.DataType.BIGINT) {
                    str = new Long(row.getLong(index)).toString();
                } else if (type.getProtocolCode() == ProtocolConstants.DataType.UUID) {
                    str = row.getUuid(index).toString();
                } else if (type.getProtocolCode() == ProtocolConstants.DataType.TIMEUUID) {
                    str = row.getUuid(index).toString();
                } else if (type.getProtocolCode() == ProtocolConstants.DataType.FLOAT) {
                    str = new Float(row.getFloat(index)).toString();
                } else if (type.getProtocolCode() == ProtocolConstants.DataType.TIMESTAMP) {
                    str = ""+row.getInstant(index).toEpochMilli();
                } else if (type.getProtocolCode() == ProtocolConstants.DataType.BOOLEAN) {
                    str = new Boolean(row.getBoolean(index)).toString();
                } else {
                    str = row.getString(index);
                }
            } catch (Exception e) {
                str = "";
            }
            return str;
        } else {
            return defaultValue;
        }
    }

    private static String createInsertStatement(String cfName, String[] columns) {
        StringBuilder insertStatementBuilder = new StringBuilder();
        insertStatementBuilder.append("INSERT INTO ").append(cfName).append(" (");
        for (String column : columns) {
            insertStatementBuilder.append(column).append(",");
        }
        insertStatementBuilder.deleteCharAt(insertStatementBuilder.length() - 1);
        insertStatementBuilder.append(") VALUES (");
        for (String column : columns) {
            insertStatementBuilder.append("?").append(",");
        }
        insertStatementBuilder.deleteCharAt(insertStatementBuilder.length() - 1);
        insertStatementBuilder.append(")");
        return insertStatementBuilder.toString();
    }

    private static void setColumnValue(TableMetadata tableMetadata, String column,
                                       CSVRecord record, BoundStatementBuilder boundStatementBuilder) {
        String value = record.get(column);
        DataType type = tableMetadata.getColumn(column).get().getType();
        if (value == null) {
            boundStatementBuilder.setToNull(column);
        } else if (type.getProtocolCode() == ProtocolConstants.DataType.DOUBLE) {
            boundStatementBuilder.setDouble(column, Double.valueOf(value));
        } else if (type.getProtocolCode() == ProtocolConstants.DataType.INT) {
            boundStatementBuilder.setInt(column, Integer.valueOf(value));
        } else if (type.getProtocolCode() == ProtocolConstants.DataType.BIGINT) {
            boundStatementBuilder.setLong(column, Long.valueOf(value));
        } else if (type.getProtocolCode() == ProtocolConstants.DataType.UUID) {
            boundStatementBuilder.setUuid(column, UUID.fromString(value));
        } else if (type.getProtocolCode() == ProtocolConstants.DataType.TIMEUUID) {
            boundStatementBuilder.setUuid(column, UUID.fromString(value));
        } else if (type.getProtocolCode() == ProtocolConstants.DataType.FLOAT) {
            boundStatementBuilder.setFloat(column, Float.valueOf(value));
        } else if (type.getProtocolCode() == ProtocolConstants.DataType.TIMESTAMP) {
            boundStatementBuilder.setInstant(column, Instant.ofEpochMilli(Long.valueOf(value)));
        } else if (type.getProtocolCode() == ProtocolConstants.DataType.BOOLEAN) {
            boundStatementBuilder.setBoolean(column, Boolean.valueOf(value));
        } else {
            boundStatementBuilder.setString(column, value);
        }
    }

}
