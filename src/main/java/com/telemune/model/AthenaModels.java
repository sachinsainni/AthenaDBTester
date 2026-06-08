package com.telemune.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class AthenaModels {

    // ── Request bodies ────────────────────────────────────────────────────────

    public record QueryRequest(
            String sql,
            String database,       // optional override
            String workgroup,      // optional override
            String outputLocation  // optional override
    ) {}

    // ── Responses ─────────────────────────────────────────────────────────────

    public record ApiResponse<T>(
            boolean success,
            T data,
            String error,
            long durationMs
    ) {
        public static <T> ApiResponse<T> ok(T data, long durationMs) {
            return new ApiResponse<>(true, data, null, durationMs);
        }
        public static <T> ApiResponse<T> error(String error) {
            return new ApiResponse<>(false, null, error, 0);
        }
    }

    public record QueryResult(
            String queryExecutionId,
            List<Map<String, String>> rows,
            int totalRows,
            long dataBytesScanned,
            long queryDurationMs,
            boolean truncated
    ) {}

    public record QuerySubmitted(
            String queryExecutionId,
            String status,
            String message
    ) {}

    public record QueryStatus(
            String queryExecutionId,
            String state,
            String stateChangeReason,
            Instant submissionTime,
            Instant completionTime,
            long dataBytesScanned,
            long engineExecutionTimeMs,
            long queryQueueTimeMs
    ) {}

    public record Database(String name, String description) {}

    public record TableInfo(
            String name,
            String databaseName,
            String tableType,
            List<ColumnInfo> columns,
            List<ColumnInfo> partitionKeys,
            Map<String, String> parameters
    ) {}

    public record ColumnInfo(
            String name,
            String type,
            String comment
    ) {}

    public record Partition(Map<String, String> values, String location) {}

    public record QueryHistory(
            String queryExecutionId,
            String query,
            String state,
            String database,
            String workgroup,
            Instant submissionTime,
            Instant completionTime,
            long dataBytesScanned
    ) {}

    public record NamedQuery(
            String namedQueryId,
            String name,
            String description,
            String database,
            String query
    ) {}

    public record WorkgroupInfo(
            String name,
            String state,
            String description,
            Long bytesScannedCutoffPerQuery
    ) {}

    public record CancelResult(String queryExecutionId, String message) {}
}
