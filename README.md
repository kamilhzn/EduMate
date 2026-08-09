# EduMate — 基于 RAG 与知识图谱的课程学习智能助手

> **面向大学生实习求职场景的智能课程学习助手，支持多门课程资料上传、精准问答、跨课程知识图谱与智能出题，具备完整的评测体系和全链路追踪能力。**

[![JDK](https://img.shields.io/badge/JDK-21-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-brightgreen)](https://spring.io/projects/spring-boot)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-1.17.2-blue)](https://docs.langchain4j.dev/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-red)](https://maven.apache.org/)

---

## 一、项目简介

| 痛点 | 解决方案 | 涉及技术 |
|------|----------|----------|
| 知识点分散（PPT/教材/习题散落各处） | 统一上传 + 层级感知解析，自动结构化 | PDFBox, POI, 层级切分 |
| 单一检索方式召回不全 | 向量 + 关键词 + 图谱三路混合检索 | Qdrant, Elasticsearch, Neo4j |
| 跨课程关联弱（如"数据结构"的 B+ 树与"数据库原理"的索引） | 基于 Neo4j 的跨课程知识图谱多跳推理 | LLM 知识抽取, Cypher 图查询 |
| 复习效率低（被动阅读，缺乏反馈） | 智能出题 Agent + ReAct 模式 | LangChain4j ChatModel, Few-shot Prompt |
| 答疑资源有限（教师/助教时间有限） | RAG 检索增强问答，SSE 流式响应 | LangChain4j, RRF 融合 |
| 缺乏质量评估手段 | 完整评测体系（检索 + 生成质量） | Recall@K, MRR, NDCG, RAGAS |
| 系统可观测性差 | 全链路 Trace 追踪 | 内存级 Trace 记录, 各阶段耗时统计 |

---

## 二、技术架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         API Layer (Spring Boot)                   │
│  DocumentController │ ChatController │ SearchController           │
│  QuizController     │ EvaluationController                        │
├─────────────────────────────────────────────────────────────────┤
│                      Business Layer (edumate-core)                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────────┐ │
│  │  Parser  │  │Retrieval │  │  Graph   │  │  Agent & Chat    │ │
│  │          │  │          │  │          │  │                  │ │
│  │ PDFBox   │  │ Qdrant   │  │ Neo4j    │  │ IntentClassifier │ │
│  │ POI      │  │ ES BM25  │  │ Cypher   │  │ QueryRewrite     │ │
│  │ Chunker  │  │ RRFusion │  │ LLM抽取  │  │ QuizAgent(ReAct) │ │
│  └──────────┘  └──────────┘  └──────────┘  │ RefusalGuard     │ │
│                                             │ ChatSession      │ │
│  ┌──────────────────────────────────────┐   └──────────────────┘ │
│  │         Evaluation & Trace           │                        │
│  │  Recall@K / MRR / NDCG / RAGAS / Trace                       │
│  └──────────────────────────────────────┘                        │
├─────────────────────────────────────────────────────────────────┤
│                      Data Layer (Containerized)                   │
│  Qdrant (向量)  │  Elasticsearch (关键词)  │  Neo4j (图谱)        │
│  Redis (会话)   │  DashScope (LLM/Embedding)                     │
└─────────────────────────────────────────────────────────────────┘
```

### 技术栈

| 层级 | 技术 | 版本 | 用途 |
|------|------|------|------|
| **JDK** | Java SE | 21 | 运行环境（LTS） |
| **核心框架** | Spring Boot | 3.4.5 | IoC 容器、Web 服务、自动配置 |
| **AI 框架** | LangChain4j | 1.17.2-beta27 | ChatModel、EmbeddingModel、EmbeddingStore 抽象 |
| **LLM** | 通义千问 DashScope | qwen3.7-plus | 问答生成、知识抽取、意图识别、出题 |
| **Embedding** | 通义千问 | text-embedding-v3 | 文本向量化（1024维） |
| **向量数据库** | Qdrant | latest | 向量相似度检索（Cosine 距离） |
| **关键词检索** | Elasticsearch | 8.15.0 | BM25 全文检索 |
| **图数据库** | Neo4j | 5-enterprise | 知识图谱存储与多跳推理 |
| **缓存/会话** | Redis | 7-alpine | 多轮对话会话管理 |
| **文档解析** | PDFBox / POI | 3.0.4 / 5.4.0 | PDF/PPTX/DOCX 文本提取 |
| **构建工具** | Maven | 3.9+ | 多模块构建、依赖管理 |

---

## 三、项目结构

```
EduMate/
├── pom.xml                              # 父 POM（3 子模块，统一版本管理）
├── compose.yaml                         # Docker Compose（Qdrant/ES/Neo4j/Redis）
├── .env.example                         # 环境变量模板（DASHSCOPE_API_KEY）
├── README.md                            # 项目说明文档
├── mvnw.cmd                             # Maven Wrapper（无需安装 Maven）
│
├── docs/
│   └── superpowers/plans/               # 5 个 Phase 开发计划文档
│
├── edumate-common/                      # 通用模块：11 个数据模型 + 2 个枚举
│   └── src/main/java/com/edumate/common/
│       ├── model/
│       │   ├── DocumentChunk.java       # RAG 检索最小单元
│       │   ├── CourseMetadata.java      # 课程元数据
│       │   ├── ChatMessage.java         # 对话消息
│       │   ├── KnowledgePoint.java      # 知识图谱节点
│       │   ├── KnowledgeRelation.java   # 知识图谱关系（边）
│       │   ├── QuizQuestion.java        # 题目模型
│       │   ├── EvaluationSample.java    # 评测样本（Ground Truth）
│       │   ├── EvaluationResult.java    # 评测结果汇总
│       │   └── TraceRecord.java         # 全链路追踪记录
│       └── enums/
│           ├── DocumentType.java        # 文档类型（PDF/PPTX/DOCX/TXT/MD）
│           └── KnowledgeRelationType.java  # 关系类型（PREREQUISITE/SUCCESSOR/...）
│
├── edumate-core/                        # 核心业务模块：18 个 Service
│   └── src/main/java/com/edumate/core/
│       ├── parser/
│       │   ├── DocumentParserService.java         # 多格式文档解析
│       │   └── HierarchicalChunkerService.java    # 层级感知切分（章→节→小节）
│       ├── retrieval/
│       │   ├── VectorStoreService.java            # 向量化入库 Qdrant
│       │   ├── KeywordIndexService.java           # ES BM25 关键词索引
│       │   ├── HybridSearchService.java           # 混合检索编排（向量+关键词+图谱）
│       │   └── RRFusionService.java               # RRF 倒数排序融合
│       ├── graph/
│       │   ├── KnowledgeGraphService.java         # Neo4j 知识点存储（Cypher MERGE）
│       │   └── GraphSearchService.java            # 图谱检索（名称匹配+多跳遍历）
│       ├── agent/
│       │   ├── IntentClassifierService.java       # 意图识别（4 分类）
│       │   ├── QueryRewriteService.java           # Query 改写（口语→检索优化）
│       │   ├── QuizAgentService.java              # 智能出题 Agent（ReAct 模式）
│       │   └── RefusalGuardService.java           # 两层拒答守卫（关键词+LLM）
│       ├── chat/
│       │   ├── ChatSessionService.java            # Redis 多轮对话会话管理
│       │   └── StreamingChatService.java          # 流式问答全流程编排
│       └── evaluation/
│           ├── EvaluationMetricsService.java      # 检索指标计算（Recall@K/MRR/NDCG）
│           ├── EvaluationOrchestrator.java        # 评测编排器
│           ├── RagasEvaluationService.java        # RAGAS 端到端评测（LLM-as-Judge）
│           └── TraceService.java                  # 全链路 Trace 追踪
│
└── edumate-admin/                       # 启动模块：5 个 Controller + 5 个 Config
    └── src/main/java/com/edumate/admin/
        ├── EduMateApplication.java               # Spring Boot 启动类
        ├── config/
        │   ├── DashScopeConfig.java              # 通义千问 ChatModel / EmbeddingModel
        │   ├── QdrantConfig.java                 # Qdrant EmbeddingStore + Collection 自动创建
        │   ├── ElasticsearchConfig.java          # ES Java Client
        │   ├── Neo4jConfig.java                  # Neo4j Driver（可降级为 null）
        │   └── RedisConfig.java                  # RedisTemplate（Jackson JSON 序列化）
        ├── controller/
        │   ├── DocumentController.java           # POST /api/documents/upload
        │   ├── ChatController.java               # POST /api/chat/stream（SSE 流式）
        │   ├── SearchController.java             # POST /api/search
        │   ├── QuizController.java               # POST /api/quiz/generate
        │   └── EvaluationController.java         # POST /api/eval/retrieval, GET /api/eval/trace/{id}
        └── service/
            └── KnowledgeExtractionService.java    # LLM 知识抽取（分块→知识点+关系）
```

---

## 四、API 端点

| 方法 | 端点 | 功能 | 说明 |
|------|------|------|------|
| `POST` | `/api/documents/upload` | 文档上传 | 完整流水线：解析→切分→向量化→关键词索引→知识抽取→图谱存储 |
| `POST` | `/api/chat/stream` | SSE 流式问答 | 拒答检查→会话保存→Query改写→混合检索→上下文拼接→流式生成 |
| `POST` | `/api/search` | 混合检索 | 向量+关键词+图谱三路并行→RRF融合排序 |
| `POST` | `/api/quiz/generate` | 智能出题 | ReAct模式（规划→生成→验证），题型比例 40%/20%/40% |
| `POST` | `/api/eval/retrieval` | 检索评测 | 加载50条标注样本，计算 Recall@K / MRR / NDCG |
| `GET` | `/api/eval/trace/{traceId}` | 单条Trace查询 | 查看某次查询的全链路各阶段耗时 |
| `GET` | `/api/eval/traces` | 最近N条Trace | 查看最近的查询追踪记录 |

---

## 五、核心业务流程

### 文档上传流程

```
上传文件 → 格式校验 → 临时保存
  → DocumentParserService.parse()           # PDFBox/POI 提取文本
  → HierarchicalChunkerService.chunk()      # 按"章→节→小节"层级切分（2000字上限，200字重叠）
  → VectorStoreService.indexChunks()        # Embedding 向量化 → Qdrant 存储
  → KeywordIndexService.indexChunks()       # ES BM25 关键词索引
  → KnowledgeExtractionService.extract()    # LLM 提取知识点 + 关系
  → KnowledgeGraphService.savePoints()      # Neo4j 图谱存储（MERGE upsert）
  → 返回 JSON（分块列表 + 知识点列表）
```

### 问答流程

```
SSE 连接建立
  → RefusalGuardService.shouldRefuse()      # 两层拒答：关键词规则 + LLM 判断
  → ChatSessionService.addMessage()         # 保存用户消息到 Redis
  → QueryRewriteService.rewrite()           # LLM 将口语化查询改写为检索优化查询
  → HybridSearchService.search()            # 三路并行检索 + RRF 融合
      ├── VectorStoreService.search()       # Qdrant 向量检索（Cosine 相似度）
      ├── KeywordIndexService.search()      # ES BM25 关键词检索
      └── GraphSearchService.search()       # Neo4j 图谱多跳检索
  → 拼接上下文 + 对话历史 + System Prompt
  → ChatModel.chat()                        # LLM 基于上下文生成答案
  → 模拟逐 token 推送 SSE
  → ChatSessionService.addMessage()         # 保存助手回复到 Redis
  → 返回 sessionId
```

### 智能出题流程（ReAct 模式）

```
QuizAgentService.generate()
  → Step 1 - Plan（规划）：内置 Prompt 确定出题范围、题型分布
  → Step 2 - Execute（执行）：LLM 生成 JSON 格式题目（含题干、选项、答案、解析）
  → Step 3 - Verify（验证）：过滤题干为空或答案为空的不合格题目
  → 返回 QuizQuestion 列表
```

### 评测流程

```
EvaluationOrchestrator.runRetrievalEvaluation()
  → 加载 eval-dataset.json（50 条标注样本）
  → 对每条样本执行 HybridSearchService.search()
  → 逐条计算 Recall@K / Reciprocal Rank
  → EvaluationMetricsService 汇总计算：
      - Recall@K（K=1,3,5,10）
      - MRR（Mean Reciprocal Rank）
      - NDCG@10（Normalized Discounted Cumulative Gain）
  → 返回 EvaluationResult（含逐样本明细）
```

---

## 六、开发阶段

| Phase | 状态 | 内容 |
|-------|------|------|
| **Phase 1** | ✅ 已完成 | 多模块架构、数据模型、文档解析、层级切分、LangChain4j 集成、Docker Compose |
| **Phase 2** | ✅ 已完成 | Qdrant 向量化、ES 关键词索引、混合检索 Pipeline、RRF 融合 |
| **Phase 3** | ✅ 已完成 | Neo4j Schema、LLM 知识抽取、图谱检索集成 |
| **Phase 4** | ✅ 已完成 | 智能出题 Agent、意图识别、Query 改写、拒答守卫、多轮对话、SSE 流式 |
| **Phase 5** | ✅ 已完成 | 50 条评测数据集、Recall@K/MRR/NDCG、RAGAS 评测、全链路 Trace 追踪 |
| **Phase 6** | ⏳ 待开发 | Vue 3 前端、Docker 一键部署 |

---

## 七、设计亮点

### 1. 降级设计

所有外部服务均使用 `@Nullable` 注入，当服务不可用时优雅降级：

| 服务不可用 | 降级行为 |
|-----------|----------|
| ChatModel（LLM） | 知识抽取/出题/意图识别/Query改写返回空或默认值 |
| EmbeddingModel | 向量索引/检索跳过 |
| Elasticsearch Client | 关键词索引/检索返回空，混合检索仅用向量路径 |
| Neo4j Driver | 图谱存储/检索返回空，混合检索仅用向量+关键词路径 |
| Redis | 会话历史返回空列表 |

### 2. RRF 倒数排序融合

三路检索结果通过 RRF（Reciprocal Rank Fusion）统一排序，而非简单拼接：

```
RRF_score(d) = Σ 1 / (k + rank_i(d))    # k=60
```

同一文档在向量、关键词、图谱三条路径中都有高排名时，融合分更高，排序更靠前。

### 3. 层级感知切分

文档切分按"章→节→小节"标题层级组织，保留父块关联（`parentChunkId`），支持检索时展开上下文。

### 4. LLM-as-Judge 评测

RAGAS 评测使用 LLM 作为评判者，评估三个维度：
- **Answer Relevance**：答案与问题的相关性
- **Faithfulness**：答案是否忠实于检索上下文（防幻觉）
- **Context Relevance**：检索到的上下文与问题的相关性

### 5. 全链路 Trace

每次查询记录经过各阶段的输入、输出、耗时、成功/失败状态，支持：
- 查询单条 Trace：`GET /api/eval/trace/{traceId}`
- 查询最近 N 条：`GET /api/eval/traces`

---

## 八、快速开始

### 前置条件

- JDK 21+
- Docker（用于启动 Qdrant / ES / Neo4j / Redis）
- 通义千问 API Key（[DashScope 控制台](https://dashscope.console.aliyun.com/) 获取）

### 1. 克隆项目

```bash
git clone <repo-url>
cd EduMate
```

### 2. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env，填入你的 DASHSCOPE_API_KEY
```

### 3. 启动依赖服务

```bash
docker compose up -d
```

### 4. 编译运行

```bash
# 编译（使用 Maven Wrapper，无需安装 Maven）
mvnw.cmd clean install -DskipTests

# 运行测试
mvnw.cmd test

# 启动应用
mvnw.cmd -pl edumate-admin spring-boot:run
```

### 5. 测试 API

```bash
# 上传文档
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@数据结构笔记.txt" \
  -F "courseName=数据结构" \
  -F "semester=2026-春"

# 流式问答
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"query":"什么是红黑树？有哪些性质？"}'

# 混合检索
curl -X POST http://localhost:8080/api/search \
  -H "Content-Type: application/json" \
  -d '{"query":"B+树索引","topK":5}'

# 智能出题
curl -X POST http://localhost:8080/api/quiz/generate \
  -H "Content-Type: application/json" \
  -d '{"courseName":"数据结构","count":5,"difficulty":"medium"}'

# 运行评测
curl -X POST http://localhost:8080/api/eval/retrieval \
  -H "Content-Type: application/json" \
  -d '{}'
```

---

## 九、开发路线

```
Phase 1 ✅          Phase 2 ✅          Phase 3 ✅          Phase 4 ✅          Phase 5 ✅          Phase 6 ⏳
基础骨架           检索能力           知识图谱           Agent能力          评测体系           前端与部署
──────┼───────────────┼──────────────────┼───────────────────┼───────────────────┼───────────────────┼──────►
多模块架构         向量化+Qdrant      Neo4j Schema       Agent 出题          评测数据集          Vue 3 前端
文档解析           ES 关键词索引      LLM 知识抽取       意图识别             Recall@K/MRR        Docker 部署
层级切分           混合检索Pipeline   图谱检索集成        Query 改写           RAGAS 评测
LangChain4j        RRF 融合                            拒答守卫             Trace 追踪
Docker Compose                                        多轮对话+SSE
```

---

## 十、参考项目

- [RuoYi-RAG](https://github.com/zhaoshibao/ruoyi-rag) — Spring AI 框架的 RAG 知识库系统
- [LangChain4j](https://docs.langchain4j.dev/) — Java 生态的 LLM 应用开发框架
- [Qdrant](https://qdrant.tech/) — 高性能向量数据库
- [RAGAS](https://docs.ragas.io/) — RAG 系统评测框架