package com.telemune.exception;

public class AthenaException extends RuntimeException {
    public AthenaException(String message) { super(message); }
    public AthenaException(String message, Throwable cause) { super(message, cause); }
}

class QueryTimeoutException extends AthenaException {
    public QueryTimeoutException(String queryId, long timeoutSeconds) {
        super("Query " + queryId + " timed out after " + timeoutSeconds + "s");
    }
}

class InvalidSqlException extends AthenaException {
    public InvalidSqlException(String reason) {
        super("Invalid SQL: " + reason);
    }
}
