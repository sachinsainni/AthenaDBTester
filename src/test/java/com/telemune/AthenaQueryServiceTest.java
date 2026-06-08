package com.telemune;

import com.telemune.model.AthenaModels.*;
import com.telemune.service.AthenaQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AthenaQueryServiceTest {

    @Mock
    private AthenaClient athenaClient;

    @InjectMocks
    private AthenaQueryService queryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(queryService, "defaultDatabase", "test_db");
        ReflectionTestUtils.setField(queryService, "defaultOutputLocation", "s3://bucket/");
        ReflectionTestUtils.setField(queryService, "defaultWorkgroup", "primary");
        ReflectionTestUtils.setField(queryService, "timeoutSeconds", 300L);
        ReflectionTestUtils.setField(queryService, "maxResults", 1000);
        ReflectionTestUtils.setField(queryService, "pollIntervalMs", 100L);
    }

    @Test
    void testValidSqlPassesValidation() {
        assertDoesNotThrow(() -> {
            // Uses reflection since validateSql is private
            var method = AthenaQueryService.class.getDeclaredMethod("validateSql", String.class);
            method.setAccessible(true);
            method.invoke(queryService, "SELECT * FROM orders");
        });
    }

    @Test
    void testBlockedSqlThrowsException() {
        assertThrows(Exception.class, () -> {
            var method = AthenaQueryService.class.getDeclaredMethod("validateSql", String.class);
            method.setAccessible(true);
            method.invoke(queryService, "DROP TABLE orders");
        });
    }

    @Test
    void testSubmitQueryReturnsExecutionId() {
        when(athenaClient.startQueryExecution(any(StartQueryExecutionRequest.class)))
                .thenReturn(StartQueryExecutionResponse.builder()
                        .queryExecutionId("test-execution-id")
                        .build());

        QuerySubmitted result = queryService.submitQuery(
                new QueryRequest("SELECT 1", null, null, null));

        assertEquals("test-execution-id", result.queryExecutionId());
        assertEquals("SUBMITTED", result.status());
    }

    @Test
    void testCancelQuery() {
        when(athenaClient.stopQueryExecution(any(StopQueryExecutionRequest.class)))
                .thenReturn(StopQueryExecutionResponse.builder().build());

        CancelResult result = queryService.cancelQuery("some-id");
        assertEquals("some-id", result.queryExecutionId());
        assertTrue(result.message().contains("cancellation"));
    }

    @Test
    void testEmptySqlThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                queryService.submitQuery(new QueryRequest("", null, null, null)));
    }

    @Test
    void testNullSqlThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                queryService.submitQuery(new QueryRequest(null, null, null, null)));
    }
}
