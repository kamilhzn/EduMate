package com.edumate.core.retrieval;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.edumate.common.model.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 关键词索引服务 —— 将文档分块索引到 Elasticsearch 并支持 BM25 检索
 * <p>
 * 当 ElasticsearchClient 不可用时（ES 未启动），服务降级为空操作。
 */
@Service
public class KeywordIndexService {

    private static final Logger log = LoggerFactory.getLogger(KeywordIndexService.class);

    private final ElasticsearchClient esClient;

    @Value("${elasticsearch.index-name:edumate-keywords}")
    private String indexName;

    /**
     * 构造器注入，ES 未启动时 esClient 可能为 null（降级模式）
     */
    public KeywordIndexService(@Nullable ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    /**
     * 批量索引文档分块到 ES
     */
    public void indexChunks(List<DocumentChunk> chunks) {
        if (esClient == null || chunks == null || chunks.isEmpty()) {
            return;
        }

        try {
            var bulkBuilder = new co.elastic.clients.elasticsearch.core.BulkRequest.Builder();
            for (DocumentChunk chunk : chunks) {
                Map<String, Object> doc = buildDocument(chunk);
                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .index(indexName)
                                .id(chunk.getId())
                                .document(doc)));
            }

            BulkResponse response = esClient.bulk(bulkBuilder.build());
            long failedCount = response.items().stream()
                    .filter(item -> item.error() != null)
                    .count();
            if (failedCount > 0) {
                log.warn("ES 批量索引: {} 条成功, {} 条失败", chunks.size() - failedCount, failedCount);
            } else {
                log.info("ES 批量索引完成: {} 条分块", chunks.size());
            }
        } catch (IOException e) {
            log.error("ES 索引失败", e);
        }
    }

    /**
     * 关键词检索 —— BM25 全文搜索，返回匹配的 DocumentChunk
     */
    public List<DocumentChunk> search(String query, int topK) {
        if (esClient == null) {
            return List.of();
        }

        try {
            SearchResponse<Map> response = esClient.search(s -> s
                            .index(indexName)
                            .query(q -> q
                                    .match(m -> m
                                            .field("content")
                                            .query(query)))
                            .size(topK),
                    Map.class);

            return response.hits().hits().stream()
                    .map(this::hitToChunk)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("ES 检索失败", e);
            return List.of();
        }
    }

    /**
     * 构建索引文档（供测试使用）
     */
    Map<String, Object> buildDocument(DocumentChunk chunk) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("chunk_id", chunk.getId());
        doc.put("content", chunk.getContent());
        doc.put("course_name", chunk.getCourseName());
        doc.put("chapter_path", chunk.getChapterPath() != null ? chunk.getChapterPath() : "");
        return doc;
    }

    @SuppressWarnings("unchecked")
    private DocumentChunk hitToChunk(Hit<Map> hit) {
        Map<String, Object> source = (Map<String, Object>) hit.source();
        if (source == null) return null;
        return DocumentChunk.builder()
                .id(String.valueOf(source.getOrDefault("chunk_id", "")))
                .content(String.valueOf(source.getOrDefault("content", "")))
                .courseName(String.valueOf(source.getOrDefault("course_name", "")))
                .chapterPath(String.valueOf(source.getOrDefault("chapter_path", "")))
                .build();
    }
}