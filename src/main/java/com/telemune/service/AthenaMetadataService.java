package com.telemune.service;

import com.telemune.model.AthenaModels.ColumnInfo;
import com.telemune.model.AthenaModels.Database;
import com.telemune.model.AthenaModels.Partition;
import com.telemune.model.AthenaModels.TableInfo;
import com.telemune.model.AthenaModels.WorkgroupInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.GetWorkGroupRequest;
import software.amazon.awssdk.services.athena.model.ListWorkGroupsRequest;
import software.amazon.awssdk.services.athena.model.ListWorkGroupsResponse;
import software.amazon.awssdk.services.athena.model.WorkGroupConfiguration;

import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.Column;
import software.amazon.awssdk.services.glue.model.GetDatabasesRequest;
import software.amazon.awssdk.services.glue.model.GetDatabasesResponse;
import software.amazon.awssdk.services.glue.model.GetPartitionsRequest;
import software.amazon.awssdk.services.glue.model.GetPartitionsResponse;
import software.amazon.awssdk.services.glue.model.GetTableRequest;
import software.amazon.awssdk.services.glue.model.GetTableResponse;
import software.amazon.awssdk.services.glue.model.GetTablesRequest;
import software.amazon.awssdk.services.glue.model.GetTablesResponse;
import software.amazon.awssdk.services.glue.model.Table;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Metadata exploration via AWS Glue Data Catalog (Athena's underlying schema store)
 * and Athena workgroup management.
 *
 * All AWS SDK model types are imported explicitly to avoid ambiguity with our own
 * AthenaModels records (Database, ColumnInfo, Partition, etc.).
 */
@Service
public class AthenaMetadataService {

    private static final Logger log = LoggerFactory.getLogger(AthenaMetadataService.class);

    private final GlueClient glueClient;
    private final AthenaClient athenaClient;

    @Value("${athena.workgroup}")
    private String defaultWorkgroup;

    public AthenaMetadataService(GlueClient glueClient, AthenaClient athenaClient) {
        this.glueClient = glueClient;
        this.athenaClient = athenaClient;
    }

    // ── Databases ─────────────────────────────────────────────────────────────

    public List<Database> listDatabases() {
        GetDatabasesResponse resp = glueClient.getDatabases(
                GetDatabasesRequest.builder().build());

        return resp.databaseList().stream()
                .map(db -> new Database(db.name(), db.description()))
                .collect(Collectors.toList());
    }

    // ── Tables ────────────────────────────────────────────────────────────────

    public List<String> listTables(String database) {
        GetTablesResponse resp = glueClient.getTables(
                GetTablesRequest.builder().databaseName(database).build());

        return resp.tableList().stream()
                .map(Table::name)
                .collect(Collectors.toList());
    }

    public TableInfo describeTable(String database, String tableName) {
        GetTableResponse resp = glueClient.getTable(
                GetTableRequest.builder()
                        .databaseName(database)
                        .name(tableName)
                        .build());

        Table table = resp.table();

        List<ColumnInfo> columns = table.storageDescriptor() != null
                ? table.storageDescriptor().columns().stream()
                        .map(c -> new ColumnInfo(c.name(), c.type(), c.comment()))
                        .collect(Collectors.toList())
                : List.of();

        List<ColumnInfo> partitionKeys = table.partitionKeys() != null
                ? table.partitionKeys().stream()
                        .map(c -> new ColumnInfo(c.name(), c.type(), c.comment()))
                        .collect(Collectors.toList())
                : List.of();

        return new TableInfo(
                table.name(),
                database,
                table.tableType(),
                columns,
                partitionKeys,
                table.parameters() != null ? table.parameters() : Map.of()
        );
    }

    // ── Partitions ────────────────────────────────────────────────────────────

    public List<Partition> listPartitions(String database, String tableName) {
        GetPartitionsResponse resp = glueClient.getPartitions(
                GetPartitionsRequest.builder()
                        .databaseName(database)
                        .tableName(tableName)
                        .build());

        return resp.partitions().stream()
                .map(p -> new Partition(
                        buildPartitionValueMap(database, tableName, p.values()),
                        p.storageDescriptor() != null ? p.storageDescriptor().location() : null
                ))
                .collect(Collectors.toList());
    }

    // ── Workgroups ────────────────────────────────────────────────────────────

    /**
     * ListWorkGroups returns WorkGroupSummary objects which do NOT include
     * configuration details. We fetch each WorkGroup individually to get
     * bytesScannedCutoffPerQuery.
     */
    public List<WorkgroupInfo> listWorkgroups() {
        ListWorkGroupsResponse listResp = athenaClient.listWorkGroups(
                ListWorkGroupsRequest.builder().build());

        List<WorkgroupInfo> result = new ArrayList<>();

        for (var summary : listResp.workGroups()) {
            Long byteCutoff = null;
            try {
                var wg = athenaClient.getWorkGroup(
                        GetWorkGroupRequest.builder()
                                .workGroup(summary.name())
                                .build()
                ).workGroup();

                WorkGroupConfiguration cfg = wg.configuration();
                if (cfg != null) {
                    byteCutoff = cfg.bytesScannedCutoffPerQuery();
                }
            } catch (Exception e) {
                log.warn("Could not fetch config for workgroup {}: {}", summary.name(), e.getMessage());
            }

            result.add(new WorkgroupInfo(
                    summary.name(),
                    summary.stateAsString(),
                    summary.description(),
                    byteCutoff
            ));
        }

        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, String> buildPartitionValueMap(String database, String tableName,
                                                        List<String> values) {
        try {
            List<Column> keys = glueClient.getTable(
                    GetTableRequest.builder()
                            .databaseName(database)
                            .name(tableName)
                            .build()
            ).table().partitionKeys();

            Map<String, String> map = new LinkedHashMap<>();
            for (int i = 0; i < Math.min(keys.size(), values.size()); i++) {
                map.put(keys.get(i).name(), values.get(i));
            }
            return map;
        } catch (Exception e) {
            log.warn("Could not resolve partition key names: {}", e.getMessage());
            Map<String, String> fallback = new LinkedHashMap<>();
            for (int i = 0; i < values.size(); i++) {
                fallback.put("key" + i, values.get(i));
            }
            return fallback;
        }
    }
}
