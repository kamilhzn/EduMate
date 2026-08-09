package com.edumate.core.evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EvaluationMetricsServiceTest {

    private final EvaluationMetricsService service = new EvaluationMetricsService();

    @Test
    void shouldCalculatePerfectRecallAt5() {
        double recall = service.calculateRecallAtK(
                List.of("A", "B", "C"),
                List.of("A", "B", "C", "D", "E"),
                5);
        assertEquals(1.0, recall, 0.001);
    }

    @Test
    void shouldCalculatePartialRecallAt3() {
        double recall = service.calculateRecallAtK(
                List.of("A", "B", "C", "D"),
                List.of("A", "B", "X", "Y", "Z"),
                3);
        assertEquals(0.5, recall, 0.001);
    }

    @Test
    void shouldCalculateZeroRecallWhenNoRelevant() {
        double recall = service.calculateRecallAtK(
                List.of("A", "B", "C"),
                List.of("X", "Y", "Z"),
                3);
        assertEquals(0.0, recall, 0.001);
    }

    @Test
    void shouldCalculateMRR() {
        double mrr = service.calculateMRR(List.of(0.5, 1.0, 0.0));
        assertEquals((0.5 + 1.0 + 0.0) / 3, mrr, 0.001);
    }

    @Test
    void shouldCalculateNDCG() {
        List<Integer> relevance = List.of(3, 2, 0, 1, 0);
        double ndcg = service.calculateNDCG(relevance, 5);
        assertTrue(ndcg >= 0.0 && ndcg <= 1.0);
    }

    @Test
    void shouldCalculatePerfectNDCG() {
        List<Integer> relevance = List.of(3, 2, 1, 0, 0);
        double ndcg = service.calculateNDCG(relevance, 5);
        assertEquals(1.0, ndcg, 0.001);
    }
}