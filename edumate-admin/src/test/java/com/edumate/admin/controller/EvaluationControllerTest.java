package com.edumate.admin.controller;

import com.edumate.common.model.EvaluationResult;
import com.edumate.core.evaluation.EvaluationOrchestrator;
import com.edumate.core.evaluation.TraceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EvaluationController.class)
class EvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvaluationOrchestrator evaluationOrchestrator;

    @MockitoBean
    private TraceService traceService;

    @Test
    void shouldReturn404ForUnknownTraceId() throws Exception {
        when(traceService.getTrace(any())).thenReturn(null);

        mockMvc.perform(get("/api/eval/trace/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn200ForRetrievalEval() throws Exception {
        EvaluationResult result = EvaluationResult.builder()
                .runId("test-001").type("retrieval").totalSamples(0).mrr(0.0).build();
        when(evaluationOrchestrator.runRetrievalEvaluation(any())).thenReturn(result);

        mockMvc.perform(post("/api/eval/retrieval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("test-001"));
    }
}