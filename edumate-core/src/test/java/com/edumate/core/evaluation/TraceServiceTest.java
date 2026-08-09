package com.edumate.core.evaluation;

import com.edumate.common.model.TraceRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TraceServiceTest {

    private TraceService service;

    @BeforeEach
    void setUp() {
        service = new TraceService();
    }

    @Test
    void shouldStartAndGetTrace() {
        TraceRecord trace = service.startTrace("test-query-1", "session-1");
        assertNotNull(trace);
        assertNotNull(trace.getTraceId());
        assertEquals("test-query-1", trace.getQuery());
        assertEquals("session-1", trace.getSessionId());
    }

    @Test
    void shouldRecordStageAndGetTrace() {
        TraceRecord trace = service.startTrace("query", "session");
        String traceId = trace.getTraceId();

        service.recordStage(traceId, "vectorRetrieval", 150L,
                "query: 二叉树", "found 10 results", true, null);

        TraceRecord retrieved = service.getTrace(traceId);
        assertNotNull(retrieved);
        assertNotNull(retrieved.getVectorRetrieval());
        assertEquals("vectorRetrieval", retrieved.getVectorRetrieval().getStage());
        assertEquals(150L, retrieved.getVectorRetrieval().getLatencyMs());
        assertTrue(retrieved.getVectorRetrieval().isSuccess());
    }

    @Test
    void shouldReturnNullForUnknownTraceId() {
        assertNull(service.getTrace("nonexistent"));
    }

    @Test
    void shouldCompleteTrace() {
        TraceRecord trace = service.startTrace("query", "session");
        String traceId = trace.getTraceId();

        service.recordStage(traceId, "vectorRetrieval", 100L, "in", "out", true, null);
        service.recordStage(traceId, "keywordRetrieval", 80L, "in", "out", true, null);
        service.recordStage(traceId, "rrfFusion", 20L, "in", "out", true, null);

        TraceRecord completed = service.completeTrace(traceId);
        assertNotNull(completed);
        assertTrue(completed.getTotalLatencyMs() > 0);
    }

    @Test
    void shouldTrackRecentTraces() {
        for (int i = 0; i < 5; i++) {
            service.startTrace("query-" + i, "session-" + i);
        }
        var recent = service.getRecentTraces(3);
        assertEquals(3, recent.size());
    }
}