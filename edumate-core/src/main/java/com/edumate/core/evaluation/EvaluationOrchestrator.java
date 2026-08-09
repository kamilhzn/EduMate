package com.edumate.core.evaluation;

import com.edumate.common.model.DocumentChunk;
import com.edumate.common.model.EvaluationResult;
import com.edumate.common.model.EvaluationSample;
import com.edumate.core.agent.QueryRewriteService;
import com.edumate.core.retrieval.HybridSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 评测编排器 —— 加载数据集 → 逐条检索 → 计算指标 → 输出报告
 * <p>
 * 支持两种评测模式：
 * 1. 检索评测（retrieval）：只评测检索质量（Recall@K、MRR、NDCG）
 * 2. 全量评测（full）：检索 + RAGAS 端到端评测（需 ChatModel 可用）
 * <p>
 * 当 HybridSearchService 不可用时（如 Docker 未启动），返回空结果。
 */
@Service
public class EvaluationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(EvaluationOrchestrator.class);

    private final HybridSearchService hybridSearchService;
    private final QueryRewriteService queryRewriteService;
    private final RagasEvaluationService ragasEvaluationService;
    private final EvaluationMetricsService metricsService;
    private final TraceService traceService;
    private final ObjectMapper objectMapper;

    public EvaluationOrchestrator(HybridSearchService hybridSearchService,
                                  QueryRewriteService queryRewriteService,
                                  @Nullable RagasEvaluationService ragasEvaluationService,
                                  EvaluationMetricsService metricsService,
                                  TraceService traceService,
                                  ObjectMapper objectMapper) {
        this.hybridSearchService = hybridSearchService;
        this.queryRewriteService = queryRewriteService;
        this.ragasEvaluationService = ragasEvaluationService;
        this.metricsService = metricsService;
        this.traceService = traceService;
        this.objectMapper = objectMapper;
    }

    /**
     * 运行检索评测
     * <p>
     * 流程：对每条样本 → Query 改写 → 混合检索 → 计算 Recall@K + MRR
     *
     * @param samples 评测样本列表
     * @return 评测结果
     */
    public EvaluationResult runRetrievalEvaluation(List<EvaluationSample> samples) {
        String runId = UUID.randomUUID().toString().substring(0, 8);
        log.info("开始检索评测 runId={}, samples={}", runId, samples.size());

        if (samples == null || samples.isEmpty()) {
            return EvaluationResult.builder()
                    .runId(runId).type("retrieval").totalSamples(0).build();
        }

        List<Double> reciprocalRanks = new ArrayList<>();
        Map<Integer, List<Double>> recallByK = new LinkedHashMap<>();
        for (int k : List.of(1, 3, 5, 10)) recallByK.put(k, new ArrayList<>());
        List<EvaluationResult.SampleMetric> sampleMetrics = new ArrayList<>();

        for (EvaluationSample sample : samples) {
            try {
                // Query 改写
                String rewritten = queryRewriteService.rewrite(sample.getQuery());

                // 混合检索
                List<DocumentChunk> chunks = hybridSearchService.search(rewritten, 10);
                List<String> retrievedIds = chunks.stream()
                        .map(DocumentChunk::getId).collect(Collectors.toList());

                List<String> relevantIds = sample.getRelevantChunkIds() != null
                        ? sample.getRelevantChunkIds() : List.of();

                // 计算指标
                double rr = metricsService.calculateReciprocalRank(relevantIds, retrievedIds);
                reciprocalRanks.add(rr);
                for (int k : List.of(1, 3, 5, 10)) {
                    recallByK.get(k).add(
                            metricsService.calculateRecallAtK(relevantIds, retrievedIds, k));
                }

                sampleMetrics.add(EvaluationResult.SampleMetric.builder()
                        .sampleId(sample.getId())
                        .query(sample.getQuery())
                        .recallAt5(metricsService.calculateRecallAtK(relevantIds, retrievedIds, 5))
                        .reciprocalRank(rr)
                        .retrievedChunkIds(retrievedIds)
                        .relevantChunkIds(relevantIds)
                        .build());

            } catch (Exception e) {
                log.warn("样本评测失败 sampleId={}: {}", sample.getId(), e.getMessage());
                reciprocalRanks.add(0.0);
                for (int k : List.of(1, 3, 5, 10)) recallByK.get(k).add(0.0);
            }
        }

        // 汇总指标
        Map<Integer, Double> avgRecall = new LinkedHashMap<>();
        for (var entry : recallByK.entrySet()) {
            avgRecall.put(entry.getKey(),
                    entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
        }

        double mrr = metricsService.calculateMRR(reciprocalRanks);

        log.info("检索评测完成 runId={}: Recall@5={}, MRR={}",
                runId, String.format("%.3f", avgRecall.get(5)),
                String.format("%.3f", mrr));

        return EvaluationResult.builder()
                .runId(runId)
                .timestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .type("retrieval")
                .totalSamples(samples.size())
                .recallAtK(avgRecall)
                .mrr(mrr)
                .sampleMetrics(sampleMetrics)
                .build();
    }
}