package com.edumate.admin.controller;

import com.edumate.common.model.DocumentChunk;
import com.edumate.core.retrieval.HybridSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 搜索控制器 —— 混合检索（向量 + 关键词）入口
 */
@RestController
@RequestMapping("/api")
public class SearchController {

    private final HybridSearchService hybridSearchService;

    public SearchController(HybridSearchService hybridSearchService) {
        this.hybridSearchService = hybridSearchService;
    }

    /**
     * 混合检索 —— 向量语义检索 + BM25 关键词检索，RRF 融合排序
     *
     * @param request 包含 query（必填）和 topK（可选，默认 5）
     * @return 融合排序后的检索结果
     */
    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody SearchRequest request) {
        if (request.query() == null || request.query().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "query 不能为空"));
        }

        int topK = request.topK() > 0 ? request.topK() : 5;
        List<DocumentChunk> results = hybridSearchService.search(request.query(), topK);

        return ResponseEntity.ok(Map.of(
                "query", request.query(),
                "resultsCount", results.size(),
                "results", results
        ));
    }

    /**
     * 搜索请求 DTO
     */
    public record SearchRequest(String query, int topK) {}
}