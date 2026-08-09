package com.edumate.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 评测结果 —— 单次评测运行的完整指标汇总
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResult {
    /** 评测运行 ID */
    private String runId;
    /** 评测时间戳 */
    private String timestamp;
    /** 评测类型：retrieval / ragas / full */
    private String type;
    /** 评测样本总数 */
    private int totalSamples;
    /** ── 检索指标 ── */
    /** Recall@K（K=1,3,5,10） */
    private Map<Integer, Double> recallAtK;
    /** Mean Reciprocal Rank */
    private double mrr;
    /** Normalized Discounted Cumulative Gain（K=10） */
    private double ndcgAt10;
    /** ── RAGAS 指标 ── */
    /** 答案相关性（Answer Relevance） */
    private double answerRelevance;
    /** 忠实度（Faithfulness） */
    private double faithfulness;
    /** 上下文相关性（Context Relevance） */
    private double contextRelevance;
    /** ── 逐样本明细 ── */
    private List<SampleMetric> sampleMetrics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SampleMetric {
        private String sampleId;
        private String query;
        private double recallAt5;
        private double reciprocalRank;
        private List<String> retrievedChunkIds;
        private List<String> relevantChunkIds;
    }
}