package com.telemune.service;

import com.telemune.exception.AthenaException;
import com.telemune.model.AthenaModels.CancelResult;
import com.telemune.model.AthenaModels.NamedQuery;
import com.telemune.model.AthenaModels.QueryHistory;
import com.telemune.model.AthenaModels.QueryRequest;
import com.telemune.model.AthenaModels.QueryResult;
import com.telemune.model.AthenaModels.QueryStatus;
import com.telemune.model.AthenaModels.QuerySubmitted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.model.GetNamedQueryRequest;
import software.amazon.awssdk.services.athena.model.GetNamedQueryResponse;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.GetQueryResultsRequest;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.ListNamedQueriesRequest;
import software.amazon.awssdk.services.athena.model.ListNamedQueriesResponse;
import software.amazon.awssdk.services.athena.model.ListQueryExecutionsRequest;
import software.amazon.awssdk.services.athena.model.ListQueryExecutionsResponse;
import software.amazon.awssdk.services.athena.model.QueryExecutionContext;
import software.amazon.awssdk.services.athena.model.QueryExecutionStatistics;
import software.amazon.awssdk.services.athena.model.ResultConfiguration;
import software.amazon.awssdk.services.athena.model.Row;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.StopQueryExecutionRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AthenaQueryService {

    private static final Logger log = LoggerFactory.getLogger(AthenaQueryService.class);

    private static final Set<String> BLOCKED_KEYWORDS = Set.of(
            "DROP", "DELETE", "TRUNCATE", "INSERT", "UPDATE", "ALTER", "CREATE",
            "REPLACE", "MERGE", "GRANT", "REVOKE"
    );

    private final AthenaClient athenaClient;

    @Value("${athena.database}")
    private String defaultDatabase;

    @Value("${athena.output-location}")
    private String defaultOutputLocation;

    @Value("${athena.workgroup}")
    private String defaultWorkgroup;

    @Value("${athena.query.timeout-seconds:300}")
    private long timeoutSeconds;

    @Value("${athena.query.max-results:1000}")
    private int maxResults;

    @Value("${athena.query.poll-interval-ms:500}")
    private long pollIntervalMs;

    public AthenaQueryService(AthenaClient athenaClient) {
        this.athenaClient = athenaClient;
    }

    // ── Synchronous query (blocking until complete) ───────────────────────────

    public QueryResult executeQuery(QueryRequest req) throws Exception {
        validateSql(req.sql());
        long start = System.currentTimeMillis();

        String database = req.database() != null ? req.database() : defaultDatabase;
        String workgroup = req.workgroup() != null ? req.workgroup() : defaultWorkgroup;
        String outputLocation = req.outputLocation() != null ? req.outputLocation() : defaultOutputLocation;

        log.info("Executing query on database='{}' workgroup='{}'", database, workgroup);

        String queryExecutionId = startQuery(req.sql(), database, workgroup, outputLocation);
        waitForQuery(queryExecutionId);

        QueryExecutionStatistics stats = getStats(queryExecutionId);  // ← fixed class name
        List<Map<String, String>> rows = fetchAllResults(queryExecutionId);

        boolean truncated = rows.size() >= maxResults;
        long durationMs = System.currentTimeMillis() - start;

        log.info("Query {} completed: {} rows, {} bytes scanned, {}ms",
                queryExecutionId, rows.size(),
                stats != null ? stats.dataScannedInBytes() : 0,
                durationMs);

        return new QueryResult(
                queryExecutionId,
                rows,
                rows.size(),
                stats != null ? stats.dataScannedInBytes() : 0,
                durationMs,
                truncated
        );
    }

    // ── Async: submit only, return execution ID ───────────────────────────────

    public QuerySubmitted submitQuery(QueryRequest req) {
        validateSql(req.sql());
        String database = req.database() != null ? req.database() : defaultDatabase;
        String workgroup = req.workgroup() != null ? req.workgroup() : defaultWorkgroup;
        String outputLocation = req.outputLocation() != null ? req.outputLocation() : defaultOutputLocation;

        String queryExecutionId = startQuery(req.sql(), database, workgroup, outputLocation);
        log.info("Async query submitted: {}", queryExecutionId);
        return new QuerySubmitted(queryExecutionId, "SUBMITTED",
                "Poll /athena/query/" + queryExecutionId + "/status for updates");
    }

    // ── Status of a query ─────────────────────────────────────────────────────

    public QueryStatus getQueryStatus(String queryExecutionId) {
        GetQueryExecutionResponse resp = athenaClient.getQueryExecution(
                GetQueryExecutionRequest.builder()
                        .queryExecutionId(queryExecutionId)
                        .build());

        var execution = resp.queryExecution();
        var status = execution.status();
        var stats = execution.statistics();

        return new QueryStatus(
                queryExecutionId,
                status.stateAsString(),
                status.stateChangeReason(),
                status.submissionDateTime(),
                status.completionDateTime(),
                stats != null ? stats.dataScannedInBytes() : 0,
                stats != null ? stats.engineExecutionTimeInMillis() : 0,
                stats != null ? stats.queryQueueTimeInMillis() : 0
        );
    }

    // ── Get results for a completed async query ───────────────────────────────

    public QueryResult getQueryResults(String queryExecutionId) {
        QueryStatus status = getQueryStatus(queryExecutionId);
        if (!"SUCCEEDED".equals(status.state())) {
            throw new AthenaException("Query is not complete. Current state: " + status.state());
        }
        List<Map<String, String>> rows = fetchAllResults(queryExecutionId);
        return new QueryResult(
                queryExecutionId, rows, rows.size(),
                status.dataBytesScanned(), status.engineExecutionTimeMs(),
                rows.size() >= maxResults
        );
    }

    // ── Cancel a running query ────────────────────────────────────────────────

    public CancelResult cancelQuery(String queryExecutionId) {
        athenaClient.stopQueryExecution(
                StopQueryExecutionRequest.builder()
                        .queryExecutionId(queryExecutionId)
                        .build());
        log.info("Cancelled query: {}", queryExecutionId);
        return new CancelResult(queryExecutionId, "Query cancellation requested.");
    }

    // ── Query history ─────────────────────────────────────────────────────────

    public List<QueryHistory> getQueryHistory(int maxItems) {
        ListQueryExecutionsResponse resp = athenaClient.listQueryExecutions(
                ListQueryExecutionsRequest.builder()
                        .maxResults(Math.min(maxItems, 50))
                        .workGroup(defaultWorkgroup)
                        .build());

        List<QueryHistory> history = new ArrayList<>();
        for (String id : resp.queryExecutionIds()) {
            try {
                GetQueryExecutionResponse execResp = athenaClient.getQueryExecution(
                        GetQueryExecutionRequest.builder().queryExecutionId(id).build());
                var ex = execResp.queryExecution();
                var stats = ex.statistics();
                history.add(new QueryHistory(
                        id,
                        ex.query(),
                        ex.status().stateAsString(),
                        ex.queryExecutionContext() != null ? ex.queryExecutionContext().database() : null,
                        ex.workGroup(),
                        ex.status().submissionDateTime(),
                        ex.status().completionDateTime(),
                        stats != null ? stats.dataScannedInBytes() : 0
                ));
            } catch (Exception e) {
                log.warn("Could not fetch execution {}: {}", id, e.getMessage());
            }
        }
        return history;
    }

    // ── Named queries ─────────────────────────────────────────────────────────

    public List<NamedQuery> listNamedQueries() {
        ListNamedQueriesResponse resp = athenaClient.listNamedQueries(
                ListNamedQueriesRequest.builder().workGroup(defaultWorkgroup).build());

        List<NamedQuery> queries = new ArrayList<>();
        for (String id : resp.namedQueryIds()) {
            try {
                GetNamedQueryResponse nqResp = athenaClient.getNamedQuery(
                        GetNamedQueryRequest.builder().namedQueryId(id).build());
                var nq = nqResp.namedQuery();
                queries.add(new NamedQuery(
                        nq.namedQueryId(), nq.name(), nq.description(),
                        nq.database(), nq.queryString()));
            } catch (Exception e) {
                log.warn("Could not fetch named query {}: {}", id, e.getMessage());
            }
        }
        return queries;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private String startQuery(String sql, String database, String workgroup, String outputLocation) {
        StartQueryExecutionRequest request = StartQueryExecutionRequest.builder()
                .queryString(sql)
                .queryExecutionContext(QueryExecutionContext.builder().database(database).build())
                .resultConfiguration(ResultConfiguration.builder().outputLocation(outputLocation).build())
                .workGroup(workgroup)
                .build();

        return athenaClient.startQueryExecution(request).queryExecutionId();
    }

    private void waitForQuery(String queryExecutionId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000;

        while (System.currentTimeMillis() < deadline) {
            GetQueryExecutionResponse resp = athenaClient.getQueryExecution(
                    GetQueryExecutionRequest.builder()
                            .queryExecutionId(queryExecutionId).build());

            var status = resp.queryExecution().status();
            String state = status.stateAsString();

            switch (state) {
                case "SUCCEEDED" -> { return; }
                case "FAILED", "CANCELLED" -> throw new AthenaException(
                        "Query " + state.toLowerCase() + ": " + status.stateChangeReason());
                default -> Thread.sleep(pollIntervalMs);
            }
        }
        throw new AthenaException("Query timed out after " + timeoutSeconds + " seconds: " + queryExecutionId);
    }

    private List<Map<String, String>> fetchAllResults(String queryExecutionId) {
        List<Map<String, String>> allRows = new ArrayList<>();
        String nextToken = null;
        List<String> columnNames = null;
        boolean firstPage = true;

        do {
            GetQueryResultsRequest.Builder reqBuilder = GetQueryResultsRequest.builder()
                    .queryExecutionId(queryExecutionId)
                    .maxResults(1000);

            if (nextToken != null) reqBuilder.nextToken(nextToken);

            GetQueryResultsResponse resp = athenaClient.getQueryResults(reqBuilder.build());
            List<Row> rows = resp.resultSet().rows();

            if (firstPage) {
                columnNames = rows.get(0).data().stream()
                        .map(Datum::varCharValue)
                        .toList();
                rows = rows.subList(1, rows.size());
                firstPage = false;
            }

            for (Row row : rows) {
                if (allRows.size() >= maxResults) break;
                Map<String, String> rowMap = new LinkedHashMap<>();
                List<Datum> data = row.data();
                for (int j = 0; j < data.size(); j++) {
                    rowMap.put(columnNames.get(j), data.get(j).varCharValue());
                }
                allRows.add(rowMap);
            }

            nextToken = resp.nextToken();

        } while (nextToken != null && allRows.size() < maxResults);

        return allRows;
    }

    private QueryExecutionStatistics getStats(String queryExecutionId) {  // ← fixed class name
        try {
            return athenaClient.getQueryExecution(
                    GetQueryExecutionRequest.builder()
                            .queryExecutionId(queryExecutionId).build())
                    .queryExecution().statistics();
        } catch (Exception e) {
            return null;
        }
    }

    private void validateSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL query cannot be empty.");
        }
        String upper = sql.trim().toUpperCase();
        for (String keyword : BLOCKED_KEYWORDS) {
            if (upper.startsWith(keyword + " ") || upper.startsWith(keyword + "\n")) {
                throw new IllegalArgumentException(
                        "Statement type '" + keyword + "' is not allowed. Only SELECT/SHOW/DESCRIBE queries are permitted.");
            }
        }
    }
}