# AthenaApi

A Spring Boot REST API for querying and exploring **AWS Athena** — built for Telemune. Includes a full-featured browser-based SQL console served directly from the application.

---

## Table of Contents

- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [Running the Application](#running-the-application)
- [Frontend UI](#frontend-ui)
- [API Reference](#api-reference)
  - [Query — Synchronous](#1-query--synchronous)
  - [Query — Async](#2-query--async)
  - [Query Status](#3-query-status)
  - [Fetch Async Results](#4-fetch-async-results)
  - [Cancel Query](#5-cancel-query)
  - [Query History](#6-query-history)
  - [Named Queries](#7-named-queries)
  - [List Databases](#8-list-databases)
  - [List Tables](#9-list-tables)
  - [Describe Table](#10-describe-table)
  - [List Partitions](#11-list-partitions)
  - [List Workgroups](#12-list-workgroups)
- [Response Format](#response-format)
- [SQL Validation](#sql-validation)
- [IAM Permissions](#iam-permissions)
- [Running Tests](#running-tests)
- [Troubleshooting](#troubleshooting)

---

## Overview

AthenaApi is a production-ready REST API that wraps AWS Athena with the following capabilities:

- **Synchronous and asynchronous query execution** with timeout and pagination
- **Schema exploration** via the AWS Glue Data Catalog (databases, tables, columns, partitions)
- **Query history and named query management**
- **Workgroup management**
- **Built-in SQL validation** — blocks destructive statements (DROP, DELETE, etc.)
- **Secure credential handling** — env vars and IAM role support, never hardcoded
- **Browser SQL console** — served at `http://localhost:8080`

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.5.10 |
| AWS SDK | AWS SDK for Java v2 (2.25.55) |
| AWS Services | Amazon Athena, AWS Glue, AWS STS |
| Build Tool | Maven |
| Frontend | Vanilla HTML/CSS/JS (served as static resource) |

---

## Project Structure

```
AthenaApiRebuild/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/telemune/
    │   │   ├── AthenaApiApplication.java          # Spring Boot entry point
    │   │   ├── config/
    │   │   │   ├── AthenaConfig.java              # AWS client beans (AthenaClient, GlueClient)
    │   │   │   └── WebConfig.java                 # CORS + static resource configuration
    │   │   ├── controller/
    │   │   │   └── AthenaController.java          # All 12 REST endpoints
    │   │   ├── exception/
    │   │   │   ├── AthenaException.java           # Custom exceptions
    │   │   │   └── GlobalExceptionHandler.java    # Centralized error handling
    │   │   ├── model/
    │   │   │   └── AthenaModels.java              # All request/response record types
    │   │   └── service/
    │   │       ├── AthenaQueryService.java        # Query execution logic
    │   │       └── AthenaMetadataService.java     # Schema/catalog exploration
    │   └── resources/
    │       ├── application.properties             # App configuration
    │       └── static/
    │           └── index.html                     # Browser SQL console
    └── test/
        └── java/com/telemune/
            └── AthenaQueryServiceTest.java        # Unit tests
```

---

## Prerequisites

- Java 17+
- Maven 3.8+
- An AWS account with Athena enabled
- An S3 bucket for Athena query result output
- AWS credentials with the required IAM permissions (see [IAM Permissions](#iam-permissions))

---

## Configuration

All configuration is externalized via environment variables. Never hardcode credentials in source code.

### Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `AWS_ACCESS_KEY_ID` | No* | — | AWS access key |
| `AWS_SECRET_ACCESS_KEY` | No* | — | AWS secret key |
| `AWS_REGION` | No | `ap-south-1` | AWS region where Athena runs |
| `ATHENA_DATABASE` | No | `telemune_db` | Default Glue database to query |
| `ATHENA_OUTPUT_LOCATION` | No | `s3://telemune-athena-db-a/` | S3 path for query results |
| `ATHENA_WORKGROUP` | No | `primary` | Athena workgroup |
| `ATHENA_QUERY_TIMEOUT` | No | `300` | Query timeout in seconds |
| `ATHENA_MAX_RESULTS` | No | `1000` | Max rows returned per query |

*If running on EC2/ECS with an IAM role attached, `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` can be omitted — the SDK automatically picks up the instance profile via the AWS Default Credentials chain.

### Setting Environment Variables

**Mac/Linux:**
```bash
export AWS_ACCESS_KEY_ID=your_access_key
export AWS_SECRET_ACCESS_KEY=your_secret_key
export AWS_REGION=ap-south-1
export ATHENA_DATABASE=your_database
export ATHENA_OUTPUT_LOCATION=s3://your-bucket/athena-results/
export ATHENA_WORKGROUP=primary
```

**Windows (Command Prompt):**
```cmd
set AWS_ACCESS_KEY_ID=your_access_key
set AWS_SECRET_ACCESS_KEY=your_secret_key
set AWS_REGION=ap-south-1
```

**application.properties** (for non-sensitive defaults only):
```properties
server.port=8080
aws.region=${AWS_REGION:ap-south-1}
athena.database=${ATHENA_DATABASE:telemune_db}
athena.output-location=${ATHENA_OUTPUT_LOCATION:s3://your-bucket/}
athena.workgroup=${ATHENA_WORKGROUP:primary}
athena.query.timeout-seconds=${ATHENA_QUERY_TIMEOUT:300}
athena.query.max-results=${ATHENA_MAX_RESULTS:1000}
```

---

## Running the Application

```bash
# Clone / navigate to project
cd AthenaApiRebuild

# Set environment variables (see above)
export AWS_ACCESS_KEY_ID=...
export AWS_SECRET_ACCESS_KEY=...
export ATHENA_DATABASE=your_db
export ATHENA_OUTPUT_LOCATION=s3://your-bucket/

# Build and run
mvn spring-boot:run

# Or build a JAR and run
mvn clean package -DskipTests
java -jar target/AthenaApi-1.0.0.jar
```

The application starts on `http://localhost:8080`.

---

## Frontend UI

A browser-based SQL console is served directly at:

```
http://localhost:8080
```

### Features

| Feature | Description |
|---|---|
| SQL Editor | Syntax-aware textarea with Tab indentation and Cmd/Ctrl+Enter to run |
| Schema Sidebar | Auto-loads all databases and tables from Glue catalog on startup |
| Table Inspector | Click any table to see columns, types, partition keys; generates a SELECT query |
| Query History | Live feed of recent executions with state, duration, bytes scanned |
| Saved Queries | Lists Athena named queries stored in your workgroup |
| Results Table | Rendered with up to 2,000 rows, sortable columns |
| JSON View | Raw API response for debugging |
| Schema View | Column type inspector with type pills |
| Async Mode | Submit long-running queries without blocking; poll for completion |
| CSV Export | Download results as a `.csv` file |
| Status Bar | Shows live execution ID, scan size, and duration |

---

## API Reference

All responses follow a standard envelope format — see [Response Format](#response-format).

Base URL: `http://localhost:8080`

---

### 1. Query — Synchronous

Executes a SQL query and waits for it to complete before returning results. Suitable for queries that finish in under 5 minutes (configurable timeout).

```
POST /athena/query
Content-Type: application/json
```

**Request Body:**
```json
{
  "sql": "SELECT * FROM cdr_logs LIMIT 100",
  "database": "telemune_db",
  "workgroup": null,
  "outputLocation": null
}
```

| Field | Required | Description |
|---|---|---|
| `sql` | Yes | SQL query to execute |
| `database` | No | Overrides the default database from config |
| `workgroup` | No | Overrides the default workgroup |
| `outputLocation` | No | Overrides the S3 output location |

**Response:**
```json
{
  "success": true,
  "data": {
    "queryExecutionId": "a1b2c3d4-...",
    "rows": [
      { "column1": "value1", "column2": "value2" }
    ],
    "totalRows": 100,
    "dataBytesScanned": 204800,
    "queryDurationMs": 3450,
    "truncated": false
  },
  "error": null,
  "durationMs": 3512
}
```

`truncated: true` means the result hit the `ATHENA_MAX_RESULTS` limit and there are more rows available.

**curl example:**
```bash
curl -X POST http://localhost:8080/athena/query \
  -H "Content-Type: application/json" \
  -d '{"sql": "SELECT * FROM cdr_logs LIMIT 10", "database": null, "workgroup": null, "outputLocation": null}'
```

---

### 2. Query — Async

Submits a query and returns immediately with an execution ID. Use this for long-running queries. Poll `/status` to check progress, then fetch `/results` when done.

```
POST /athena/query/async
Content-Type: application/json
```

**Request Body:** Same as synchronous query.

**Response:**
```json
{
  "success": true,
  "data": {
    "queryExecutionId": "a1b2c3d4-...",
    "status": "SUBMITTED",
    "message": "Poll /athena/query/a1b2c3d4-.../status for updates"
  },
  "error": null,
  "durationMs": 120
}
```

**curl example:**
```bash
curl -X POST http://localhost:8080/athena/query/async \
  -H "Content-Type: application/json" \
  -d '{"sql": "SELECT COUNT(*) FROM large_table", "database": null, "workgroup": null, "outputLocation": null}'
```

---

### 3. Query Status

Polls the current state of a running or completed query.

```
GET /athena/query/{queryExecutionId}/status
```

**Response:**
```json
{
  "success": true,
  "data": {
    "queryExecutionId": "a1b2c3d4-...",
    "state": "SUCCEEDED",
    "stateChangeReason": null,
    "submissionTime": "2026-06-07T13:30:00Z",
    "completionTime": "2026-06-07T13:30:05Z",
    "dataBytesScanned": 204800,
    "engineExecutionTimeMs": 4200,
    "queryQueueTimeMs": 150
  },
  "error": null,
  "durationMs": 0
}
```

Possible `state` values: `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`

**curl example:**
```bash
curl http://localhost:8080/athena/query/a1b2c3d4-.../status
```

---

### 4. Fetch Async Results

Retrieves the results of a completed async query. Will return an error if the query has not yet succeeded.

```
GET /athena/query/{queryExecutionId}/results
```

**Response:** Same structure as the synchronous query response.

**curl example:**
```bash
curl http://localhost:8080/athena/query/a1b2c3d4-.../results
```

---

### 5. Cancel Query

Cancels a running query.

```
DELETE /athena/query/{queryExecutionId}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "queryExecutionId": "a1b2c3d4-...",
    "message": "Query cancellation requested."
  },
  "error": null,
  "durationMs": 0
}
```

**curl example:**
```bash
curl -X DELETE http://localhost:8080/athena/query/a1b2c3d4-...
```

---

### 6. Query History

Lists recent query executions from the workgroup.

```
GET /athena/query/history?maxItems=20
```

| Parameter | Required | Default | Description |
|---|---|---|---|
| `maxItems` | No | `20` | Number of results to return (max 50) |

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "queryExecutionId": "a1b2c3d4-...",
      "query": "SELECT * FROM cdr_logs LIMIT 10",
      "state": "SUCCEEDED",
      "database": "telemune_db",
      "workgroup": "primary",
      "submissionTime": "2026-06-07T13:30:00Z",
      "completionTime": "2026-06-07T13:30:05Z",
      "dataBytesScanned": 204800
    }
  ],
  "error": null,
  "durationMs": 0
}
```

**curl example:**
```bash
curl "http://localhost:8080/athena/query/history?maxItems=10"
```

---

### 7. Named Queries

Lists saved (named) queries stored in the Athena workgroup.

```
GET /athena/query/named
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "namedQueryId": "x1y2z3-...",
      "name": "Daily CDR Summary",
      "description": "Aggregates CDR logs by day",
      "database": "telemune_db",
      "query": "SELECT date, COUNT(*) FROM cdr_logs GROUP BY date"
    }
  ],
  "error": null,
  "durationMs": 0
}
```

**curl example:**
```bash
curl http://localhost:8080/athena/query/named
```

---

### 8. List Databases

Lists all databases in the AWS Glue Data Catalog. This is instant and does not trigger a query execution — no Athena cost.

```
GET /athena/databases
```

**Response:**
```json
{
  "success": true,
  "data": [
    { "name": "telemune_db", "description": "Main Telemune database" },
    { "name": "telemune_archive", "description": "Archive data" }
  ],
  "error": null,
  "durationMs": 0
}
```

**curl example:**
```bash
curl http://localhost:8080/athena/databases
```

---

### 9. List Tables

Lists all tables in a specific database. No Athena cost — reads directly from Glue.

```
GET /athena/databases/{database}/tables
```

**Response:**
```json
{
  "success": true,
  "data": ["cdr_logs", "subscribers", "billing_records"],
  "error": null,
  "durationMs": 0
}
```

**curl example:**
```bash
curl http://localhost:8080/athena/databases/telemune_db/tables
```

---

### 10. Describe Table

Returns full schema for a table — columns, types, partition keys, table type, and Glue parameters.

```
GET /athena/databases/{database}/tables/{tableName}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "name": "cdr_logs",
    "databaseName": "telemune_db",
    "tableType": "EXTERNAL_TABLE",
    "columns": [
      { "name": "call_id",    "type": "string",  "comment": "Unique call identifier" },
      { "name": "duration",   "type": "bigint",  "comment": "Call duration in seconds" },
      { "name": "caller_msisdn", "type": "string", "comment": "" }
    ],
    "partitionKeys": [
      { "name": "dt", "type": "string", "comment": "Partition date YYYY-MM-DD" }
    ],
    "parameters": {
      "classification": "parquet",
      "compressionType": "snappy"
    }
  },
  "error": null,
  "durationMs": 0
}
```

**curl example:**
```bash
curl http://localhost:8080/athena/databases/telemune_db/tables/cdr_logs
```

---

### 11. List Partitions

Lists all partitions for a partitioned table with their S3 locations.

```
GET /athena/databases/{database}/tables/{tableName}/partitions
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "values": { "dt": "2026-06-01" },
      "location": "s3://telemune-data/cdr_logs/dt=2026-06-01/"
    },
    {
      "values": { "dt": "2026-06-02" },
      "location": "s3://telemune-data/cdr_logs/dt=2026-06-02/"
    }
  ],
  "error": null,
  "durationMs": 0
}
```

**curl example:**
```bash
curl http://localhost:8080/athena/databases/telemune_db/tables/cdr_logs/partitions
```

---

### 12. List Workgroups

Lists all Athena workgroups with their state and byte scan limits.

```
GET /athena/workgroups
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "name": "primary",
      "state": "ENABLED",
      "description": "Primary workgroup",
      "bytesScannedCutoffPerQuery": null
    },
    {
      "name": "dev-workgroup",
      "state": "ENABLED",
      "description": "Development",
      "bytesScannedCutoffPerQuery": 1073741824
    }
  ],
  "error": null,
  "durationMs": 0
}
```

`bytesScannedCutoffPerQuery: null` means unlimited. A value of `1073741824` = 1 GB.

**curl example:**
```bash
curl http://localhost:8080/athena/workgroups
```

---

## Response Format

Every API response follows this envelope structure:

```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "durationMs": 1234
}
```

| Field | Type | Description |
|---|---|---|
| `success` | boolean | `true` if the request succeeded, `false` on error |
| `data` | object/array | Response payload (null on error) |
| `error` | string | Error message (null on success) |
| `durationMs` | long | Total time taken by the API in milliseconds |

**Error response example:**
```json
{
  "success": false,
  "data": null,
  "error": "Statement type 'DROP' is not allowed.",
  "durationMs": 0
}
```

**HTTP status codes used:**

| Code | When |
|---|---|
| `200` | Success |
| `400` | Bad request (invalid SQL, missing params) |
| `502` | Athena returned an error |
| `500` | Unexpected server error |

---

## SQL Validation

The API blocks the following statement types at the application layer before any request reaches Athena. This enforces read-only access.

**Blocked keywords:** `DROP`, `DELETE`, `TRUNCATE`, `INSERT`, `UPDATE`, `ALTER`, `CREATE`, `REPLACE`, `MERGE`, `GRANT`, `REVOKE`

Attempting to run any of these returns:
```json
{
  "success": false,
  "data": null,
  "error": "Statement type 'DROP' is not allowed. Only SELECT/SHOW/DESCRIBE queries are permitted.",
  "durationMs": 0
}
```

---

## IAM Permissions

The AWS credentials used must have the following IAM permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "athena:StartQueryExecution",
        "athena:StopQueryExecution",
        "athena:GetQueryExecution",
        "athena:GetQueryResults",
        "athena:ListQueryExecutions",
        "athena:ListNamedQueries",
        "athena:GetNamedQuery",
        "athena:ListWorkGroups",
        "athena:GetWorkGroup"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "glue:GetDatabases",
        "glue:GetDatabase",
        "glue:GetTables",
        "glue:GetTable",
        "glue:GetPartitions"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:ListBucket",
        "s3:GetBucketLocation"
      ],
      "Resource": [
        "arn:aws:s3:::your-athena-output-bucket",
        "arn:aws:s3:::your-athena-output-bucket/*",
        "arn:aws:s3:::your-data-bucket/*"
      ]
    }
  ]
}
```

---

## Running Tests

```bash
mvn test
```

Unit tests are in `AthenaQueryServiceTest.java` and use Mockito to mock the `AthenaClient` — no real AWS calls are made during testing.

**Test coverage:**
- Valid SQL passes validation
- Blocked SQL (DROP, DELETE, etc.) throws `IllegalArgumentException`
- Empty and null SQL throw `IllegalArgumentException`
- Async submit returns correct execution ID
- Cancel query calls `stopQueryExecution` and returns confirmation

---

## Troubleshooting

**App starts but `/athena/databases` returns empty `[]`**
- Your AWS credentials may not have Glue permissions — run `aws glue get-databases --region ap-south-1` to verify
- The region may be wrong — check that `AWS_REGION` matches where your Glue catalog is

**`NoCredentialsException` on startup**
- Set `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`, or attach an IAM role if running on EC2/ECS

**Query times out**
- Increase `ATHENA_QUERY_TIMEOUT` (default 300s)
- Check Athena query history in the AWS console for the actual error

**`FAILED` query state with no clear reason**
- Check `stateChangeReason` in the status response — it contains the Athena error message
- Verify the S3 output location exists and the IAM user has write access to it

**Results are truncated**
- Increase `ATHENA_MAX_RESULTS` (default 1000), or use `LIMIT` in your SQL query

**`favicon.ico` errors in logs**
- Already suppressed — add `logging.level.org.springframework.web.servlet.resource=OFF` to `application.properties` if still appearing% 
