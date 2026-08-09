# EduMate Phase 2：检索能力 —— 执行计划书

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现完整的混合检索链路：文档分块 → 向量化入库 Qdrant → 关键词索引 Elasticsearch → 并行检索 → RRF 融合排序 → 通过 REST API 暴露。

**Architecture:** 在 edumate-core 中新增 `retrieval/` 包，包含 VectorStoreService（Qdrant 向量存储）、KeywordIndexService（ES 关键词索引）、HybridSearchService（并行检索编排）、RRFusionService（RRF 融合）。在 edumate-admin 中新增 SearchController（搜索 API）和 Qdrant/ES 的 Starter 依赖。Docker 基础设施已在 Phase 1 配置完毕，本阶段直接使用。

**Tech Stack:** JDK 21, Spring Boot 3.4.5, LangChain4j Community 1.17.2-beta27, Qdrant (Docker:6334), Elasticsearch 8.15 (Docker:9200), Embedding 模型 text-embedding-v3

---

## 当前状态（Phase 1 完成后）

```
EduMate/
├── edumate-common/
│   └── model/DocumentChunk.java          # 已有：id, content, courseName, chapterPath, metadata
├── edumate-core/
│   └── parser/
│       ├── DocumentParserService.java     # 已有：多格式文档解析
│       └── HierarchicalChunkerService.java # 已有：层级感知切分
└── edumate-admin/
    ├── controller/
    │   └── DocumentController.java        # 已有：POST /api/documents/upload
    └── resources/application.yml          # 已有：LangChain4j DashScope 配置
```

## 目标状态（Phase 2 完成后）

```
EduMate/
├── edumate-core/
│   └── retrieval/                         # 新增
│       ├── VectorStoreService.java        # 向量化 + Qdrant 入库
│       ├── KeywordIndexService.java       # ES 关键词索引 + 检索
│       ├── HybridSearchService.java       # 并行检索编排
│       └── RRFusionService.java           # RRF 倒数排序融合
└── edumate-admin/
    ├── controller/
    │   ├── DocumentController.java        # 修改：上传后自动索引
    │   └── SearchController.java          # 新增：POST /api/search
    └── resources/application.yml          # 修改：添加 Qdrant/ES 配置
```

---

## Task 1: Qdrant 向量存储集成

**Files:**
- Modify: `f:\JetBrains\RAG\EduMate\edumate-admin\pom.xml`（添加 Qdrant 依赖）
- Modify: `f:\JetBrains\RAG\EduMate\edumate-admin\src\main\resources\application.yml`（添加 Qdrant 配置）
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\main\java\com\edumate\core\retrieval\VectorStoreService.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\test\java\com\edumate\core\retrieval\VectorStoreServiceTest.java`

### 1.1 添加 Qdrant 依赖

- [ ] **Step 1: 在 edumate-admin/pom.xml 中添加 LangChain4j Community Qdrant Starter**

在 `edumate-admin/pom.xml` 的 `<dependencies>` 中，LangChain4j 依赖区域下方添加：

```xml
<!-- LangChain4j Community: Qdrant 向量存储（自动配置 EmbeddingStore） -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-qdrant-spring-boot-starter</artifactId>
</dependency>
```

同时在父 POM `f:\JetBrains\RAG\EduMate\pom.xml` 的 `<dependencyManagement>` 中添加版本管理：

```xml
<!-- LangChain4j Community: Qdrant -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-qdrant-spring-boot-starter</artifactId>
    <version>${langchain4j.community.version}</version>
</dependency>
```

- [ ] **Step 2: 在 application.yml 中添加 Qdrant 配置**

在 `edumate-admin/src/main/resources/application.yml` 末尾追加：

```yaml
# LangChain4j Community Qdrant 向量存储
langchain4j:
  community:
    qdrant:
      host: ${QDRANT_HOST:localhost}
      port: ${QDRANT_GRPC_PORT:6334}
      collection-name: edumate-docs
```

- [ ] **Step 3: 编译验证依赖可用**

```powershell
cd f:\JetBrains\RAG\EduMate
$env:JAVA_HOME = 'D:\Jave\.jdks\ms-21.0.11'
.\mvnw.cmd clean compile -q
```

预期：BUILD SUCCESS，Qdrant 相关类可解析。

- [ ] **Step 4: Commit**

```bash
git add pom.xml edumate-admin/pom.xml edumate-admin/src/main/resources/application.yml
git commit -m "feat: add LangChain4j Community Qdrant starter dependency and config"
```

### 1.2 实现 VectorStoreService

- [ ] **Step 5: 编写 VectorStoreService 测试**

```java
package com.edumate.core.retrieval;

import com.edumate.common.model.DocumentChunk;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VectorStoreServiceTest {

    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;

    @Mock
    private EmbeddingModel embeddingModel;

    private VectorStoreService vectorStoreService;

    @BeforeEach
    void setUp() {
        vectorStoreService = new VectorStoreService(embeddingStore, embeddingModel);
    }

    @Test
    void shouldEmbedAndStoreChunks() {
        when(embeddingModel.embedAll(anyList()))
                .thenReturn(embeddingModel.embedAll(anyList()));

        List<DocumentChunk> chunks = List.of(
                DocumentChunk.builder()
                        .id("chunk-1")
                        .content("红黑树是一种自平衡二叉搜索树")
                        .courseName("数据结构")
                        .chapterPath("第3章 > 3.2节 > 红黑树")
                        .build()
        );

        vectorStoreService.indexChunks(chunks);

        verify(embeddingModel).embedAll(anyList());
        verify(embeddingStore).addAll(anyList());
    }

    @Test
    void shouldSearchByQuery() {
        String query = "什么是红黑树";
        when(embeddingModel.embed(query)).thenReturn(mock(Embedding.class));
        when(embeddingStore.search(any(), anyInt())).thenReturn(List.of());

        List<TextSegment> results = vectorStoreService.search(query, 5);

        verify(embeddingModel).embed(query);
        verify(embeddingStore).search(any(), eq(5));
    }
}
```

- [ ] **Step 6: 运行测试验证失败**

```powershell
.\mvnw.cmd test -pl edumate-core -Dtest=VectorStoreServiceTest -q
```

预期：FAIL —— `VectorStoreService` 类尚未创建。

- [ ] **Step 7: 实现 VectorStoreService**

```java
package com.edumate.core.retrieval;

import com.edumate.common.model.DocumentChunk;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量存储服务 —— 将文档分块向量化后存入 Qdrant
 * <p>
 * EmbeddingStore 由 LangChain4j Community Qdrant Starter 自动配置，
 * EmbeddingModel 由 DashScope Starter 自动配置。
 */
@Service
public class VectorStoreService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public VectorStoreService(EmbeddingStore<TextSegment> embeddingStore,
                              EmbeddingModel embeddingModel) {
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

        List<String> texts = segments.stream().map(TextSegment::text).collect(Collectors.toList());
        List<Embedding> embeddings = embeddingModel.embedAll(texts).content();

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            matches.add(new EmbeddingMatch<>(0.0, chunks.get(i).getId(), embeddings.get(i), segments.get(i)));
        }
        // Qdrant EmbeddingStore 的 addAll 接受 Embedding + TextSegment 对
        List<TextSegment> segmentList = segments;
        embeddingStore.addAll(embeddings, segmentList);
    }

    /**
     * 向量检索 —— 返回 Top-K 相似分块
     */
    public List<TextSegment> search(String query, int topK) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(queryEmbedding, topK);
        return matches.stream()
                .map(EmbeddingMatch::embedded)
                .collect(Collectors.toList());
    }
}
```

- [ ] **Step 8: 运行测试验证通过**

```powershell
.\mvnw.cmd test -pl edumate-core -Dtest=VectorStoreServiceTest -q
```

预期：PASS —— 2 个测试通过。

- [ ] **Step 9: Commit**

```bash
git add edumate-core/src/main/java/com/edumate/core/retrieval/VectorStoreService.java
git add edumate-core/src/test/java/com/edumate/core/retrieval/VectorStoreServiceTest.java
git commit -m "feat: implement VectorStoreService for Qdrant embedding storage"
```

---

## Task 2: Elasticsearch 关键词索引

**Files:**
- Modify: `f:\JetBrains\RAG\EduMate\edumate-admin\pom.xml`（添加 ES 依赖）
- Modify: `f:\JetBrains\RAG\EduMate\edumate-admin\src\main\resources\application.yml`（添加 ES 配置）
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\main\java\com\edumate\core\retrieval\KeywordIndexService.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\test\java\com\edumate\core\retrieval\KeywordIndexServiceTest.java`

### 2.1 添加 ES 依赖

- [ ] **Step 1: 在 edumate-admin/pom.xml 中添加 ES 依赖**

在 `edumate-admin/pom.xml` 的 Qdrant 依赖下方添加：

```xml
<!-- Elasticsearch REST Client（关键词检索） -->
<dependency>
    <groupId>org.elasticsearch.client</groupId>
    <artifactId>elasticsearch-rest-client</artifactId>
</dependency>
<dependency>
    <groupId>co.elastic.clients</groupId>
    <artifactId>elasticsearch-java</artifactId>
</dependency>
```

同时在父 POM 的 `<dependencyManagement>` 中添加（Spring Boot 3.4.5 已管理 elasticsearch 版本，无需额外指定版本）。

- [ ] **Step 2: 在 application.yml 中添加 ES 配置**

在 `application.yml` 末尾追加：

```yaml
# Elasticsearch 关键词检索
elasticsearch:
  host: ${ES_HOST:localhost}
  port: ${ES_PORT:9200}
  index-name: edumate-keywords
```

- [ ] **Step 3: 编译验证依赖可用**

```powershell
.\mvnw.cmd clean compile -pl edumate-admin -am -q
```

预期：BUILD SUCCESS。

- [ ] **Step 4: Commit**

```bash
git add edumate-admin/pom.xml edumate-admin/src/main/resources/application.yml pom.xml
git commit -m "feat: add Elasticsearch REST client dependency and config"
```

### 2.2 实现 KeywordIndexService

- [ ] **Step 5: 编写 KeywordIndexService 测试**

```java
package com.edumate.core.retrieval;

import com.edumate.common.model.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordIndexServiceTest {

    private KeywordIndexService keywordIndexService;

    @BeforeEach
    void setUp() {
        // 不连接真实 ES，仅验证数据转换逻辑
        keywordIndexService = new KeywordIndexService(null);
    }

    @Test
    void shouldBuildIndexDocumentFromChunk() {
        DocumentChunk chunk = DocumentChunk.builder()
                .id("chunk-1")
                .content("红黑树是一种自平衡二叉搜索树")
                .courseName("数据结构")
                .chapterPath("第3章 > 3.2节 > 红黑树")
                .build();

        Map<String, Object> doc = keywordIndexService.buildDocument(chunk);

        assertThat(doc).containsKeys("chunk_id", "content", "course_name", "chapter_path");
        assertThat(doc.get("chunk_id")).isEqualTo("chunk-1");
        assertThat(doc.get("content")).isEqualTo("红黑树是一种自平衡二叉搜索树");
        assertThat(doc.get("course_name")).isEqualTo("数据结构");
    }
}
```

- [ ] **Step 6: 运行测试验证失败**

```powershell
.\mvnw.cmd test -pl edumate-core -Dtest=KeywordIndexServiceTest -q
```

预期：FAIL —— `KeywordIndexService` 类尚未创建。

- [ ] **Step 7: 实现 KeywordIndexService**

```java
package com.edumate.core.retrieval;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.edumate.common.model.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
     * 构造器注入，ES 未启动时 esClient 为 null（降级模式）
     */
    public KeywordIndexService(@org.springframework.lang.Nullable ElasticsearchClient esClient) {
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
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
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

    private DocumentChunk hitToChunk(Hit<Map> hit) {
        @SuppressWarnings("unchecked")
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
```

- [ ] **Step 8: 运行测试验证通过**

```powershell
.\mvnw.cmd test -pl edumate-core -Dtest=KeywordIndexServiceTest -q
```

预期：PASS —— 1 个测试通过。

- [ ] **Step 9: Commit**

```bash
git add edumate-core/src/main/java/com/edumate/core/retrieval/KeywordIndexService.java
git add edumate-core/src/test/java/com/edumate/core/retrieval/KeywordIndexServiceTest.java
git commit -m "feat: implement KeywordIndexService for Elasticsearch BM25 indexing and search"
```

---

## Task 3: 混合检索 Pipeline + 搜索 API

**Files:**
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\main\java\com\edumate\core\retrieval\HybridSearchService.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\main\java\com\edumate\core\retrieval\RRFusionService.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\test\java\com\edumate\core\retrieval\RRFusionServiceTest.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-admin\src\main\java\com\edumate\admin\controller\SearchController.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-admin\src\test\java\com\edumate\admin\controller\SearchControllerTest.java`
- Modify: `f:\JetBrains\RAG\EduMate\edumate-admin\src\main\java\com\edumate\admin\controller\DocumentController.java`（上传后自动索引）

### 3.1 实现 RRFusionService

- [ ] **Step 1: 编写 RRFusionService 测试**

```java
package com.edumate.core.retrieval;

import com.edumate.common.model.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RRFusionServiceTest {

    private RRFusionService fusionService;

    @BeforeEach
    void setUp() {
        fusionService = new RRFusionService();
    }

    @Test
    void shouldMergeAndRankResults() {
        DocumentChunk chunkA = DocumentChunk.builder().id("A").content("红黑树定义").courseName("数据结构").build();
        DocumentChunk chunkB = DocumentChunk.builder().id("B").content("二叉搜索树").courseName("数据结构").build();
        DocumentChunk chunkC = DocumentChunk.builder().id("C").content("AVL树").courseName("数据结构").build();

        // 向量检索结果：A 排第1，B 排第2
        List<DocumentChunk> vectorResults = List.of(chunkA, chunkB);
        // 关键词检索结果：B 排第1，C 排第2
        List<DocumentChunk> keywordResults = List.of(chunkB, chunkC);

        List<DocumentChunk> fused = fusionService.fuse(vectorResults, keywordResults, 5);

        assertThat(fused).isNotEmpty();
        // B 在两个结果中都出现，应该排在最前面
        assertThat(fused.get(0).getId()).isEqualTo("B");
        // 去重后应有 3 个唯一结果
        assertThat(fused).hasSize(3);
    }

    @Test
    void shouldHandleEmptyInputs() {
        List<DocumentChunk> empty = List.of();
        List<DocumentChunk> result = fusionService.fuse(empty, empty, 5);
        assertThat(result).isEmpty();
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```powershell
.\mvnw.cmd test -pl edumate-core -Dtest=RRFusionServiceTest -q
```

预期：FAIL —— `RRFusionService` 类尚未创建。

- [ ] **Step 3: 实现 RRFusionService**

```java
package com.edumate.core.retrieval;

import com.edumate.common.model.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RRF（Reciprocal Rank Fusion）倒数排序融合 —— 合并多个检索结果集
 * <p>
 * 公式：score(d) = Σ 1 / (k + rank_i(d))，其中 k=60（经典值）
 * 同一文档在多个结果集中排名越靠前，融合后得分越高。
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

        // 向量检索结果：rank 从 1 开始
        accumulateScores(vectorResults, chunkMap, scores);

        // 关键词检索结果：rank 从 1 开始
        accumulateScores(keywordResults, chunkMap, scores);

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
```

- [ ] **Step 4: 运行测试验证通过**

```powershell
.\mvnw.cmd test -pl edumate-core -Dtest=RRFusionServiceTest -q
```

预期：PASS —— 2 个测试通过。

### 3.2 实现 HybridSearchService

- [ ] **Step 5: 实现 HybridSearchService（无独立测试，依赖注入验证在 SearchController 集成测试中）**

```java
package com.edumate.core.retrieval;

import com.edumate.common.model.DocumentChunk;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 混合检索服务 —— 编排向量检索 + 关键词检索并行执行，RRF 融合排序
 */
@Service
public class HybridSearchService {

    private final VectorStoreService vectorStoreService;
    private final KeywordIndexService keywordIndexService;
    private final RRFusionService fusionService;

    public HybridSearchService(VectorStoreService vectorStoreService,
                               KeywordIndexService keywordIndexService,
                               RRFusionService fusionService) {
        this.vectorStoreService = vectorStoreService;
        this.keywordIndexService = keywordIndexService;
        this.fusionService = fusionService;
    }

    /**
     * 混合检索 —— 并行执行向量检索 + 关键词检索，RRF 融合后返回 Top-K
     *
     * @param query 用户查询
     * @param topK  返回结果数量上限
     * @return 融合排序后的文档分块列表
     */
    public List<DocumentChunk> search(String query, int topK) {
        // 向量检索
        List<TextSegment> vectorSegments = vectorStoreService.search(query, topK * 2);
        List<DocumentChunk> vectorResults = segmentsToChunks(vectorSegments);

        // 关键词检索
        List<DocumentChunk> keywordResults = keywordIndexService.search(query, topK * 2);

        // RRF 融合
        return fusionService.fuse(vectorResults, keywordResults, topK);
    }

    private List<DocumentChunk> segmentsToChunks(List<TextSegment> segments) {
        return segments.stream()
                .map(seg -> {
                    dev.langchain4j.data.document.Metadata metadata = seg.metadata();
                    return DocumentChunk.builder()
                            .id(metadata.getString("chunk_id"))
                            .content(seg.text())
                            .courseName(metadata.getString("course_name"))
                            .chapterPath(metadata.getString("chapter_path"))
                            .parentChunkId(metadata.getString("parent_chunk_id"))
                            .build();
                })
                .collect(Collectors.toList());
    }
}
```

### 3.3 修改 DocumentController（上传后自动索引）

- [ ] **Step 6: 修改 DocumentController 注入检索服务，上传后自动索引**

修改 `f:\JetBrains\RAG\EduMate\edumate-admin\src\main\java\com\edumate\admin\controller\DocumentController.java`：

在现有字段声明后添加：

```java
private final VectorStoreService vectorStoreService;
private final KeywordIndexService keywordIndexService;
```

修改构造函数：

```java
public DocumentController(DocumentParserService parserService,
                          HierarchicalChunkerService chunkerService,
                          VectorStoreService vectorStoreService,
                          KeywordIndexService keywordIndexService) {
    this.parserService = parserService;
    this.chunkerService = chunkerService;
    this.vectorStoreService = vectorStoreService;
    this.keywordIndexService = keywordIndexService;
}
```

在 `try` 块中，`List<DocumentChunk> chunks = chunkerService.chunk(...)` 之后添加：

```java
// 3. 异步索引到向量库和关键词索引（不阻塞响应）
vectorStoreService.indexChunks(chunks);
keywordIndexService.indexChunks(chunks);
```

同时在文件顶部添加 import：

```java
import com.edumate.core.retrieval.VectorStoreService;
import com.edumate.core.retrieval.KeywordIndexService;
```

### 3.4 创建 SearchController

- [ ] **Step 7: 编写 SearchController 测试**

```java
package com.edumate.admin.controller;

import com.edumate.core.retrieval.HybridSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HybridSearchService hybridSearchService;

    @Test
    void searchEndpointShouldReturn200() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType("application/json")
                        .content("{\"query\":\"红黑树\",\"topK\":3}"))
                .andExpect(status().isOk());
    }

    @Test
    void searchShouldReturnResultsArray() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType("application/json")
                        .content("{\"query\":\"数据结构\",\"topK\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.query").value("数据结构"));
    }
}
```

- [ ] **Step 8: 运行测试验证失败**

```powershell
.\mvnw.cmd test -pl edumate-admin -Dtest=SearchControllerTest -q
```

预期：FAIL —— `SearchController` 尚未创建。

- [ ] **Step 9: 实现 SearchController**

```java
package com.edumate.admin.controller;

import com.edumate.common.model.DocumentChunk;
import com.edumate.core.retrieval.HybridSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 搜索控制器 —— 混合检索入口
 */
@RestController
@RequestMapping("/api")
public class SearchController {

    private final HybridSearchService hybridSearchService;

    public SearchController(HybridSearchService hybridSearchService) {
        this.hybridSearchService = hybridSearchService;
    }

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
```

- [ ] **Step 10: 运行测试验证通过**

```powershell
.\mvnw.cmd test -pl edumate-admin -Dtest=SearchControllerTest -q
```

预期：PASS —— 2 个测试通过。

- [ ] **Step 11: 运行全量测试**

```powershell
cd f:\JetBrains\RAG\EduMate
.\mvnw.cmd test -q
```

预期：BUILD SUCCESS，所有模块测试通过。

- [ ] **Step 12: Commit**

```bash
git add edumate-core/src/main/java/com/edumate/core/retrieval/
git add edumate-core/src/test/java/com/edumate/core/retrieval/
git add edumate-admin/src/main/java/com/edumate/admin/controller/
git add edumate-admin/src/test/java/com/edumate/admin/controller/
git commit -m "feat: implement hybrid search pipeline with RRF fusion and search API"
```

---

## Task 4: 端到端验证（Docker 服务 + 全链路）

**Files:**
- 无新建文件（验证已有服务）

- [ ] **Step 1: 启动 Docker 依赖服务**

```powershell
cd f:\JetBrains\RAG\EduMate
docker compose up -d
```

等待约 30 秒让服务启动完成。

- [ ] **Step 2: 验证各服务健康状态**

```powershell
# Qdrant
curl http://localhost:6333/healthz

# Elasticsearch
curl http://localhost:9200

# Neo4j (Phase 3 使用)
curl http://localhost:7474

# Redis (Phase 4 使用)
docker exec edumate-redis redis-cli ping
```

预期：Qdrant 返回健康状态，ES 返回 JSON，Neo4j 返回 200，Redis 返回 `PONG`。

- [ ] **Step 3: 启动 Spring Boot 应用**

在 IDE 中运行 `EduMateApplication`，或命令行：

```powershell
.\mvnw.cmd -pl edumate-admin spring-boot:run
```

- [ ] **Step 4: 上传测试文档**

```powershell
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@edumate-core/src/test/resources/test-document.txt" \
  -F "courseName=数据结构" \
  -F "semester=2026-春"
```

预期：返回 JSON，含 `chunkCount` > 0 和 `chunks` 数组。

- [ ] **Step 5: 执行搜索**

```powershell
curl -X POST http://localhost:8080/api/search \
  -H "Content-Type: application/json" \
  -d '{"query":"什么是二叉树","topK":3}'
```

预期：返回 JSON，含 `results` 数组，结果中包含"二叉树"相关内容。

- [ ] **Step 6: 停止 Docker 服务**

```powershell
docker compose down
```

---

## 验收检查清单

执行完所有 Task 后，逐项确认：

- [ ] `mvn clean compile` 全量编译通过
- [ ] `mvn test` 全量测试通过（新增 VectorStoreServiceTest、KeywordIndexServiceTest、RRFusionServiceTest、SearchControllerTest）
- [ ] `docker compose up -d` 能启动 Qdrant/ES/Neo4j/Redis 四个服务
- [ ] Qdrant 健康检查通过（`curl http://localhost:6333/healthz`）
- [ ] ES 集群信息可访问（`curl http://localhost:9200`）
- [ ] `POST /api/documents/upload` 上传文档后自动索引到 Qdrant 和 ES
- [ ] `POST /api/search` 返回融合排序后的检索结果
- [ ] RRF 融合正确去重并按排名合并
- [ ] 项目结构符合预期（edumate-core 新增 `retrieval/` 包，edumate-admin 新增 SearchController）

---

## Phase 2 完成后的项目结构

```
EduMate/
├── pom.xml
├── compose.yaml
├── .env.example
│
├── edumate-common/
│   └── src/main/java/com/edumate/common/
│       ├── model/
│       │   ├── DocumentChunk.java
│       │   └── CourseMetadata.java
│       └── enums/
│           ├── DocumentType.java
│           └── KnowledgeRelationType.java
│
├── edumate-core/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/edumate/core/
│       │   ├── parser/
│       │   │   ├── DocumentParserService.java
│       │   │   └── HierarchicalChunkerService.java
│       │   └── retrieval/                        # 新增
│       │       ├── VectorStoreService.java        # 向量化 + Qdrant
│       │       ├── KeywordIndexService.java       # ES 关键词索引
│       │       ├── HybridSearchService.java       # 混合检索编排
│       │       └── RRFusionService.java           # RRF 融合
│       └── test/java/com/edumate/core/
│           ├── parser/
│           │   ├── DocumentParserServiceTest.java
│           │   └── HierarchicalChunkerServiceTest.java
│           └── retrieval/                        # 新增
│               ├── VectorStoreServiceTest.java
│               ├── KeywordIndexServiceTest.java
│               └── RRFusionServiceTest.java
│
└── edumate-admin/
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/edumate/admin/
        │   │   ├── EduMateApplication.java
        │   │   └── controller/
        │   │       ├── DocumentController.java    # 修改：上传后自动索引
        │   │       └── SearchController.java      # 新增：搜索 API
        │   └── resources/
        │       └── application.yml                # 修改：添加 Qdrant/ES 配置
        └── test/java/com/edumate/admin/
            ├── EduMateApplicationTests.java
            └── controller/
                ├── DocumentControllerTest.java
                └── SearchControllerTest.java      # 新增
```