/*
 * Copyright (C) 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.teleport.v2.cdc.mappers;

import com.google.api.services.bigquery.model.TableRow;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.teleport.v2.cdc.merge.MergeInfo;
import com.google.cloud.teleport.v2.transforms.BigQueryConverters;
import com.google.cloud.teleport.v2.utils.BigQueryTableCache;
import com.google.cloud.teleport.v2.utils.DataStreamClient;
import com.google.cloud.teleport.v2.utils.DataStreamPkCache;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.beam.sdk.metrics.Counter;
import org.apache.beam.sdk.metrics.Metrics;
import org.apache.beam.sdk.transforms.FlatMapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.apache.beam.vendor.guava.v20_0.com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class {@link MergeInfoMapper}.
 */
public class MergeInfoMapper
    extends PTransform<PCollection<KV<TableId, TableRow>>, PCollection<MergeInfo>> {

  public static final List<String> ORACLE_ORDER_BY_FIELDS =
      Arrays.asList("_metadata_timestamp", "_metadata_scn");
  public static final List<String> MYSQL_ORDER_BY_FIELDS =
      Arrays.asList("_metadata_timestamp", "_metadata_log_file", "_metadata_log_position");
  public static final String METADATA_DELETED = "_metadata_deleted";
  public static final String METADATA_REPLICA_TABLE = "_metadata_table";

  private static final Logger LOG = LoggerFactory.getLogger(MergeInfoMapper.class);
  private DataStreamClient dataStreamClient;
  private String stagingDataset;
  private String stagingTable;
  private String replicaDataset;
  private String replicaTable;
  private static BigQueryTableCache tableCache;
  private static DataStreamPkCache pkCache;

  private final Counter foregoneMerges = Metrics.counter(MergeInfoMapper.class, "mergesForegone");

  public MergeInfoMapper(
      DataStreamClient dataStreamClient,
      String stagingDataset, String stagingTable,
      String replicaDataset, String replicaTable) {
    this.stagingDataset = stagingDataset;
    this.stagingTable = stagingTable;

    this.replicaDataset = replicaDataset;
    this.replicaTable = replicaTable;

    this.dataStreamClient = dataStreamClient;
  }

  public MergeInfoMapper withDataStreamRootUrl(String url) {
    if (this.dataStreamClient != null) {
      this.dataStreamClient.setRootUrl(url);
    }

    return this;
  }

  private synchronized void setUpTableCache() {
    if (tableCache == null) {
      BigQuery bigquery = BigQueryOptions.getDefaultInstance().getService();
      tableCache = new BigQueryTableCache(bigquery);
    }
  }

  private synchronized void setUpPkCache() {
    if (pkCache == null) {
      pkCache = new DataStreamPkCache(this.dataStreamClient);
    }
  }

  public BigQueryTableCache getTableCache() {
    if (this.tableCache == null) {
      setUpTableCache();
    }

    return this.tableCache;
  }

  public DataStreamPkCache getPkCache() {
    if (this.pkCache == null) {
      setUpPkCache();
    }

    return this.pkCache;
  }

  @Override
  public PCollection<MergeInfo> expand(PCollection<KV<TableId, TableRow>> input) {
    return input.apply(
        FlatMapElements.into(TypeDescriptor.of(MergeInfo.class))
            .via(
                element -> {
                  try {
                    TableId tableId = element.getKey();
                    TableRow row = element.getValue();

                    String streamName = (String) row.get("_metadata_stream");
                    String schemaName = (String) row.get("_metadata_schema");
                    String tableName = (String) row.get("_metadata_table");

                    List<String> mergeFields = getMergeFields(tableId, row);
                    List<String> allPkFields = getPrimaryKeys(
                        streamName, schemaName, tableName, mergeFields);
                    List<String> allSortFields = getSortFields(row);

                    if (allPkFields.size() == 0) {
                      LOG.warn("Unable to retrieve primary keys for table {}.{} in stream {}. "
                              + "Not performing merge-based consolidation.",
                          schemaName, tableName, streamName);
                      foregoneMerges.inc();
                      return Lists.newArrayList();
                    } else if (allSortFields.size() == 0) {
                      LOG.warn("Unable to retrieve sort keys for table {}.{} in stream {}. "
                              + "Not performing merge-based consolidation.",
                          schemaName, tableName, streamName);
                      foregoneMerges.inc();
                    }

                    MergeInfo mergeInfo = MergeInfo.create(
                        allPkFields,
                        allSortFields,
                        METADATA_DELETED,
                        String.format("%s.%s", // Staging Table
                            BigQueryConverters
                                .formatStringTemplate(stagingDataset, row),
                            BigQueryConverters
                                .formatStringTemplate(stagingTable, row)).replaceAll("\\$", "_"),
                        String.format("%s.%s", // Replica Table
                            BigQueryConverters
                                .formatStringTemplate(replicaDataset, row),
                            BigQueryConverters
                                .formatStringTemplate(replicaTable, row)).replaceAll("\\$", "_"),
                        mergeFields);

                    return Lists.newArrayList(mergeInfo);
                  } catch (Exception e) {
                    LOG.error(
                      "Merge Info Failure, skipping merge for: {} -> {}",
                      element.getValue().toString(),
                      e.toString());
                    return Lists.newArrayList();
                  }
                }));
  }

  public List<String> getSortFields(TableRow row) {
    String sourceType = (String) row.get("_metadata_source_type");
    if (sourceType.equals("mysql")) {
      return MYSQL_ORDER_BY_FIELDS;
    } else {
      return ORACLE_ORDER_BY_FIELDS;
    }
  }

  public List<String> getPrimaryKeys(String streamName, String schemaName, String tableName,
      List<String> mergeFields) {
    List<String> searchKey = ImmutableList.of(streamName, schemaName, tableName);
    List<String> primaryKeys = getPkCache().get(searchKey);

    if (primaryKeys == null) {
      // An error occurred and was logged upstream
      return new ArrayList<String>();
    }

    if (primaryKeys.size() == 0 && mergeFields.contains("_metadata_row_id")) {
      // TODO when DataStream releases new outputs, change logic here.
      // Possibly move upstream to DataStream Client
      primaryKeys.add("_metadata_row_id");
    }

    return primaryKeys;
  }

  public List<String> getMergeFields(TableId tableId, TableRow row) {
    List<String> mergeFields = new ArrayList<String>();
    Table table = getTableCache().get(tableId);
    FieldList tableFields = table.getDefinition().getSchema().getFields();

    for (Field field : tableFields) {
      mergeFields.add(field.getName());
    }

    return mergeFields;
  }
}
