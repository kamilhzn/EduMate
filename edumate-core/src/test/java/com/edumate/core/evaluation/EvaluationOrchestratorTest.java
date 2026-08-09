package com.edumate.core.evaluation;

import com.edumate.common.model.DocumentChunk;
import com.edumate.common.model.EvaluationResult;
import com.edumate.common.model.EvaluationSample;
import com.edumate.core.agent.QueryRewriteService;
import com.edumate.core.retrieval.HybridSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationOrchestratorTest {

    @Mock
    private HybridSearchService hybridSearchService;

    @Mock
    private QueryRewriteService queryRewriteService;

    @Mock
    private EvaluationMetricsService metricsService;

    @Mock
    private TraceService traceService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRunRetrievalEvaluation() {
        when(queryRewriteService.rewrite(anyString())).thenAnswer(inv -> inv.getArgument(0));

        DocumentChunk chunk = DocumentChunk.builder()
                .id("chunk-1").content("二叉树内容").courseName("数据结构").build();
        when(hybridSearchService.search(anyString(), anyInt())).thenReturn(List.of(chunk));

        EvaluationOrchestrator orchestrator = new EvaluationOrchestrator(
                hybridSearchService, queryRewriteService, null,
                metricsService, traceService, objectMapper);

        EvaluationSample sample = EvaluationSample.builder()
                .id("DS-001").query("什么是二叉树")
                .relevantChunkIds(List.of("chunk-1"))
                .courseName("数据结构").build();

        EvaluationResult result = orchestrator.runRetrievalEvaluation(List.of(sample));

        assertNotNull(result);
        assertEquals("retrieval", result.getType());
        assertEquals(1, result.getTotalSamples());
    }

    @Test
    void shouldReturnEmptyResultForEmptySamples() {
        EvaluationOrchestrator orchestrator = new EvaluationOrchestrator(
                hybridSearchService, queryRewriteService, null,
                metricsService, traceService, objectMapper);

        EvaluationResult result = orchestrator.runRetrievalEvaluation(List.of());
        assertEquals(0, result.getTotalSamples());
    }
}