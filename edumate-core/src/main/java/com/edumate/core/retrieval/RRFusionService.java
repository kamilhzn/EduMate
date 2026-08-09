package com.edumate.core.retrieval;

import com.edumate.common.model.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RRF（Reciprocal Rank Fusion）倒数排序融合 —— 合并多个检索结果集
 * <p>
 * 公式：score(d) = Σ 1 / (k + rank_i(d))，其中 k=60（经典值）
 * 同一文档在多个结果集中排名越靠前，融合后得分越高，且自动去重。
 */
@Service
public class RRFusionService {

    private static final double K = 60.0;

    /**
     * 对向量检索和关键词检索的结果进行 RRF 融合
     *
     * @param vectorResults  向量检索结果（已按相似度排序）
     * @param keywordResults 关键词检索结果（已按 BM25 排序）
     * @param topK           最终返回的最大结果数
     * @return 融合后按得分降序排列的结果列表
     */
    public List<DocumentChunk> fuse(List<DocumentChunk> vectorResults,
                                    List<DocumentChunk> keywordResults,
                                    int topK) {
        Map<String, DocumentChunk> chunkMap = new LinkedHashMap<>();
        Map<String, Double> scores = new HashMap<>();

        accumulateScores(vectorResults, chunkMap, scores);
        accumulateScores(keywordResults, chunkMap, scores);

        return chunkMap.values().stream()
                .sorted((a, b) -> Double.compare(
                        scores.getOrDefault(b.getId(), 0.0),
                        scores.getOrDefault(a.getId(), 0.0)))
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * 对向量检索、关键词检索和图谱检索的结果进行三路 RRF 融合
     *
     * @param vectorResults  向量检索结果
     * @param keywordResults 关键词检索结果
     * @param graphResults   图谱检索结果
     * @param topK           最终返回的最大结果数
     * @return 融合后按得分降序排列的结果列表
     */
    public List<DocumentChunk> fuse(List<DocumentChunk> vectorResults,
                                    List<DocumentChunk> keywordResults,
                                    List<DocumentChunk> graphResults,
                                    int topK) {
        Map<String, DocumentChunk> chunkMap = new LinkedHashMap<>();
        Map<String, Double> scores = new HashMap<>();

        accumulateScores(vectorResults, chunkMap, scores);
        accumulateScores(keywordResults, chunkMap, scores);
        accumulateScores(graphResults, chunkMap, scores);

        return chunkMap.values().stream()
                .sorted((a, b) -> Double.compare(
                        scores.getOrDefault(b.getId(), 0.0),
                        scores.getOrDefault(a.getId(), 0.0)))
                .limit(topK)
                .collect(Collectors.toList());
    }

    private void accumulateScores(List<DocumentChunk> results,
                                  Map<String, DocumentChunk> chunkMap,
                                  Map<String, Double> scores) {
        for (int i = 0; i < results.size(); i++) {
            DocumentChunk chunk = results.get(i);
            String id = chunk.getId();
            chunkMap.putIfAbsent(id, chunk);
            double score = 1.0 / (K + i + 1);
            scores.merge(id, score, Double::sum);
        }
    }
}