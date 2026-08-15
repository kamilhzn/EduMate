# EduMate — 基于 RAG 与知识图谱的课程学习智能助手

> **面向大学生实习求职场景的智能课程学习助手**：支持多门课程资料上传、精准问答、跨课程知识图谱、智能出题，并内置完整评测体系与全链路追踪能力。

[![JDK](https://img.shields.io/badge/JDK-21-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-brightgreen)](https://spring.io/projects/spring-boot)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-1.17.2-blue)](https://docs.langchain4j.dev/)
[![Vue](https://img.shields.io/badge/Vue-3.5-green)](https://vuejs.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-red)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)

---

## 一分钟了解（TL;DR）

| 维度 | 说明 |
|------|------|
| **是什么** | 基于 **RAG + 知识图谱** 的课程学习问答/出题系统，前后端分离 |
| **后端** | Java 21 · Spring Boot 3.4.5 · LangChain4j 1.17.2 · Maven 多模块 |
| **前端** | Vue 3 · Vite · Element Plus · Pinia（端口 3000） |
| **LLM** | 通义千问 DashScope（`qwen-plus` 对话 / `text-embedding-v3` 向量） |
| **数据存储** | Qdrant（向量）· Elasticsearch（关键词）· Neo4j（知识图谱）· Redis（会话） |
| **核心特性** | 三路混合检索（向量+关键词+图谱，RRF 融合）、智能出题 Agent（ReAct）、LLM-as-Judge 评测、全链路 Trace |
| **部署方式** | Docker Compose 一键起依赖 + Maven Wrapper 编译运行 |
| **License** | Apache 2.0 |

> 给 AI 助手 / 快速上手的开发者：本项目是经典 **RAG 系统** 的教学级实现，亮点在"混合检索 + 跨课程知识图谱 + 完整评测链路"。代码入口：`edumate-admin`（启动/接口）→ `edumate-core`（业务逻辑）→ `edumate-common`（数据模型）。

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
│                         API Layer (edumate-admin)                  │
│  DocumentController │ ChatController │ SearchController            │
│  QuizController     │ EvaluationController │ CourseController      │
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
| **LLM** | 通义千问 DashScope | qwen-plus（可配） | 问答生成、知识抽取、意图识别、出题 |
| **Embedding** | 通义千问 | text-embedding-v3 | 文本向量化（1024 维） |
| **向量数据库** | Qdrant | latest | 向量相似度检索（Cosine 距离） |
| **关键词检索** | Elasticsearch | 8.15.0 | BM25 全文检索 |
| **图数据库** | Neo4j | 5-enterprise | 知识图谱存储与多跳推理 |
| **缓存/会话** | Redis | 7-alpine | 多轮对话会话管理 |
| **文档解析** | PDFBox / POI | 3.0.4 / 5.4.0 | PDF/PPTX/DOCX 文本提取 |
| **前端** | Vue 3 / Vite / Element Plus | 3.5 / 6.3 / 2.9 | 管理界面（端口 3000） |
| **构建工具** | Maven | 3.9+ | 多模块构建、依赖管理 |

---

## 三、项目结构

```
EduMate/
├── pom.xml                              # 父 POM（3 个后端子模块，统一版本管理）
├── compose.yaml                         # Docker Compose（Qdrant/ES/Neo4j/Redis）
├── .env.example                        # 环境变量模板（占位符，勿填真实密钥）
├── README.md                           # 项目说明文档
├── mvnw.cmd                           # Maven Wrapper（无需安装 Maven）
│
├── edumate-common/                     # 通用模块：13 个数据模型 + 2 个枚举（纯 POJO）
│   └── src/main/java/com/edumate/common/
│       ├── model/                      # DocumentChunk / CourseMetadata / ChatMessage /
│       │                               # KnowledgePoint / KnowledgeRelation / Quiz /
│       │                               # QuizQuestion / EvaluationSample / EvaluationResult /
│       │                               # TraceRecord / Chapter / Section / Course
│       └── enums/                      # DocumentType / KnowledgeRelationType
│
├── edumate-core/                       # 核心业务模块（8 个包，21 个类）
│   └── src/main/java/com/edumate/core/
│       ├── parser/                     # DocumentParserService / HierarchicalChunkerService
│       ├── retrieval/                  # VectorStoreService / KeywordIndexService /
│       │                               # HybridSearchService / RRFusionService
│       ├── graph/                      # KnowledgeGraphService / GraphSearchService
│       ├── agent/                      # IntentClassifierService / QueryRewriteService /
│       │                               # QuizAgentService / RefusalGuardService
│       ├── chat/                       # ChatSessionService / StreamingChatService
│       ├── course/                     # CourseService / ChapterService
│       ├── quiz/                       # QuizService（内存题库管理）
│       └── evaluation/                 # EvaluationMetricsService / EvaluationOrchestrator /
│                                       # RagasEvaluationService / TraceService
│
├── edumate-admin/                      # 启动模块：6 个 Controller + 5 个 Config
│   └── src/main/java/com/edumate/admin/
│       ├── EduMateApplication.java     # Spring Boot 启动类
│       ├── config/                     # CorsConfig / QdrantConfig / ElasticsearchConfig /
│       │                               # Neo4jConfig / RedisConfig
│       ├── controller/                 # DocumentController / ChatController / SearchController /
│       │                               # QuizController / EvaluationController / CourseController
│       └── service/                    # KnowledgeExtractionService（LLM 知识抽取）
│
└── edumate-frontend/                  # Vue 3 前端（Vite 端口 3000，/api 代理到 8080）
    └── src / public / vite.config.js / package.json
```

> 注：`docs/` 目录为本地开发计划文档，已被 `.gitignore` 排除，不随仓库分发。

---

## 四、API 端点

后端默认端口 `8080`，前端开发服务器（`3000`）会将 `/api` 代理到后端。

### 文档 & 检索

| 方法 | 端点 | 功能 | 说明 |
|------|------|------|------|
| `POST` | `/api/documents/upload` | 文档上传 | 流水线：解析→切分→向量化→关键词索引→知识抽取→图谱存储 |
| `POST` | `/api/chat/stream` | SSE 流式问答 | 拒答检查→会话保存→Query 改写→混合检索→上下文拼接→流式生成 |
| `POST` | `/api/search` | 混合检索 | 向量+关键词+图谱三路并行→RRF 融合排序 |

### 课程管理

| 方法 | 端点 | 功能 |
|------|------|------|
| `GET` | `/api/courses` | 课程列表 |
| `GET` | `/api/courses/{id}` | 课程详情 |
| `POST` | `/api/courses` | 创建课程（body: `{"name": "..."}`） |
| `GET` | `/api/courses/{courseId}/chapters` | 课程章节列表 |
| `GET` | `/api/courses/{courseId}/chapters/{chapterId}/sections/{sectionId}` | 小节内容 |
| `GET` | `/api/courses/{courseId}/sections/{sectionId}` | 小节内容（兼容旧版 URL） |

### 智能出题

| 方法 | 端点 | 功能 | 说明 |
|------|------|------|------|
| `POST` | `/api/quiz/generate` | 生成并保存题库 | body: `{"courseName","chapter","section","count","difficulty"}`；题型 单选30%/多选20%/填空20%/简答30% |
| `GET` | `/api/quizzes` | 题库列表 | |
| `DELETE` | `/api/quizzes/{id}` | 删除题库 | |

### 评测 & 追踪

| 方法 | 端点 | 功能 | 说明 |
|------|------|------|------|
| `POST` | `/api/eval/retrieval` | 检索评测 | 默认加载内置 50 条标注样本，或传自定义样本；输出 Recall@K / MRR / NDCG |
| `GET` | `/api/eval/trace/{traceId}` | 单条 Trace 查询 | 查看某次查询全链路各阶段耗时 |
| `GET` | `/api/eval/traces?count=10` | 最近 N 条 Trace | 默认 10 条，最大 50 |

---

## 五、核心业务流程

### 文档上传流程

```
上传文件 → 格式校验 → 临时保存
  → DocumentParserService.parse()           # PDFBox/POI 提取文本
  → HierarchicalChunkerService.chunk()      # 按"章→节→小节"层级切分（2000 字上限，200 字重叠）
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
  → 返回 sessionId
```

### 智能出题流程（ReAct 模式）

```
QuizAgentService.generate()
  → Step 1 - Plan（规划）：内置 Prompt 确定出题范围、题型分布（单选30%/多选20%/填空20%/简答30%）
  → Step 2 - Execute（执行）：LLM 生成 JSON 格式题目（题干、选项、答案、解析）
  → Step 3 - Verify（验证）：过滤题干为空或答案为空的不合格题目
  → 生成题库并保存（QuizService 内存存储）
  → 返回 Quiz（含题目列表）
```

### 评测流程

```
EvaluationOrchestrator.runRetrievalEvaluation()
  → 加载 eval-dataset.json（内置 50 条标注样本）或请求传入的自定义样本
  → 对每条样本执行 HybridSearchService.search()
  → 逐条计算 Recall@K / Reciprocal Rank
  → EvaluationMetricsService 汇总计算：
      - Recall@K（K=1,3,5,10）
      - MRR（Mean Reciprocal Rank）
      - NDCG@10（Normalized Discounted Cumulative Gain）
  → 返回 EvaluationResult（含逐样本明细）
```

---

## 六、设计亮点

### 1. 降级设计

所有外部服务均使用 `@Nullable` 注入，当服务不可用时优雅降级：

| 服务不可用 | 降级行为 |
|-----------|----------|
| ChatModel（LLM） | 知识抽取/出题/意图识别/Query 改写返回空或默认值 |
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
- 查询最近 N 条：`GET /api/eval/traces?count=N`

---

## 七、快速开始

### 前置条件

- JDK 21+
- Maven 3.9+（或使用仓库自带 Maven Wrapper `mvnw.cmd`）
- Node.js 18+（仅前端需要）
- Docker（用于启动 Qdrant / ES / Neo4j / Redis）
- 通义千问 API Key（[DashScope 控制台](https://dashscope.console.aliyun.com/) 获取）

### 1. 克隆项目

```bash
git clone https://github.com/kamilhzn/EduMate.git
cd EduMate
```

### 2. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env，将 DASHSCOPE_API_KEY 替换为你的真实 Key
```

> ⚠️ **安全提示**：`.env` 已被 `.gitignore` 忽略，请勿提交真实密钥。`.env.example` 仅作模板，保留占位符。

### 3. 启动依赖服务

```bash
docker compose up -d
```

### 4. 编译运行后端

```bash
# 编译（使用 Maven Wrapper）
mvnw.cmd clean install -DskipTests

# 运行测试
mvnw.cmd test

# 启动应用（默认端口 8080）
mvnw.cmd -pl edumate-admin spring-boot:run
```

或在 IDEA 中直接运行 `EduMateApplication`。

### 5. 启动前端（可选）

```bash
cd edumate-frontend
npm install
npm run dev        # http://localhost:3000
```

### 6. 测试 API

```bash
# 上传文档
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@数据结构笔记.txt" \
  -F "courseName=数据结构" \
  -F "semester=2026-春"

# 创建课程
curl -X POST http://localhost:8080/api/courses \
  -H "Content-Type: application/json" \
  -d '{"name":"数据结构"}'

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

# 运行检索评测
curl -X POST http://localhost:8080/api/eval/retrieval \
  -H "Content-Type: application/json" \
  -d '{}'
```

---

## 八、测试

项目包含 **21 个测试类**（JUnit 5 + Mockito + AssertJ）：

- `edumate-core`（13 个）：agent / chat / course / evaluation / parser / quiz 各 Service 单元测试
- `edumate-admin`（8 个）：6 个 Controller 测试 + `KnowledgeExtractionService` + 上下文加载测试
- 评测数据：`edumate-admin/src/test/resources/eval/eval-dataset.json`

```bash
mvnw.cmd test
```

---

## 九、环境变量总览

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DASHSCOPE_API_KEY` | （必填） | 通义千问 API Key |
| `DASHSCOPE_CHAT_MODEL` | `qwen-plus` | 对话模型名 |
| `DASHSCOPE_EMBEDDING_MODEL` | `text-embedding-v3` | 向量模型名 |
| `QDRANT_HOST` / `QDRANT_GRPC_PORT` / `QDRANT_HTTP_PORT` | `localhost` / `6334` / `6333` | Qdrant 连接 |
| `ES_HOST` / `ES_PORT` | `localhost` / `9200` | Elasticsearch 连接 |
| `NEO4J_URI` / `NEO4J_USERNAME` / `NEO4J_PASSWORD` | `bolt://localhost:7687` / `neo4j` / `password123` | Neo4j 连接 |
| `REDIS_HOST` / `REDIS_PORT` | `127.0.0.1` / `6379` | Redis 连接 |

---

## 十、License

本项目基于 [Apache License 2.0](LICENSE) 开源。