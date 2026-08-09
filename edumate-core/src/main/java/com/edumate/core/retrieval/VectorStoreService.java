package com.edumate.core.retrieval;

import com.edumate.common.model.DocumentChunk;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量存储服务 —— 将文档分块向量化后存入 Qdrant
 * <p>
 * 当 EmbeddingModel 不可用时（API key 未配置），索引和检索操作降级为空操作。
 */
@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public VectorStoreService(EmbeddingStore<TextSegment> embeddingStore,
                              @Nullable EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 将文档分块向量化后批量存入 Qdrant
     */
    public void indexChunks(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        if (embeddingModel == null) {
            log.warn("EmbeddingModel 不可用，跳过向量索引");
            return;
        }

        List<TextSegment> segments = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            Metadata metadata = new Metadata()
                    .put("chunk_id", chunk.getId())
                    .put("course_name", chunk.getCourseName())
                    .put("chapter_path", chunk.getChapterPath() != null ? chunk.getChapterPath() : "");
            if (chunk.getParentChunkId() != null) {
                metadata.put("parent_chunk_id", chunk.getParentChunkId());
            }
            segments.add(TextSegment.from(chunk.getContent(), metadata));
        }

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);
        log.info("向量入库完成: {} 条分块", segments.size());
    }

    /**
     * 向量检索 —— 返回 Top-K 相似分块
     */
    public List<DocumentChunk> search(String query, int topK) {
        if (embeddingModel == null) {
            log.warn("EmbeddingModel 不可用，返回空检索结果");
            return List.of();
        }
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .build();

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(searchRequest).matches();
        return matches.stream()
                .map(this::matchToChunk)
                .collect(Collectors.toList());
    }

    private DocumentChunk matchToChunk(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        Metadata metadata = segment.metadata();
        return DocumentChunk.builder()
                .id(metadata.getString("chunk_id"))
                .content(segment.text())
                .courseName(metadata.getString("course_name"))
                .chapterPath(metadata.getString("chapter_path"))
                .parentChunkId(metadata.getString("parent_chunk_id"))
                .build();
    }
}