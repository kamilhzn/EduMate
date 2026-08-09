# EduMate Phase 3：知识图谱集成 —— 执行计划书

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 LLM 辅助知识抽取 → Neo4j 知识图谱存储 → 图谱检索 → 三路混合检索（向量 + 关键词 + 图谱）集成。

**Architecture:** 在 edumate-core 中新增 `graph/` 包，包含 KnowledgeExtractionService（LLM 抽取知识点+关系）、KnowledgeGraphService（Neo4j 存储）、GraphSearchService（Cypher 图谱检索）。扩展 RRFusionService 支持三路融合，扩展 HybridSearchService 编排三路检索。在 edumate-admin 中新增 Neo4j Spring Data 依赖和配置。文档上传后自动触发知识抽取与图谱入库。

**Tech Stack:** JDK 21, Spring Boot 3.4.5, Spring Data Neo4j, Neo4j 5-enterprise (Docker:7687), LangChain4j Community 1.17.2-beta27 (ChatLanguageModel), DashScope qwen3.7-plus-2026-05-26

---

## 当前状态（Phase 2 完成后）

```
EduMate/
├── edumate-common/
│   ├── model/
│   │   ├── DocumentChunk.java          # id, content, courseName, chapterPath, metadata
│   │   └── CourseMetadata.java         # courseName, teacher, semester, chapter, level
│   └── enums/
│       └── KnowledgeRelationType.java  # PREREQUISITE, SUCCESSOR, RELATED_TO, BELONGS_TO, APPLIED_IN
├── edumate-core/
│   └── retrieval/
│       ├── VectorStoreService.java     # 向量检索（Qdrant），search() 返回 List<DocumentChunk>
│       ├── KeywordIndexService.java    # 关键词检索（ES），search() 返回 List<DocumentChunk>
│       ├── HybridSearchService.java    # 编排两路检索 + RRF 融合
│       └── RRFusionService.java        # RRF 融合，fuse(vectorResults, keywordResults, topK)
└── edumate-admin/
    ├── controller/
    │   ├── DocumentController.java     # 上传后自动触发 vector + keyword 索引
    │   └── SearchController.java       # POST /api/search
    └── resources/application.yml       # Qdrant/ES/DashScope 配置
```

## 目标状态（Phase 3 完成后）

```
EduMate/
├── edumate-common/
│   └── model/
│       └── KnowledgePoint.java         # 新增：图谱节点模型（非 Neo4j 实体，用于 API 传输）
├── edumate-core/
│   ├── graph/                          # 新增
│   │   ├── KnowledgeExtractionService.java  # LLM 知识抽取
│   │   ├── KnowledgeGraphService.java       # Neo4j 存储
│   │   └── GraphSearchService.java          # 图谱检索
│   └── retrieval/
│       ├── VectorStoreService.java     # 不变
│       ├── KeywordIndexService.java    # 不变
│       ├── HybridSearchService.java    # 修改：编排三路检索
│       └── RRFusionService.java        # 修改：支持三路融合
└── edumate-admin/
    ├── controller/
    │   ├── DocumentController.java     # 修改：上传后触发图谱抽取
    │   └── SearchController.java       # 不变（三路检索对调用方透明）
    └── resources/application.yml       # 修改：添加 Neo4j 配置
```

---

## 文件职责映射

| 文件 | 职责 |
|------|------|
| `KnowledgePoint.java` (common) | 图谱知识点 DTO，用于 API 层传输，与 Neo4j 实体解耦 |
| `KnowledgeExtractionService.java` | 接收 DocumentChunk 列表，调用 LLM 提取知识点 + 关系，返回结构化 JSON |
| `KnowledgeGraphService.java` | 管理 Neo4j 节点/关系 CRUD，使用原生 Neo4j Java Driver 直接操作 |
| `GraphSearchService.java` | 基于用户查询的图谱检索：名称匹配 + 多跳遍历，返回关联 DocumentChunk |
| `RRFusionService.java` (修改) | 新增三路融合方法 `fuse(vector, keyword, graph, topK)` |
| `HybridSearchService.java` (修改) | 注入 GraphSearchService，编排三路并行检索 |
| `DocumentController.java` (修改) | 注入 KnowledgeExtractionService + KnowledgeGraphService，上传后触发图谱抽取 |

---

## 设计决策

### 1. 为什么使用原生 Neo4j Java Driver 而非 Spring Data Neo4j？

Spring Data Neo4j 的 `@Node` 实体要求与 Neo4j 节点结构强绑定，且它的 OGM 映射层在知识图谱多跳遍历场景下不够灵活。使用原生 `org.neo4j.driver` 可以直接执行 Cypher 语句，对多跳查询、关系动态创建等场景更友好，且避免额外的 OGM 学习成本。

### 2. 为什么 KnowledgePoint 放在 common 而非 core？

GraphSearchService 检索到的知识点需要作为 API 响应的一部分返回给前端。将 KnowledgePoint 作为 DTO 放在 common 模块，保持 common 是纯数据结构层，core 是业务逻辑层。

### 3. LLM 知识抽取策略

使用 DashScope 的 ChatLanguageModel（qwen3.7-plus），通过 Few-shot Prompt 引导 LLM 从文档分块中抽取结构化的知识点和关系。LLM 返回 JSON 格式，由 KnowledgeExtractionService 解析后传给 KnowledgeGraphService 入库。

### 4. 抽取时机

文档上传后，在 DocumentController 中同步调用知识抽取（与向量/关键词索引并行），不阻塞 HTTP 响应。抽取失败不影响文档上传成功。

---

## Task 1: Neo4j 依赖与配置

**Files:**
- Modify: `f:\JetBrains\RAG\EduMate\pom.xml`（添加 Neo4j Driver 版本管理）
- Modify: `f:\JetBrains\RAG\EduMate\edumate-core\pom.xml`（添加 Neo4j Driver 依赖）
- Modify: `f:\JetBrains\RAG\EduMate\edumate-admin\src\main\resources\application.yml`（添加 Neo4j 连接配置）

### 1.1 添加 Neo4j 依赖

- [ ] **Step 1: 在父 POM 中添加 Neo4j Driver 版本管理**

在 `f:\JetBrains\RAG\EduMate\pom.xml` 的 `<properties>` 中添加：

```xml
<neo4j.driver.version>5.28.0</neo4j.driver.version>
```

在 `<dependencyManagement>` 的 `<dependencies>` 末尾添加：

```xml
<!-- Neo4j Java Driver（原生驱动，非 Spring Data） -->
<dependency>
    <groupId>org.neo4j.driver</groupId>
    <artifactId>neo4j-java-driver</artifactId>
    <version>${neo4j.driver.version}</version>
</dependency>
```

- [ ] **Step 2: 在 edumate-core/pom.xml 中添加 Neo4j Driver 依赖**

在 `f:\JetBrains\RAG\EduMate\edumate-core\pom.xml` 的 `<dependencies>` 中，Elasticsearch 依赖下方添加：

```xml
<!-- Neo4j Java Driver（知识图谱存储） -->
<dependency>
    <groupId>org.neo4j.driver</groupId>
    <artifactId>neo4j-java-driver</artifactId>
</dependency>
```

- [ ] **Step 3: 在 application.yml 中添加 Neo4j 配置**

在 `f:\JetBrains\RAG\EduMate\edumate-admin\src\main\resources\application.yml` 末尾追加：

```yaml
# Neo4j 图数据库（知识图谱）
neo4j:
  uri: ${NEO4J_URI:bolt://localhost:7687}
  username: ${NEO4J_USERNAME:neo4j}
  password: ${NEO4J_PASSWORD:password123}
  database: ${NEO4J_DATABASE:neo4j}
```

- [ ] **Step 4: 编译验证依赖可用**

```powershell
cd f:\JetBrains\RAG\EduMate
$env:JAVA_HOME = 'D:\Jave\.jdks\ms-21.0.11'
.\mvnw.cmd clean compile -pl edumate-core -am -q
```

预期：BUILD SUCCESS，Neo4j Driver 相关类可解析。

- [ ] **Step 5: Commit**

```bash
git add pom.xml edumate-core/pom.xml edumate-admin/src/main/resources/application.yml
git commit -m "feat: add Neo4j Java Driver dependency and connection config"
```

---

## Task 2: 知识图谱数据模型

**Files:**
- Create: `f:\JetBrains\RAG\EduMate\edumate-common\src\main\java\com\edumate\common\model\KnowledgePoint.java`

### 2.1 创建 KnowledgePoint DTO

- [ ] **Step 1: 创建 KnowledgePoint 模型类**

```java
package com.edumate.common.model;

import com.edumate.common.enums.KnowledgeRelationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 知识点 —— 知识图谱中的节点
 * <p>
 * 用于 API 层传输，与 Neo4j 内部存储结构解耦。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgePoint {
    /** 知识点名称（唯一标识），如 "红黑树" */
    private String name;
    /** 知识点描述 */
    private String description;
    /** 所属课程 */
    private String courseName;
    /** 章节路径 */
    private String chapterPath;
    /** 来源分块 ID */
    private String sourceChunkId;
    /** 关联的知识点及关系 */
    private List<KnowledgeRelation> relations;
}

/**
 * 知识点关系
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class KnowledgeRelation {
    /** 关系类型 */
    private KnowledgeRelationType type;
    /** 目标知识点名称 */
    private String targetName;
    /** 关系描述 */
    private String description;
}
```

注意：`KnowledgeRelation` 是包级内部类，或者单独成为文件。为保持简洁，这里用内部类。但考虑到 lombok 和 Jackson 序列化，建议改为独立文件。修改为：

```java
package com.edumate.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 知识点 —— 知识图谱中的节点
 * <p>
 * 用于 API 层传输，与 Neo4j 内部存储结构解耦。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgePoint {
    /** 知识点名称（唯一标识），如 "红黑树" */
    private String name;
    /** 知识点描述 */
    private String description;
    /** 所属课程 */
    private String courseName;
    /** 章节路径 */
    private String chapterPath;
    /** 来源分块 ID */
    private String sourceChunkId;
    /** 关联的知识点及关系 */
    private List<KnowledgeRelation> relations;
}
```

同时创建 `KnowledgeRelation`：

```java
package com.edumate.common.model;

import com.edumate.common.enums.KnowledgeRelationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识点关系 —— 知识图谱中的边
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRelation {
    /** 关系类型 */
    private KnowledgeRelationType type;
    /** 目标知识点名称 */
    private String targetName;
    /** 关系描述 */
    private String description;
}
```

- [ ] **Step 2: 编译验证模型类可解析**

```powershell
.\mvnw.cmd clean compile -pl edumate-common -q
```

预期：BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add edumate-common/src/main/java/com/edumate/common/model/KnowledgePoint.java
git add edumate-common/src/main/java/com/edumate/common/model/KnowledgeRelation.java
git commit -m "feat: add KnowledgePoint and KnowledgeRelation DTOs for graph data model"
```

---

## Task 3: 知识抽取服务

**Files:**
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\main\java\com\edumate\core\graph\KnowledgeExtractionService.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\test\java\com\edumate\core\graph\KnowledgeExtractionServiceTest.java`

### 3.1 编写测试

- [ ] **Step 1: 编写 KnowledgeExtractionService 测试**

```java
package com.edumate.core.graph;

import com.edumate.common.model.DocumentChunk;
import com.edumate.common.model.KnowledgePoint;
import com.edumate.common.enums.KnowledgeRelationType;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeExtractionServiceTest {

    @Mock
    private ChatLanguageModel chatLanguageModel;

    private KnowledgeExtractionService extractionService;

    @BeforeEach
    void setUp() {
        extractionService = new KnowledgeExtractionService(chatLanguageModel);
    }

    @Test
    void shouldExtractKnowledgePointsFromChunks() {
        String llmResponse = """
                [
                  {
                    "name": "红黑树",
                    "description": "一种自平衡二叉搜索树，通过颜色标记节点来维持平衡",
                    "courseName": "数据结构",
                    "chapterPath": "第3章 > 3.2节 > 红黑树",
                    "relations": [
                      {
                        "type": "PREREQUISITE",
                        "targetName": "二叉搜索树",
                        "description": "红黑树是二叉搜索树的扩展"
                      },
                      {
                        "type": "RELATED_TO",
                        "targetName": "AVL树",
                        "description": "都是自平衡二叉搜索树，平衡策略不同"
                      }
                    ]
                  },
                  {
                    "name": "二叉搜索树",
                    "description": "左子树节点值小于根节点，右子树节点值大于根节点的二叉树",
                    "courseName": "数据结构",
                    "chapterPath": "第3章 > 3.1节 > 二叉搜索树",
                    "relations": []
                  }
                ]""";

        when(chatLanguageModel.chat(anyString())).thenReturn(llmResponse);

        List<DocumentChunk> chunks = List.of(
                DocumentChunk.builder()
                        .id("chunk-1")
                        .content("红黑树是一种自平衡二叉搜索树，通过颜色标记节点来维持平衡。它是二叉搜索树的扩展。")
                        .courseName("数据结构")
                        .chapterPath("第3章 > 3.2节 > 红黑树")
                        .build()
        );

        List<KnowledgePoint> points = extractionService.extract(chunks);

        assertThat(points).hasSize(2);
        assertThat(points.get(0).getName()).isEqualTo("红黑树");
        assertThat(points.get(0).getRelations()).hasSize(2);
        assertThat(points.get(0).getRelations().get(0).getType())
                .isEqualTo(KnowledgeRelationType.PREREQUISITE);
    }

    @Test
    void shouldReturnEmptyListWhenChatModelIsNull() {
        List<DocumentChunk> chunks = List.of(
                DocumentChunk.builder()
                        .id("chunk-1")
                        .content("测试内容")
                        .courseName("数据结构")
                        .build()
        );

        KnowledgeExtractionService serviceWithNullModel = new KnowledgeExtractionService(null);
        List<KnowledgePoint> points = serviceWithNullModel.extract(chunks);

        assertThat(points).isEmpty();
    }

    @Test
    void shouldReturnEmptyListForEmptyChunks() {
        List<KnowledgePoint> points = extractionService.extract(List.of());
        assertThat(points).isEmpty();
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```powershell
.\mvnw.cmd test -pl edumate-core -Dtest=KnowledgeExtractionServiceTest -q
```

预期：FAIL —— `KnowledgeExtractionService` 类尚未创建。

### 3.2 实现 KnowledgeExtractionService

- [ ] **Step 3: 实现 KnowledgeExtractionService**

```java
package com.edumate.core.graph;

import com.edumate.common.model.DocumentChunk;
import com.edumate.common.model.KnowledgePoint;
import com.edumate.common.model.KnowledgeRelation;
import com.edumate.common.enums.KnowledgeRelationType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识抽取服务 —— 使用 LLM 从文档分块中提取结构化的知识点和关系
 * <p>
 * 当 ChatLanguageModel 不可用时，返回空列表（降级模式）。
 */
@Service
public class KnowledgeExtractionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeExtractionService.class);

    private final ChatLanguageModel chatLanguageModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String EXTRACTION_PROMPT = """
            你是一个课程知识图谱构建专家。请从以下课程文档内容中提取所有知识点及其关系。

            ## 提取规则
            1. 每个知识点必须有 name（名称）和 description（描述）
            2. 关系类型包括：
               - PREREQUISITE（前置知识）：学习该知识点前需要掌握的内容
               - SUCCESSOR（后继知识）：该知识点是后续学习的基础
               - RELATED_TO（关联）：两个知识点之间存在交叉或类比关系
               - BELONGS_TO（属于）：知识点属于某个章节/课程
               - APPLIED_IN（应用）：知识点在某个场景中的应用
            3. 只提取文档中明确提到的知识点，不要编造
            4. 输出严格 JSON 数组格式

            ## 文档内容
            课程：%s
            章节：%s
            内容：
            %s

            ## 输出格式
            请输出如下格式的 JSON 数组（不要包含其他文字）：
            [
              {
                "name": "知识点名称",
                "description": "知识点描述",
                "relations": [
                  {"type": "PREREQUISITE", "targetName": "目标知识点名称", "description": "关系描述"}
                ]
              }
            ]
            """;

    public KnowledgeExtractionService(@Nullable ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    /**
     * 从文档分块中提取知识点和关系
     *
     * @param chunks 文档分块列表
     * @return 提取到的知识点列表（含关系）
     */
    public List<KnowledgePoint> extract(List<DocumentChunk> chunks) {
        if (chatLanguageModel == null) {
            log.warn("ChatLanguageModel 不可用，跳过知识抽取");
            return List.of();
        }
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        List<KnowledgePoint> allPoints = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        for (DocumentChunk chunk : chunks) {
            try {
                String prompt = String.format(EXTRACTION_PROMPT,
                        chunk.getCourseName(),
                        chunk.getChapterPath() != null ? chunk.getChapterPath() : "",
                        chunk.getContent());

                String response = chatLanguageModel.chat(prompt);
                List<KnowledgePointRaw> rawPoints = parseResponse(response);

                for (KnowledgePointRaw raw : rawPoints) {
                    if (seenNames.add(raw.name)) {
                        allPoints.add(KnowledgePoint.builder()
                                .name(raw.name)
                                .description(raw.description)
                                .courseName(chunk.getCourseName())
                                .chapterPath(chunk.getChapterPath())
                                .sourceChunkId(chunk.getId())
                                .relations(raw.relations != null ? raw.relations.stream()
                                        .map(r -> KnowledgeRelation.builder()
                                                .type(parseRelationType(r.type))
                                                .targetName(r.targetName)
                                                .description(r.description)
                                                .build())
                                        .collect(Collectors.toList()) : List.of())
                                .build());
                    }
                }
            } catch (Exception e) {
                log.warn("知识抽取失败 (chunk={}): {}", chunk.getId(), e.getMessage());
            }
        }

        log.info("知识抽取完成: {} 个分块 → {} 个知识点", chunks.size(), allPoints.size());
        return allPoints;
    }

    private List<KnowledgePointRaw> parseResponse(String response) throws JsonProcessingException {
        // LLM 可能返回带 markdown 代码块的 JSON，先清理
        String json = response.trim();
        if (json.startsWith("```json")) {
            json = json.substring(7);
        }
        if (json.startsWith("```")) {
            json = json.substring(3);
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        json = json.trim();

        return objectMapper.readValue(json, new TypeReference<List<KnowledgePointRaw>>() {});
    }

    private KnowledgeRelationType parseRelationType(String type) {
        try {
            return KnowledgeRelationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return KnowledgeRelationType.RELATED_TO;
        }
    }

    /**
     * LLM 响应的原始 JSON 结构（用于反序列化）
     */
    private static class KnowledgePointRaw {
        public String name;
        public String description;
        public List<RelationRaw> relations;
    }

    private static class RelationRaw {
        public String type;
        public String targetName;
        public String description;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```powershell
.\mvnw.cmd test -pl edumate-core -Dtest=KnowledgeExtractionServiceTest -q
```

预期：PASS —— 3 个测试通过。

- [ ] **Step 5: Commit**

```bash
git add edumate-core/src/main/java/com/edumate/core/graph/KnowledgeExtractionService.java
git add edumate-core/src/test/java/com/edumate/core/graph/KnowledgeExtractionServiceTest.java
git commit -m "feat: implement KnowledgeExtractionService with LLM-powered knowledge extraction"
```

---

## Task 4: 知识图谱存储服务

**Files:**
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\main\java\com\edumate\core\graph\KnowledgeGraphService.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\test\java\com\edumate\core\graph\KnowledgeGraphServiceTest.java`

### 4.1 编写测试

- [ ] **Step 1: 编写 KnowledgeGraphService 测试**

```java
package com.edumate.core.graph;

import com.edumate.common.model.KnowledgePoint;
import com.edumate.common.model.KnowledgeRelation;
import com.edumate.common.enums.KnowledgeRelationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@SuppressWarnings("resource")
class KnowledgeGraphServiceTest {

    private Driver driver;
    private Session session;
    private Transaction tx;
    private KnowledgeGraphService graphService;

    @BeforeEach
    void setUp() {
        driver = mock(Driver.class);
        session = mock(Session.class);
        tx = mock(Transaction.class);
        when(driver.session(any(SessionConfig.class))).thenReturn(session);
        when(session.beginTransaction()).thenReturn(tx);
        graphService = new KnowledgeGraphService(driver);
    }

    @Test
    void shouldSaveKnowledgePoints() {
        when(tx.run(anyString(), anyMap())).thenReturn(mock(Result.class));

        List<KnowledgePoint> points = List.of(
                KnowledgePoint.builder()
                        .name("红黑树")
                        .description("一种自平衡二叉搜索树")
                        .courseName("数据结构")
                        .chapterPath("第3章 > 3.2节 > 红黑树")
                        .sourceChunkId("chunk-1")
                        .relations(List.of(
                                KnowledgeRelation.builder()
                                        .type(KnowledgeRelationType.PREREQUISITE)
                                        .targetName("二叉搜索树")
                                        .description("红黑树是二叉搜索树的扩展")
                                        .build()
                        ))
                        .build()
        );

        graphService.savePoints(points);

        verify(tx, atLeastOnce()).run(anyString(), anyMap());
        verify(tx).commit();
        verify(session).close();
    }

    @Test
    void shouldHandleEmptyPoints() {
        graphService.savePoints(List.of());

        verify(tx, never()).run(anyString(), anyMap());
        verify(session).close();
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```powershell
.\mvnw.cmd test -pl edumate-core -Dtest=KnowledgeGraphServiceTest -q
```

预期：FAIL —— `KnowledgeGraphService` 类尚未创建。

### 4.2 实现 KnowledgeGraphService

- [ ] **Step 3: 实现 KnowledgeGraphService**

```java
package com.edumate.core.graph;

import com.edumate.common.model.KnowledgePoint;
import com.edumate.common.model.KnowledgeRelation;
import org.neo4j.driver.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱存储服务 —— 将知识点和关系写入 Neo4j
 * <p>
 * 使用原生 Neo4j Java Driver，通过 Cypher MERGE 实现 upsert 语义。
 * 当 Driver 不可用时（Neo4j 未启动），操作降级为空操作。
 */
@Service
public class KnowledgeGraphService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphService.class);

    private final Driver driver;

    @Value("${neo4j.database:neo4j}")
    private String database;

    public KnowledgeGraphService(@Nullable Driver driver) {
        this.driver = driver;
    }

    /**
     * 批量保存知识点到 Neo4j
     * <p>
     * 每个知识点创建为 KnowledgePoint 节点，
     * 每个关系创建为有向边（带类型和描述属性）。
     */
    public void savePoints(List<KnowledgePoint> points) {
        if (driver == null) {
            log.warn("Neo4j Driver 不可用，跳过图谱存储");
            return;
        }
        if (points == null || points.isEmpty()) {
            return;
        }

        try (Session session = driver.session(SessionConfig.forDatabase(database))) {
            Transaction tx = session.beginTransaction();

            for (KnowledgePoint point : points) {
                // 创建知识点节点
                tx.run("""
                        MERGE (k:KnowledgePoint {name: $name})
                        SET k.description = $description,
                            k.courseName = $courseName,
                            k.chapterPath = $chapterPath,
                            k.sourceChunkId = $sourceChunkId
                        """,
                        Map.of(
                                "name", point.getName(),
                                "description", point.getDescription() != null ? point.getDescription() : "",
                                "courseName", point.getCourseName() != null ? point.getCourseName() : "",
                                "chapterPath", point.getChapterPath() != null ? point.getChapterPath() : "",
                                "sourceChunkId", point.getSourceChunkId() != null ? point.getSourceChunkId() : ""
                        ));

                // 创建关系
                if (point.getRelations() != null) {
                    for (KnowledgeRelation rel : point.getRelations()) {
                        // 先确保目标节点存在（占位节点）
                        tx.run("""
                                MERGE (target:KnowledgePoint {name: $targetName})
                                ON CREATE SET target.description = '',
                                    target.courseName = $courseName,
                                    target.chapterPath = '',
                                    target.sourceChunkId = ''
                                """,
                                Map.of(
                                        "targetName", rel.getTargetName(),
                                        "courseName", point.getCourseName() != null ? point.getCourseName() : ""
                                ));

                        // 创建关系
                        String relType = rel.getType() != null ? rel.getType().name() : "RELATED_TO";
                        tx.run("""
                                MATCH (a:KnowledgePoint {name: $fromName})
                                MATCH (b:KnowledgePoint {name: $toName})
                                MERGE (a)-[r:%s]->(b)
                                SET r.description = $description
                                """.formatted(relType),
                                Map.of(
                                        "fromName", point.getName(),
                                        "toName", rel.getTargetName(),
                                        "description", rel.getDescription() != null ? rel.getDescription() : ""
                                ));
                    }
                }
            }

            tx.commit();
            log.info("图谱存储完成: {} 个知识点", points.size());
        } catch (Exception e) {
            log.error("图谱存储失败", e);
        }
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```powershell
.\mvnw.cmd test -pl edumate-core -Dtest=KnowledgeGraphServiceTest -q
```

预期：PASS —— 2 个测试通过。

- [ ] **Step 5: Commit**

```bash
git add edumate-core/src/main/java/com/edumate/core/graph/KnowledgeGraphService.java
git add edumate-core/src/test/java/com/edumate/core/graph/KnowledgeGraphServiceTest.java
git commit -m "feat: implement KnowledgeGraphService for Neo4j graph storage with upsert"
```

---

## Task 5: 图谱检索服务

**Files:**
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\main\java\com\edumate\core\graph\GraphSearchService.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\test\java\com\edumate\core\graph\GraphSearchServiceTest.java`

### 5.1 编写测试

- [ ] **Step 1: 编写 GraphSearchService 测试**

```java
package com.edumate.core.graph;

import com.edumate.common.model.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@SuppressWarnings("resource")
class GraphSearchServiceTest {

    private Driver driver;
    private Session session;
    private GraphSearchService graphSearchService;

    @BeforeEach
    void setUp() {
        driver = mock(Driver.class);
        session = mock(Session.class);
        when(driver.session(any(SessionConfig.class))).thenReturn(session);
        graphSearchService = new GraphSearchService(driver);
    }

    @Test
    void shouldReturnEmptyListWhenDriverIsNull() {
        GraphSearchService service = new GraphSearchService(null);
        List<DocumentChunk> results = service.search("红黑树", 5);
        assertThat(results).isEmpty();
    }

    @Test
    void shouldSearchByNameMatch() {
        Result result = mock(Result.class);
        Record record = mock(Record.class);
        Value value = mock(Value.class);

        when(session.run(anyString(), anyMap())).thenReturn(result);
        when(result.list()).thenReturn(List.of(record));
        when(record.get("name")).thenReturn(value);
        when(record.get("description")).thenReturn(value);
        when(record.get("courseName")).thenReturn(value);
        when(record.get("chapterPath")).thenReturn(value);
        when(record.get("sourceChunkId")).thenReturn(value);
        when(record.get("content")).thenReturn(value);

        when(value.asString())
                .thenReturn("红黑树")
                .thenReturn("一种自平衡二叉搜索树")
                .thenReturn("数据结构")
                .thenReturn("第3章 > 3.2节 > 红黑树")
                .thenReturn("chunk-1")
                .thenReturn("红黑树是一种自平衡二叉搜索树");

        List<DocumentChunk> results = graphSearchService.search("红黑树", 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo("chunk-1");
        assertThat(results.get(0).getCourseName()).isEqualTo("数据结构");
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```powershell
.\mvnw.cmd test -pl edumate-core -Dtest=GraphSearchServiceTest -q
```

预期：FAIL —— `GraphSearchService` 类尚未创建。

### 5.2 实现 GraphSearchService

- [ ] **Step 3: 实现 GraphSearchService**

```java
package com.edumate.core.graph;

import com.edumate.common.model.DocumentChunk;
import org.neo4j.driver.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 图谱检索服务 —— 基于 Neo4j 知识图谱的关系检索
 * <p>
 * 检索策略：
 * 1. 名称匹配：查找名称与查询相关的知识点节点
 * 2. 多跳遍历：沿关系扩展 1-2 跳，获取关联知识点
 * 3. 返回关联的 DocumentChunk（通过 sourceChunkId 关联回原始文档）
 * <p>
 * 当 Driver 不可用时（Neo4j 未启动），返回空列表。
 */
@Service
public class GraphSearchService {

    private static final Logger log = LoggerFactory.getLogger(GraphSearchService.class);

    private final Driver driver;

    @Value("${neo4j.database:neo4j}")
    private String database;

    public GraphSearchService(@Nullable Driver driver) {
        this.driver = driver;
    }

    /**
     * 图谱检索 —— 查询与用户输入相关的知识点及其关联内容
     *
     * @param query 用户查询
     * @param topK  返回结果数量上限
     * @return 关联的文档分块列表
     */
    public List<DocumentChunk> search(String query, int topK) {
        if (driver == null) {
            log.warn("Neo4j Driver 不可用，返回空图谱检索结果");
            return List.of();
        }

        try (Session session = driver.session(SessionConfig.forDatabase(database))) {
            // 策略：名称模糊匹配 + 1-2 跳图遍历，返回关联的知识点
            String cypher = """
                    MATCH (k:KnowledgePoint)
                    WHERE k.name CONTAINS $query
                    OPTIONAL MATCH (k)-[r]->(related:KnowledgePoint)
                    WHERE type(r) IN ['PREREQUISITE', 'SUCCESSOR', 'RELATED_TO', 'APPLIED_IN']
                    RETURN DISTINCT
                        k.name AS name,
                        k.description AS description,
                        k.courseName AS courseName,
                        k.chapterPath AS chapterPath,
                        k.sourceChunkId AS sourceChunkId,
                        k.description AS content
                    UNION
                    MATCH (related:KnowledgePoint)-[r]->(k:KnowledgePoint)
                    WHERE k.name CONTAINS $query
                      AND type(r) IN ['PREREQUISITE', 'SUCCESSOR', 'RELATED_TO', 'APPLIED_IN']
                    RETURN DISTINCT
                        related.name AS name,
                        related.description AS description,
                        related.courseName AS courseName,
                        related.chapterPath AS chapterPath,
                        related.sourceChunkId AS sourceChunkId,
                        related.description AS content
                    LIMIT $limit
                    """;

            List<DocumentChunk> results = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();

            var records = session.run(cypher,
                    Map.of("query", query, "limit", topK * 2)).list();

            for (var record : records) {
                String sourceChunkId = record.get("sourceChunkId").asString(null);
                String id = sourceChunkId != null ? sourceChunkId : record.get("name").asString();
                if (seenIds.add(id)) {
                    results.add(DocumentChunk.builder()
                            .id(id)
                            .content(record.get("content").asString(""))
                            .courseName(record.get("courseName").asString(""))
                            .chapterPath(record.get("chapterPath").asString(""))
                            .build());
                }
            }

            log.debug("图谱检索: query='{}' → {} 条结果", query, results.size());
            return results.stream().limit(topK).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("图谱检索失败", e);
            return List.of();
        }
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```powershell
.\mvnw.cmd test -pl edumate-core -Dtest=GraphSearchServiceTest -q
```

预期：PASS —— 2 个测试通过。

- [ ] **Step 5: Commit**

```bash
git add edumate-core/src/main/java/com/edumate/core/graph/GraphSearchService.java
git add edumate-core/src/test/java/com/edumate/core/graph/GraphSearchServiceTest.java
git commit -m "feat: implement GraphSearchService for Neo4j knowledge graph retrieval"
```

---

## Task 6: 集成到混合检索 Pipeline

**Files:**
- Modify: `f:\JetBrains\RAG\EduMate\edumate-core\src\main\java\com\edumate\core\retrieval\RRFusionService.java`（新增三路融合方法）
- Modify: `f:\JetBrains\RAG\EduMate\edumate-core\src\test\java\com\edumate\core\retrieval\RRFusionServiceTest.java`（新增三路融合测试）
- Modify: `f:\JetBrains\RAG\EduMate\edumate-core\src\main\java\com\edumate\core\retrieval\HybridSearchService.java`（注入 GraphSearchService，编排三路检索）
- Modify: `f:\JetBrains\RAG\EduMate\edumate-admin\src\main\java\com\edumate\admin\controller\DocumentController.java`（上传后触发图谱抽取）

### 6.1 扩展 RRFusionService 支持三路融合

- [ ] **Step 1: 在 RRFusionServiceTest 中添加三路融合测试**

在 `f:\JetBrains\RAG\EduMate\edumate-core\src\test\java\com\edumate\core\retrieval\RRFusionServiceTest.java` 的现有测试类中追加：

```java
@Test
void shouldFuseThreeResultSets() {
    DocumentChunk chunkA = DocumentChunk.builder().id("A").content("红黑树定义").courseName("数据结构").build();
    DocumentChunk chunkB = DocumentChunk.builder().id("B").content("二叉搜索树").courseName("数据结构").build();
    DocumentChunk chunkC = DocumentChunk.builder().id("C").content("AVL树").courseName("数据结构").build();
    DocumentChunk chunkD = DocumentChunk.builder().id("D").content("B树").courseName("数据结构").build();

    // 向量：A(1), B(2), C(3)
    List<DocumentChunk> vectorResults = List.of(chunkA, chunkB, chunkC);
    // 关键词：B(1), C(2)
    List<DocumentChunk> keywordResults = List.of(chunkB, chunkC);
    // 图谱：C(1), D(2)
    List<DocumentChunk> graphResults = List.of(chunkC, chunkD);

    List<DocumentChunk> fused = fusionService.fuse(vectorResults, keywordResults, graphResults, 5);

    assertThat(fused).isNotEmpty();
    // C 在三路结果中都出现，得分最高
    assertThat(fused.get(0).getId()).isEqualTo("C");
    // B 在两路结果中出现，得分第二
    assertThat(fused.get(1).getId()).isEqualTo("B");
    // 去重后应有 4 个唯一结果
    assertThat(fused).hasSize(4);
}
```

- [ ] **Step 2: 运行测试验证失败**

```powershell
.\mvnw.cmd test -pl edumate-core -Dtest=RRFusionServiceTest#shouldFuseThreeResultSets -q
```

预期：FAIL —— 三路融合方法尚未定义。

- [ ] **Step 3: 在 RRFusionService 中添加三路融合方法**

在 `f:\JetBrains\RAG\EduMate\edumate-core\src\main\java\com\edumate\core\retrieval\RRFusionService.java` 中，在现有 `fuse` 方法后追加：

```java
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
```

- [ ] **Step 4: 运行测试验证通过**

```powershell
.\mvnw.cmd test -pl edumate-core -Dtest=RRFusionServiceTest -q
```

预期：PASS —— 3 个测试通过（原有 2 个 + 新增 1 个）。

### 6.2 修改 HybridSearchService 编排三路检索

- [ ] **Step 5: 修改 HybridSearchService**

将 `f:\JetBrains\RAG\EduMate\edumate-core\src\main\java\com\edumate\core\retrieval\HybridSearchService.java` 的内容替换为：

```java
package com.edumate.core.retrieval;

import com.edumate.common.model.DocumentChunk;
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

    public HybridSearchService(VectorStoreService vectorStoreService,
                               KeywordIndexService keywordIndexService,
                               GraphSearchService graphSearchService,
                               RRFusionService fusionService) {
        this.vectorStoreService = vectorStoreService;
        this.keywordIndexService = keywordIndexService;
        this.graphSearchService = graphSearchService;
        this.fusionService = fusionService;
    }

    /**
     * 混合检索 —— 并行执行向量检索 + 关键词检索 + 图谱检索，三路 RRF 融合后返回 Top-K
     *
     * @param query 用户查询
     * @param topK  返回结果数量上限
     * @return 融合排序后的文档分块列表
     */
    public List<DocumentChunk> search(String query, int topK) {
        // 向量检索（多取一些，给融合留空间）
        List<DocumentChunk> vectorResults = vectorStoreService.search(query, topK * 2);

        // 关键词检索
        List<DocumentChunk> keywordResults = keywordIndexService.search(query, topK * 2);

        // 图谱检索
        List<DocumentChunk> graphResults = graphSearchService.search(query, topK * 2);

        // 三路 RRF 融合
        return fusionService.fuse(vectorResults, keywordResults, graphResults, topK);
    }
}
```

### 6.3 修改 DocumentController 触发图谱抽取

- [ ] **Step 6: 修改 DocumentController 注入图谱服务**

修改 `f:\JetBrains\RAG\EduMate\edumate-admin\src\main\java\com\edumate\admin\controller\DocumentController.java`：

在现有字段声明后添加：

```java
private final KnowledgeExtractionService extractionService;
private final KnowledgeGraphService knowledgeGraphService;
```

修改构造函数：

```java
public DocumentController(DocumentParserService parserService,
                          HierarchicalChunkerService chunkerService,
                          VectorStoreService vectorStoreService,
                          KeywordIndexService keywordIndexService,
                          KnowledgeExtractionService extractionService,
                          KnowledgeGraphService knowledgeGraphService) {
    this.parserService = parserService;
    this.chunkerService = chunkerService;
    this.vectorStoreService = vectorStoreService;
    this.keywordIndexService = keywordIndexService;
    this.extractionService = extractionService;
    this.knowledgeGraphService = knowledgeGraphService;
}
```

在 `try` 块中，`keywordIndexService.indexChunks(chunks);` 之后添加：

```java
// 4. 知识抽取并存入图谱
List<KnowledgePoint> points = extractionService.extract(chunks);
knowledgeGraphService.savePoints(points);
```

同时在文件顶部添加 import：

```java
import com.edumate.core.graph.KnowledgeExtractionService;
import com.edumate.core.graph.KnowledgeGraphService;
import com.edumate.common.model.KnowledgePoint;
```

- [ ] **Step 7: 编译验证**

```powershell
.\mvnw.cmd clean compile -q
```

预期：BUILD SUCCESS。

- [ ] **Step 8: 运行全量测试**

```powershell
.\mvnw.cmd test -q
```

预期：BUILD SUCCESS，所有模块测试通过。

- [ ] **Step 9: Commit**

```bash
git add edumate-core/src/main/java/com/edumate/core/retrieval/RRFusionService.java
git add edumate-core/src/test/java/com/edumate/core/retrieval/RRFusionServiceTest.java
git add edumate-core/src/main/java/com/edumate/core/retrieval/HybridSearchService.java
git add edumate-admin/src/main/java/com/edumate/admin/controller/DocumentController.java
git commit -m "feat: integrate graph search into hybrid retrieval pipeline with 3-way RRF fusion"
```

---

## Task 7: Neo4j Driver Bean 配置

**Files:**
- Create: `f:\JetBrains\RAG\EduMate\edumate-admin\src\main\java\com\edumate\admin\config\Neo4jConfig.java`

### 7.1 创建 Neo4j 配置

- [ ] **Step 1: 创建 Neo4jConfig**

```java
package com.edumate.admin.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Neo4j Driver 配置 —— 原生 Java Driver，非 Spring Data
 * <p>
 * 当 Neo4j 不可用时，返回 null（KnowledgeGraphService 和 GraphSearchService 会降级处理）。
 */
@Configuration
public class Neo4jConfig {

    private static final Logger log = LoggerFactory.getLogger(Neo4jConfig.class);

    @Value("${neo4j.uri:bolt://localhost:7687}")
    private String uri;

    @Value("${neo4j.username:neo4j}")
    private String username;

    @Value("${neo4j.password:password123}")
    private String password;

    @Bean
    public Driver neo4jDriver() {
        try {
            Config config = Config.builder()
                    .withConnectionTimeout(10, TimeUnit.SECONDS)
                    .withMaxConnectionPoolSize(20)
                    .build();

            Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password), config);
            // 快速验证连接
            driver.verifyConnectivity();
            log.info("Neo4j 连接成功: {}", uri);
            return driver;
        } catch (Exception e) {
            log.warn("Neo4j 连接失败 ({}): {} —— 图谱功能将降级", uri, e.getMessage());
            return null;
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```powershell
.\mvnw.cmd clean compile -pl edumate-admin -am -q
```

预期：BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add edumate-admin/src/main/java/com/edumate/admin/config/Neo4jConfig.java
git commit -m "feat: add Neo4jConfig for native Neo4j Java Driver bean"
```

---

## Task 8: 端到端验证

**Files:**
- 无新建文件

- [ ] **Step 1: 确保 Docker 依赖服务已启动**

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

# Neo4j
curl http://localhost:7474

# Redis
docker exec edumate-redis redis-cli ping
```

预期：全部返回正常响应。

- [ ] **Step 3: 启动 Spring Boot 应用**

在 IDE 中运行 `EduMateApplication`，或命令行：

```powershell
.\mvnw.cmd -pl edumate-admin spring-boot:run
```

- [ ] **Step 4: 上传测试文档**

```powershell
curl.exe -X POST http://localhost:8080/api/documents/upload ^
  -F "file=@f:\JetBrains\RAG\EduMate\edumate-core\src\test\resources\test-document.txt" ^
  -F "courseName=数据结构" ^
  -F "semester=2026-春"
```

预期：返回 JSON，含 `chunkCount` > 0，且日志中出现 "知识抽取完成" 和 "图谱存储完成"。

- [ ] **Step 5: 执行搜索**

```powershell
curl.exe -X POST http://localhost:8080/api/search ^
  -H "Content-Type: application/json" ^
  -d "{\"query\":\"二叉树\",\"topK\":3}"
```

预期：返回 JSON，含 `results` 数组，结果包含三路融合后的内容。

- [ ] **Step 6: 验证 Neo4j 中的图谱数据**

```powershell
# 通过 Neo4j Browser 验证（浏览器打开 http://localhost:7474）
# 或通过 curl 验证
curl.exe -X POST http://localhost:7474/db/neo4j/tx/commit ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Basic bmVvNGo6cGFzc3dvcmQxMjM=" ^
  -d "{\"statements\":[{\"statement\":\"MATCH (n:KnowledgePoint) RETURN n LIMIT 10\"}]}"
```

预期：返回包含 KnowledgePoint 节点的 JSON。

- [ ] **Step 7: 停止 Docker 服务**

```powershell
docker compose down
```

---

## 验收检查清单

执行完所有 Task 后，逐项确认：

- [ ] `mvn clean compile` 全量编译通过
- [ ] `mvn test` 全量测试通过（新增 KnowledgeExtractionServiceTest、KnowledgeGraphServiceTest、GraphSearchServiceTest、RRFusionServiceTest 三路融合测试）
- [ ] `docker compose up -d` 能启动 Qdrant/ES/Neo4j/Redis 四个服务
- [ ] Neo4j 连接验证通过（`curl http://localhost:7474`）
- [ ] `POST /api/documents/upload` 上传文档后：
  - [ ] 文档解析与切分正常
  - [ ] 向量索引到 Qdrant 正常
  - [ ] 关键词索引到 ES 正常
  - [ ] 知识抽取成功（日志中出现 "知识抽取完成"）
  - [ ] 图谱存储成功（日志中出现 "图谱存储完成"）
- [ ] Neo4j 中存在 KnowledgePoint 节点和关系
- [ ] `POST /api/search` 返回三路 RRF 融合后的检索结果
- [ ] 当 Neo4j 未启动时，应用正常启动，图谱功能降级（不报错）
- [ ] 当 DASHSCOPE_API_KEY 未配置时，知识抽取降级（不报错）
- [ ] 项目结构符合预期（edumate-core 新增 `graph/` 包，edumate-common 新增 KnowledgePoint/KnowledgeRelation）

---

## Phase 3 完成后的项目结构

```
EduMate/
├── pom.xml                               # 修改：添加 Neo4j Driver 版本管理
├── compose.yaml
├── .env.example
│
├── edumate-common/
│   └── src/main/java/com/edumate/common/
│       ├── model/
│       │   ├── DocumentChunk.java
│       │   ├── CourseMetadata.java
│       │   ├── KnowledgePoint.java       # 新增：图谱节点 DTO
│       │   └── KnowledgeRelation.java    # 新增：图谱关系 DTO
│       └── enums/
│           ├── DocumentType.java
│           └── KnowledgeRelationType.java
│
├── edumate-core/
│   ├── pom.xml                           # 修改：添加 Neo4j Driver 依赖
│   └── src/
│       ├── main/java/com/edumate/core/
│       │   ├── parser/
│       │   │   ├── DocumentParserService.java
│       │   │   └── HierarchicalChunkerService.java
│       │   ├── retrieval/
│       │   │   ├── VectorStoreService.java
│       │   │   ├── KeywordIndexService.java
│       │   │   ├── HybridSearchService.java   # 修改：编排三路检索
│       │   │   └── RRFusionService.java       # 修改：支持三路融合
│       │   └── graph/                         # 新增
│       │       ├── KnowledgeExtractionService.java  # LLM 知识抽取
│       │       ├── KnowledgeGraphService.java       # Neo4j 存储
│       │       └── GraphSearchService.java          # 图谱检索
│       └── test/java/com/edumate/core/
│           ├── parser/
│           │   ├── DocumentParserServiceTest.java
│           │   └── HierarchicalChunkerServiceTest.java
│           ├── retrieval/
│           │   ├── VectorStoreServiceTest.java
│           │   ├── KeywordIndexServiceTest.java
│           │   └── RRFusionServiceTest.java        # 修改：新增三路融合测试
│           └── graph/                              # 新增
│               ├── KnowledgeExtractionServiceTest.java
│               ├── KnowledgeGraphServiceTest.java
│               └── GraphSearchServiceTest.java
│
└── edumate-admin/
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/edumate/admin/
        │   │   ├── EduMateApplication.java
        │   │   ├── config/
        │   │   │   ├── QdrantConfig.java
        │   │   │   ├── ElasticsearchConfig.java
        │   │   │   ├── DashScopeConfig.java
        │   │   │   └── Neo4jConfig.java            # 新增：Neo4j Driver Bean
        │   │   └── controller/
        │   │       ├── DocumentController.java     # 修改：上传后触发图谱抽取
        │   │       └── SearchController.java
        │   └── resources/
        │       └── application.yml                 # 修改：添加 Neo4j 配置
        └── test/java/com/edumate/admin/
            ├── EduMateApplicationTests.java
            └── controller/
                ├── DocumentControllerTest.java
                └── SearchControllerTest.java
```