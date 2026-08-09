package com.edumate.core.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 评测指标服务 —— 计算检索质量指标（Recall@K、MRR、NDCG）
 * <p>
 * 所有方法均为纯函数，不依赖外部服务，可独立单元测试。
 */
@Service
public class EvaluationMetricsService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationMetricsService.class);

    /**
     * 计算 Recall@K —— 前 K 个检索结果中相关文档的占比
     *
     * @param relevantIds  相关文档 ID 集合（Ground Truth）
     * @param retrievedIds 检索返回的文档 ID 列表（按排名排序）
     * @param k            截断值
     * @return Recall@K，范围 [0, 1]
     */
    public double calculateRecallAtK(List<String> relevantIds, List<String> retrievedIds, int k) {
        if (relevantIds == null || relevantIds.isEmpty()) return 0.0;
        if (retrievedIds == null || retrievedIds.isEmpty()) return 0.0;

        Set<String> relevantSet = new HashSet<>(relevantIds);
        long retrievedRelevant = retrievedIds.stream()
                .limit(k)
                .filter(relevantSet::contains)
                .count();
        return (double) retrievedRelevant / relevantSet.size();
    }

    /**
     * 计算 Mean Reciprocal Rank（MRR）
     * <p>
     * MRR = (1/N) * Σ(1/rank_i)，其中 rank_i 是第一个相关文档的排名位置
     * 如果没有任何相关文档，该样本的 RR = 0
     *
     * @param perSampleRR 每个样本的 Reciprocal Rank 列表
     * @return MRR 值
     */
    public double calculateMRR(List<Double> perSampleRR) {
        if (perSampleRR == null || perSampleRR.isEmpty()) return 0.0;
        return perSampleRR.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /**
     * 计算单个样本的 Reciprocal Rank
     *
     * @param relevantIds  相关文档 ID 集合
     * @param retrievedIds 检索返回的文档 ID 列表
     * @return 第一个相关文档的倒数排名，无相关则返回 0
     */
    public double calculateReciprocalRank(List<String> relevantIds, List<String> retrievedIds) {
        if (relevantIds == null || relevantIds.isEmpty()) return 0.0;
        if (retrievedIds == null || retrievedIds.isEmpty()) return 0.0;

        Set<String> relevantSet = new HashSet<>(relevantIds);
        for (int i = 0; i < retrievedIds.size(); i++) {
            if (relevantSet.contains(retrievedIds.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /**
     * 计算 Normalized Discounted Cumulative Gain（NDCG@K）
     * <p>
     * DCG_k = Σ(rel_i / log2(i+1))，其中 i 从 1 开始
     * IDCG_k = 理想排序下的 DCG_k
     * NDCG_k = DCG_k / IDCG_k
     *
     * @param relevanceGrades 按检索排名排序的相关性等级（0=不相关，1/2/3=不同程度相关）
     * @param k               截断值
     * @return NDCG@K，范围 [0, 1]
     */
    public double calculateNDCG(List<Integer> relevanceGrades, int k) {
        if (relevanceGrades == null || relevanceGrades.isEmpty()) return 0.0;

        double dcg = 0.0;
        for (int i = 0; i < Math.min(relevanceGrades.size(), k); i++) {
            int rel = relevanceGrades.get(i);
            if (rel > 0) {
                dcg += rel / (Math.log(i + 2) / Math.log(2));
            }
        }

        List<Integer> ideal = new ArrayList<>(relevanceGrades);
        ideal.sort(Collections.reverseOrder());
        double idcg = 0.0;
        for (int i = 0; i < Math.min(ideal.size(), k); i++) {
            int rel = ideal.get(i);
            if (rel > 0) {
                idcg += rel / (Math.log(i + 2) / Math.log(2));
            }
        }

        return idcg == 0.0 ? 0.0 : dcg / idcg;
    }

    /**
     * 计算 Recall@K 的多个 K 值
     *
     * @param relevantIds  相关文档 ID
     * @param retrievedIds 检索结果 ID
     * @return Map of K → Recall@K
     */
    public Map<Integer, Double> calculateRecallAtMultipleK(List<String> relevantIds,
                                                            List<String> retrievedIds) {
        Map<Integer, Double> result = new LinkedHashMap<>();
        for (int k : List.of(1, 3, 5, 10)) {
            result.put(k, calculateRecallAtK(relevantIds, retrievedIds, k));
        }
        return result;
    }
}