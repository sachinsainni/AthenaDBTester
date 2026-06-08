package com.telemune.controller;

import com.telemune.model.AthenaModels.*;
import com.telemune.service.AthenaMetadataService;
import com.telemune.service.AthenaQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for AWS Athena.
 *
 * Query:
 *   POST   /athena/query                          → run sync query, wait, return rows
 *   POST   /athena/query/async                    → submit query, return execution ID
 *   GET    /athena/query/{id}/status              → poll query status
 *   GET    /athena/query/{id}/results             → fetch results of completed async query
 *   DELETE /athena/query/{id}                     → cancel running query
 *   GET    /athena/query/history?maxItems=20      → recent query executions
 *   GET    /athena/query/named                    → list saved named queries
 *
 * Metadata:
 *   GET    /athena/databases                      → list databases
 *   GET    /athena/databases/{db}/tables          → list tables in a database
 *   GET    /athena/databases/{db}/tables/{table}  → describe a table (columns, partitions)
 *   GET    /athena/databases/{db}/tables/{table}/partitions → list partitions
 *   GET    /athena/workgroups                     → list workgroups
 */
@RestController
@RequestMapping("/athena")
@CrossOrigin(origins = "*")
public class AthenaController {

    private final AthenaQueryService queryService;
    private final AthenaMetadataService metadataService;

    public AthenaController(AthenaQueryService queryService,
                            AthenaMetadataService metadataService) {
        this.queryService = queryService;
        this.metadataService = metadataService;
    }

    // ── Synchronous query ─────────────────────────────────────────────────────

    @PostMapping("/query")
    public ResponseEntity<ApiResponse<QueryResult>> runQuery(
            @RequestBody QueryRequest req) throws Exception {
        long start = System.currentTimeMillis();
        QueryResult result = queryService.executeQuery(req);
        return ResponseEntity.ok(ApiResponse.ok(result, System.currentTimeMillis() - start));
    }

    // ── Async query ───────────────────────────────────────────────────────────

    @PostMapping("/query/async")
    public ResponseEntity<ApiResponse<QuerySubmitted>> submitAsync(
            @RequestBody QueryRequest req) {
        long start = System.currentTimeMillis();
        QuerySubmitted submitted = queryService.submitQuery(req);
        return ResponseEntity.ok(ApiResponse.ok(submitted, System.currentTimeMillis() - start));
    }

    @GetMapping("/query/{queryExecutionId}/status")
    public ResponseEntity<ApiResponse<QueryStatus>> queryStatus(
            @PathVariable String queryExecutionId) {
        QueryStatus status = queryService.getQueryStatus(queryExecutionId);
        return ResponseEntity.ok(ApiResponse.ok(status, 0));
    }

    @GetMapping("/query/{queryExecutionId}/results")
    public ResponseEntity<ApiResponse<QueryResult>> queryResults(
            @PathVariable String queryExecutionId) {
        QueryResult result = queryService.getQueryResults(queryExecutionId);
        return ResponseEntity.ok(ApiResponse.ok(result, 0));
    }

    @DeleteMapping("/query/{queryExecutionId}")
    public ResponseEntity<ApiResponse<CancelResult>> cancelQuery(
            @PathVariable String queryExecutionId) {
        CancelResult result = queryService.cancelQuery(queryExecutionId);
        return ResponseEntity.ok(ApiResponse.ok(result, 0));
    }

    // ── History & named queries ───────────────────────────────────────────────

    @GetMapping("/query/history")
    public ResponseEntity<ApiResponse<List<QueryHistory>>> queryHistory(
            @RequestParam(defaultValue = "20") int maxItems) {
        List<QueryHistory> history = queryService.getQueryHistory(maxItems);
        return ResponseEntity.ok(ApiResponse.ok(history, 0));
    }

    @GetMapping("/query/named")
    public ResponseEntity<ApiResponse<List<NamedQuery>>> namedQueries() {
        List<NamedQuery> queries = queryService.listNamedQueries();
        return ResponseEntity.ok(ApiResponse.ok(queries, 0));
    }

    // ── Metadata ──────────────────────────────────────────────────────────────

    @GetMapping("/databases")
    public ResponseEntity<ApiResponse<List<Database>>> listDatabases() {
        return ResponseEntity.ok(ApiResponse.ok(metadataService.listDatabases(), 0));
    }

    @GetMapping("/databases/{database}/tables")
    public ResponseEntity<ApiResponse<List<String>>> listTables(
            @PathVariable String database) {
        return ResponseEntity.ok(ApiResponse.ok(metadataService.listTables(database), 0));
    }

    @GetMapping("/databases/{database}/tables/{tableName}")
    public ResponseEntity<ApiResponse<TableInfo>> describeTable(
            @PathVariable String database,
            @PathVariable String tableName) {
        return ResponseEntity.ok(ApiResponse.ok(metadataService.describeTable(database, tableName), 0));
    }

    @GetMapping("/databases/{database}/tables/{tableName}/partitions")
    public ResponseEntity<ApiResponse<List<Partition>>> listPartitions(
            @PathVariable String database,
            @PathVariable String tableName) {
        return ResponseEntity.ok(ApiResponse.ok(metadataService.listPartitions(database, tableName), 0));
    }

    @GetMapping("/workgroups")
    public ResponseEntity<ApiResponse<List<WorkgroupInfo>>> listWorkgroups() {
        return ResponseEntity.ok(ApiResponse.ok(metadataService.listWorkgroups(), 0));
    }
}
