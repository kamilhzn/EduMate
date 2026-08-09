package com.edumate.core.retrieval;

import com.edumate.common.model.DocumentChunk;
import com.edumate.core.evaluation.TraceService;
import com.edumate.core.graph.GraphSearchService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 混合检索服务 —— 编排向量检索 + 关键词检索 + 图谱检索并行执行，RRF 融合排序
 */
@Service
public class HybridSearchService {

    private final VectorStoreService vectorStoreService;
    private final KeywordIndexService keywordIndexService;
    private final GraphSearchService graphSearchService;
    private final RRFusionService fusionService;
    private final TraceService traceService;

    public HybridSearchService(VectorStoreService vectorStoreService,
                               KeywordIndexService keywordIndexService,
                               GraphSearchService graphSearchService,
                               RRFusionService fusionService,
                               TraceService traceService) {
        this.vectorStoreService = vectorStoreService;
        this.keywordIndexService = keywordIndexService;
        this.graphSearchService = graphSearchService;
        this.fusionService = fusionService;
        this.traceService = traceService;
    }

    /**
     * 混合检索 —— 并行执行向量检索 + 关键词检索 + 图谱检索，三路 RRF 融合后返回 Top-K
     *
     * @param query 用户查询
     * @param topK  返回结果数量上限
     * @return 融合排序后的文档分块列表
     */
    public List<DocumentChunk> search(String query, int topK) {
        String traceId = traceService.startTrace(query, null).getTraceId();

        // 向量检索（多取一些，给融合留空间）
        long t1 = System.currentTimeMillis();
        List<DocumentChunk> vectorResults = vectorStoreService.search(query, topK * 2);
        traceService.recordStage(traceId, "vectorRetrieval",
                System.currentTimeMillis() - t1,
                "query: " + query,
                "found " + vectorResults.size() + " results",
                true, null);

        // 关键词检索
        long t2 = System.currentTimeMillis();
        List<DocumentChunk> keywordResults = keywordIndexService.search(query, topK * 2);
        traceService.recordStage(traceId, "keywordRetrieval",
                System.currentTimeMillis() - t2,
                "query: " + query,
                "found " + keywordResults.size() + " results",
                true, null);

        // 图谱检索
        long t3 = System.currentTimeMillis();
        List<DocumentChunk> graphResults = graphSearchService.search(query, topK * 2);
        traceService.recordStage(traceId, "graphRetrieval",
                System.currentTimeMillis() - t3,
                "query: " + query,
                "found " + graphResults.size() + " results",
                true, null);

        // 三路 RRF 融合
        long t4 = System.currentTimeMillis();
        List<DocumentChunk> fused = fusionService.fuse(vectorResults, keywordResults, graphResults, topK);
        traceService.recordStage(traceId, "rrfFusion",
                System.currentTimeMillis() - t4,
                "vector=" + vectorResults.size() + " keyword=" + keywordResults.size() + " graph=" + graphResults.size(),
                "fused " + fused.size() + " results",
                true, null);

        traceService.completeTrace(traceId);
        return fused;
    }
}