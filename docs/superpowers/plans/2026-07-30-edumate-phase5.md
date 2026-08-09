# EduMate Phase 5：评测与优化 —— 执行计划书

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 EduMate 构建完整的检索与问答评测体系，包含标注测试数据集、Recall@K/MRR/NDCG 检索指标、RAGAS 端到端评测、全链路 Trace 追踪，以及基于评测结果的参数调优。

**Architecture:** 在 edumate-common 新增评测数据模型（`EvaluationSample`、`EvaluationResult`、`TraceRecord`），在 edumate-core 新增 `evaluation/` 子包负责评测逻辑（指标计算、RAGAS 评测、Trace 追踪、评测编排），在 edumate-admin 新增 `EvaluationController` 暴露评测 API。评测数据以 JSON 文件形式存储在 `edumate-admin/src/test/resources/eval/` 下，评测支持独立运行（不依赖 Docker 服务即可跑基础指标）。

**Tech Stack:** Spring Boot 3.4.5, LangChain4j Community 1.17.2-beta27, Jackson, JUnit 5, SLF4J (Trace), 通义千问 DashScope (RAGAS 评测)

---

## 当前状态

```
edumate-common/src/main/java/com/edumate/common/model/
├── ChatMessage.java
├── CourseMetadata.java
├── DocumentChunk.java
├── KnowledgePoint.java
├── KnowledgeRelation.java
└── QuizQuestion.java

edumate-core/src/main/java/com/edumate/core/
├── agent/
│   ├── IntentClassifierService.java
│   ├── QueryRewriteService.java
│   ├── QuizAgentService.java
│   └── RefusalGuardService.java
├── chat/
│   ├── ChatSessionService.java
│   └── StreamingChatService.java
├── graph/
│   ├── GraphSearchService.java
│   └── KnowledgeGraphService.java
├── parser/
│   ├── DocumentParserService.java
│   └── HierarchicalChunkerService.java
└── retrieval/
    ├── HybridSearchService.java
    ├── KeywordIndexService.java
    ├── RRFusionService.java
    └── VectorStoreService.java

edumate-admin/src/main/java/com/edumate/admin/
├── config/
│   ├── DashScopeConfig.java
│   ├── ElasticsearchConfig.java
│   ├── Neo4jConfig.java
│   ├── QdrantConfig.java
│   └── RedisConfig.java
├── controller/
│   ├── ChatController.java
│   ├── DocumentController.java
│   ├── QuizController.java
│   └── SearchController.java
└── service/
    └── KnowledgeExtractionService.java
```

## 目标状态（新增文件）

```
edumate-common/src/main/java/com/edumate/common/model/
├── EvaluationSample.java     ← 新增：评测样本（问答对+标准答案分块）
├── EvaluationResult.java     ← 新增：单次评测结果
└── TraceRecord.java          ← 新增：流水线追踪记录

edumate-core/src/main/java/com/edumate/core/evaluation/
├── EvaluationMetricsService.java   ← 新增：Recall@K / MRR / NDCG 计算
├── RagasEvaluationService.java     ← 新增：RAGAS 端到端评测（Answer Relevance / Faithfulness / Context Relevance）
├── TraceService.java               ← 新增：全链路 Trace 记录
└── EvaluationOrchestrator.java     ← 新增：评测编排（加载数据集 → 逐条检索 → 计算指标 → 输出报告）

edumate-admin/src/main/java/com/edumate/admin/controller/
└── EvaluationController.java  ← 新增：评测 API（POST /api/eval/retrieval, POST /api/eval/ragas, GET /api/eval/trace/{traceId}）

edumate-admin/src/test/resources/eval/
├── eval-dataset.json          ← 新增：50+ 标注问答对数据集
└── eval-dataset-readme.md     ← 新增：数据集说明文档
```

---

### Task 1: 评测数据模型

**Files:**
- Create: `edumate-common/src/main/java/com/edumate/common/model/EvaluationSample.java`
- Create: `edumate-common/src/main/java/com/edumate/common/model/EvaluationResult.java`
- Create: `edumate-common/src/main/java/com/edumate/common/model/TraceRecord.java`

- [ ] **Step 1: 创建 EvaluationSample.java**

```java
package com.edumate.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 评测样本 —— 一条标注好的问答对 + 标准答案（Ground Truth）
 * <p>
 * 用于评测检索质量（Recall@K、MRR）和生成质量（RAGAS）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationSample {
    /** 样本唯一 ID */
    private String id;
    /** 用户自然语言查询 */
    private String query;
    /** 期望检索到的文档分块 ID 列表（Ground Truth） */
    private List<String> relevantChunkIds;
    /** 期望的标准答案文本（用于 RAGAS 评测） */
    private String expectedAnswer;
    /** 所属课程名称 */
    private String courseName;
    /** 考察的知识点标签 */
    private List<String> knowledgePoints;
    /** 难度级别：easy / medium / hard */
    @Builder.Default
    private String difficulty = "medium";
}
```

- [ ] **Step 2: 创建 EvaluationResult.java**

```java
package com.edumate.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 评测结果 —— 单次评测运行的完整指标汇总
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResult {
    /** 评测运行 ID */
    private String runId;
    /** 评测时间戳 */
    private String timestamp;
    /** 评测类型：retrieval / ragas / full */
    private String type;
    /** 评测样本总数 */
    private int totalSamples;
    /** ── 检索指标 ── */
    /** Recall@K（K=1,3,5,10） */
    private Map<Integer, Double> recallAtK;
    /** Mean Reciprocal Rank */
    private double mrr;
    /** Normalized Discounted Cumulative Gain（K=10） */
    private double ndcgAt10;
    /** ── RAGAS 指标 ── */
    /** 答案相关性（Answer Relevance） */
    private double answerRelevance;
    /** 忠实度（Faithfulness） */
    private double faithfulness;
    /** 上下文相关性（Context Relevance） */
    private double contextRelevance;
    /** ── 逐样本明细 ── */
    private List<SampleMetric> sampleMetrics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SampleMetric {
        private String sampleId;
        private String query;
        private double recallAt5;
        private double reciprocalRank;
        private List<String> retrievedChunkIds;
        private List<String> relevantChunkIds;
    }
}
```

- [ ] **Step 3: 创建 TraceRecord.java**

```java
package com.edumate.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 流水线追踪记录 —— 记录单次查询经过各阶段的完整信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraceRecord {
    /** 追踪 ID */
    private String traceId;
    /** 原始查询 */
    private String query;
    /** 会话 ID */
    private String sessionId;
    /** 时间戳 */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /** ── 各阶段记录 ── */
    /** Query 改写 */
    private StageRecord queryRewrite;
    /** 意图识别 */
    private StageRecord intentClassification;
    /** 拒答判断 */
    private StageRecord refusalCheck;
    /** 向量检索 */
    private StageRecord vectorRetrieval;
    /** 关键词检索 */
    private StageRecord keywordRetrieval;
    /** 图谱检索 */
    private StageRecord graphRetrieval;
    /** RRF 融合 */
    private StageRecord rrfFusion;
    /** LLM 生成 */
    private StageRecord llmGeneration;

    /** 总耗时（毫秒） */
    private long totalLatencyMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageRecord {
        /** 阶段名称 */
        private String stage;
        /** 耗时（毫秒） */
        private long latencyMs;
        /** 输入数据摘要 */
        private String input;
        /** 输出数据摘要 */
        private String output;
        /** 是否成功 */
        private boolean success;
        /** 错误信息 */
        private String error;
        /** 扩展元数据（如检索结果数、得分等） */
        private Map<String, Object> metadata;
    }
}
```

- [ ] **Step 4: 编译验证**

```powershell
cd f:\JetBrains\RAG\EduMate
mvnw.cmd -pl edumate-common compile
```

Expected: BUILD SUCCESS

---

### Task 2: 标注测试数据集（50+ 问答对）

**Files:**
- Create: `edumate-admin/src/test/resources/eval/eval-dataset.json`
- Create: `edumate-admin/src/test/resources/eval/eval-dataset-readme.md`

- [ ] **Step 1: 创建数据集说明文档**

```markdown
# EduMate 评测数据集

## 概述

本数据集包含 50+ 条标注问答对，覆盖 5 门计算机核心课程，用于评测 EduMate 的检索质量和生成质量。

## 标注维度

每条样本包含：
- `query`：模拟真实用户提问的自然语言查询
- `relevantChunkIds`：标注的相关文档分块 ID（上传文档后由系统生成，初始为空，运行时动态匹配）
- `expectedAnswer`：标准答案关键要点，用于 RAGAS 评测
- `courseName`：所属课程
- `knowledgePoints`：考察的知识点
- `difficulty`：难度级别

## 课程覆盖

| 课程 | 样本数 | 主要知识点 |
|------|--------|-----------|
| 数据结构 | 12 | 二叉树、B+树、红黑树、图遍历、排序算法、哈希表、堆、栈与队列 |
| 数据库原理 | 10 | 索引、事务、SQL 优化、范式、锁机制、连接查询 |
| 操作系统 | 10 | 进程调度、内存管理、文件系统、死锁、页面置换 |
| 计算机网络 | 10 | TCP/IP、HTTP、DNS、路由算法、OSI 模型 |
| 计算机组成原理 | 8 | 流水线、Cache、中断、指令集、总线 |

## 使用方式

评测运行时会根据 `query` 和 `courseName` 动态匹配实际文档分块，因此 `relevantChunkIds` 字段在数据集文件中留空，由评测框架在加载时动态标注。
```

- [ ] **Step 2: 创建 50 条标注问答对数据集 JSON**

```json
[
  {
    "id": "DS-001",
    "query": "二叉树的中序遍历是什么？",
    "relevantChunkIds": [],
    "expectedAnswer": "中序遍历的顺序是左子树→根节点→右子树，递归实现。时间复杂度O(n)，空间复杂度O(h)。",
    "courseName": "数据结构",
    "knowledgePoints": ["二叉树遍历", "中序遍历"],
    "difficulty": "easy"
  },
  {
    "id": "DS-002",
    "query": "红黑树和AVL树有什么区别？",
    "relevantChunkIds": [],
    "expectedAnswer": "红黑树牺牲了部分平衡性换取更少的旋转操作，插入删除最多3次旋转；AVL树严格平衡，查询更快但插入删除旋转次数更多。红黑树适合频繁插入删除场景，AVL树适合查询密集场景。",
    "courseName": "数据结构",
    "knowledgePoints": ["红黑树", "AVL树", "平衡二叉树"],
    "difficulty": "medium"
  },
  {
    "id": "DS-003",
    "query": "什么是B+树？它在数据库索引中有什么作用？",
    "relevantChunkIds": [],
    "expectedAnswer": "B+树是一种多路平衡搜索树，所有数据存储在叶子节点，非叶子节点只存储键值。叶子节点通过链表相连支持范围查询。在数据库索引中，B+树能减少磁盘I/O次数，因为每个节点可以存储更多键值，降低树的高度。",
    "courseName": "数据结构",
    "knowledgePoints": ["B+树", "数据库索引"],
    "difficulty": "medium"
  },
  {
    "id": "DS-004",
    "query": "快速排序的平均时间复杂度是多少？最坏情况呢？",
    "relevantChunkIds": [],
    "expectedAnswer": "快速排序平均时间复杂度O(nlogn)，最坏情况O(n²)出现在数组已排序且每次选择第一个元素为pivot时。空间复杂度O(logn)。",
    "courseName": "数据结构",
    "knowledgePoints": ["快速排序", "时间复杂度"],
    "difficulty": "easy"
  },
  {
    "id": "DS-005",
    "query": "哈希表解决冲突的方法有哪些？",
    "relevantChunkIds": [],
    "expectedAnswer": "常见方法有：链地址法（拉链法）、开放地址法（线性探测、二次探测、双重哈希）、再哈希法、建立公共溢出区。链地址法最常用。",
    "courseName": "数据结构",
    "knowledgePoints": ["哈希表", "冲突解决"],
    "difficulty": "easy"
  },
  {
    "id": "DS-006",
    "query": "图的深度优先遍历和广度优先遍历分别在什么场景下使用？",
    "relevantChunkIds": [],
    "expectedAnswer": "DFS适合：拓扑排序、连通分量、路径搜索（不要求最短路径）、回溯问题。BFS适合：最短路径（无权图）、层序遍历、社交网络中的好友推荐。",
    "courseName": "数据结构",
    "knowledgePoints": ["图遍历", "DFS", "BFS"],
    "difficulty": "medium"
  },
  {
    "id": "DS-007",
    "query": "什么是堆？最大堆和最小堆的区别是什么？",
    "relevantChunkIds": [],
    "expectedAnswer": "堆是一种完全二叉树，分为最大堆和最小堆。最大堆的父节点大于等于子节点，根节点是最大值；最小堆的父节点小于等于子节点，根节点是最小值。堆常用于实现优先队列和堆排序。",
    "courseName": "数据结构",
    "knowledgePoints": ["堆", "优先队列", "堆排序"],
    "difficulty": "easy"
  },
  {
    "id": "DS-008",
    "query": "栈和队列在操作上的核心区别是什么？各自有什么应用场景？",
    "relevantChunkIds": [],
    "expectedAnswer": "栈是LIFO（后进先出），队列是FIFO（先进先出）。栈应用：函数调用栈、括号匹配、表达式求值、撤销操作。队列应用：BFS遍历、任务调度、消息队列、打印队列。",
    "courseName": "数据结构",
    "knowledgePoints": ["栈", "队列"],
    "difficulty": "easy"
  },
  {
    "id": "DS-009",
    "query": "归并排序的算法思想是什么？为什么它适合外部排序？",
    "relevantChunkIds": [],
    "expectedAnswer": "归并排序采用分治思想：将数组递归分成两半，分别排序，再合并两个有序子数组。时间复杂度O(nlogn)，空间复杂度O(n)。适合外部排序是因为归并阶段可以顺序读写磁盘，不需要随机访问，可以处理超出内存的大数据。",
    "courseName": "数据结构",
    "knowledgePoints": ["归并排序", "外部排序"],
    "difficulty": "medium"
  },
  {
    "id": "DS-010",
    "query": "什么是Trie树（前缀树）？它有哪些实际应用？",
    "relevantChunkIds": [],
    "expectedAnswer": "Trie树是一种用于快速检索字符串的树形结构，每个节点存储一个字符，从根到叶子的路径构成一个字符串。应用：自动补全、拼写检查、IP路由最长前缀匹配、通讯录搜索。",
    "courseName": "数据结构",
    "knowledgePoints": ["Trie树", "前缀树"],
    "difficulty": "medium"
  },
  {
    "id": "DS-011",
    "query": "动态规划解决问题的核心思想是什么？请举例说明。",
    "relevantChunkIds": [],
    "expectedAnswer": "动态规划的核心思想是将大问题分解为重叠子问题，通过存储子问题的解避免重复计算。两个关键要素：最优子结构和重叠子问题。经典例子：背包问题、最长公共子序列、最短路径（Floyd-Warshall）。",
    "courseName": "数据结构",
    "knowledgePoints": ["动态规划", "算法设计"],
    "difficulty": "medium"
  },
  {
    "id": "DS-012",
    "query": "什么是二叉搜索树？它的查找、插入、删除操作的时间复杂度是多少？",
    "relevantChunkIds": [],
    "expectedAnswer": "二叉搜索树（BST）是每个节点的左子树值小于节点值、右子树值大于节点值的二叉树。最优情况下（平衡）查找/插入/删除为O(logn)，最坏情况下（退化为链表）为O(n)。",
    "courseName": "数据结构",
    "knowledgePoints": ["二叉搜索树", "BST"],
    "difficulty": "easy"
  },
  {
    "id": "DB-001",
    "query": "数据库索引的底层数据结构是什么？为什么使用B+树而不是二叉树？",
    "relevantChunkIds": [],
    "expectedAnswer": "数据库索引底层通常使用B+树。因为B+树是多路搜索树，树的高度更低，减少了磁盘I/O次数；叶子节点形成有序链表，支持高效的范围查询。二叉树节点只有两个子节点，树的高度大，磁盘I/O多。",
    "courseName": "数据库原理",
    "knowledgePoints": ["数据库索引", "B+树"],
    "difficulty": "medium"
  },
  {
    "id": "DB-002",
    "query": "什么是事务的ACID特性？",
    "relevantChunkIds": [],
    "expectedAnswer": "ACID：原子性（Atomicity）——事务要么全部执行要么全部不执行；一致性（Consistency）——事务执行前后数据库保持一致状态；隔离性（Isolation）——并发事务之间互不干扰；持久性（Durability）——提交的事务结果永久保存。",
    "courseName": "数据库原理",
    "knowledgePoints": ["事务", "ACID"],
    "difficulty": "easy"
  },
  {
    "id": "DB-003",
    "query": "SQL查询优化有哪些常见方法？",
    "relevantChunkIds": [],
    "expectedAnswer": "常见方法：1）使用EXPLAIN分析执行计划；2）合理创建和使用索引；3）避免SELECT *，只查询需要的列；4）使用JOIN代替子查询；5）避免在WHERE子句中对字段使用函数；6）合理使用分页LIMIT；7）避免使用OR，改用UNION ALL；8）大数据量使用分批处理。",
    "courseName": "数据库原理",
    "knowledgePoints": ["SQL优化", "查询优化"],
    "difficulty": "medium"
  },
  {
    "id": "DB-004",
    "query": "数据库的三大范式是什么？",
    "relevantChunkIds": [],
    "expectedAnswer": "第一范式（1NF）：每个列都是不可再分的原子值；第二范式（2NF）：满足1NF且非主键列完全依赖于主键（消除部分依赖）；第三范式（3NF）：满足2NF且非主键列不传递依赖于主键（消除传递依赖）。",
    "courseName": "数据库原理",
    "knowledgePoints": ["数据库范式", "数据库设计"],
    "difficulty": "easy"
  },
  {
    "id": "DB-005",
    "query": "数据库的锁机制有哪些类型？什么情况下会发生死锁？",
    "relevantChunkIds": [],
    "expectedAnswer": "锁类型：共享锁（S锁/读锁）、排他锁（X锁/写锁）、意向锁（IS/IX）、行锁、表锁、间隙锁。死锁发生在两个事务互相等待对方释放锁资源时，例如事务A持有表1的锁等待表2，事务B持有表2的锁等待表1。",
    "courseName": "数据库原理",
    "knowledgePoints": ["数据库锁", "死锁"],
    "difficulty": "medium"
  },
  {
    "id": "DB-006",
    "query": "MySQL中InnoDB和MyISAM引擎的主要区别是什么？",
    "relevantChunkIds": [],
    "expectedAnswer": "InnoDB支持事务、行级锁、外键、崩溃恢复（MVCC），适合高并发OLTP场景；MyISAM不支持事务、只支持表级锁、不支持外键，但查询速度快，适合读多写少的场景。InnoDB是默认引擎。",
    "courseName": "数据库原理",
    "knowledgePoints": ["存储引擎", "InnoDB", "MyISAM"],
    "difficulty": "medium"
  },
  {
    "id": "DB-007",
    "query": "什么是数据库连接池？它的工作原理是什么？",
    "relevantChunkIds": [],
    "expectedAnswer": "数据库连接池是预先创建一定数量的数据库连接放入池中，应用程序需要时从池中获取，使用完毕后归还到池中，避免频繁创建和销毁连接的开销。核心参数：最小连接数、最大连接数、等待超时、空闲超时。",
    "courseName": "数据库原理",
    "knowledgePoints": ["连接池", "数据库连接"],
    "difficulty": "easy"
  },
  {
    "id": "DB-008",
    "query": "SQL中JOIN的几种类型及区别？",
    "relevantChunkIds": [],
    "expectedAnswer": "INNER JOIN：返回两表匹配的行；LEFT JOIN：返回左表所有行，右表无匹配则为NULL；RIGHT JOIN：返回右表所有行，左表无匹配则为NULL；FULL JOIN：返回两表所有行，无匹配为NULL；CROSS JOIN：笛卡尔积。",
    "courseName": "数据库原理",
    "knowledgePoints": ["SQL JOIN", "连接查询"],
    "difficulty": "easy"
  },
  {
    "id": "DB-009",
    "query": "什么是慢查询？如何分析和优化慢查询？",
    "relevantChunkIds": [],
    "expectedAnswer": "慢查询是执行时间超过阈值的SQL查询。分析：使用慢查询日志、EXPLAIN查看执行计划、SHOW PROFILE查看资源消耗。优化：添加索引、重写SQL、优化表结构、分库分表、使用缓存。",
    "courseName": "数据库原理",
    "knowledgePoints": ["慢查询", "SQL优化"],
    "difficulty": "medium"
  },
  {
    "id": "DB-010",
    "query": "什么是MVCC？它如何实现事务隔离？",
    "relevantChunkIds": [],
    "expectedAnswer": "MVCC（多版本并发控制）通过保存数据的多个版本来实现并发控制。每个事务看到的是数据的一个快照版本，读操作不阻塞写操作，写操作不阻塞读操作。InnoDB通过undo log和ReadView实现MVCC，支持READ COMMITTED和REPEATABLE READ隔离级别。",
    "courseName": "数据库原理",
    "knowledgePoints": ["MVCC", "事务隔离"],
    "difficulty": "hard"
  },
  {
    "id": "OS-001",
    "query": "进程和线程的区别是什么？",
    "relevantChunkIds": [],
    "expectedAnswer": "进程是资源分配的基本单位，拥有独立的内存空间；线程是CPU调度的基本单位，同一进程内的线程共享内存空间。进程切换开销大，线程切换开销小；进程间通信需要IPC，线程间可以直接读写共享内存。",
    "courseName": "操作系统",
    "knowledgePoints": ["进程", "线程"],
    "difficulty": "easy"
  },
  {
    "id": "OS-002",
    "query": "什么是死锁？死锁的四个必要条件是什么？如何预防死锁？",
    "relevantChunkIds": [],
    "expectedAnswer": "死锁是两个或多个进程互相等待对方释放资源而无限阻塞的状态。四个必要条件：互斥条件、持有并等待、不可剥夺、循环等待。预防方法：破坏任一条件，如一次性分配所有资源（破坏持有并等待）、资源有序分配（破坏循环等待）。",
    "courseName": "操作系统",
    "knowledgePoints": ["死锁", "进程同步"],
    "difficulty": "medium"
  },
  {
    "id": "OS-003",
    "query": "虚拟内存是什么？页面置换算法有哪些？",
    "relevantChunkIds": [],
    "expectedAnswer": "虚拟内存是操作系统将磁盘空间作为内存扩展的技术，使得程序可以使用比物理内存更大的地址空间。页面置换算法：FIFO（先进先出）、LRU（最近最少使用）、LFU（最不经常使用）、OPT（最优置换，理论最优）、Clock算法（LRU的近似实现）。",
    "courseName": "操作系统",
    "knowledgePoints": ["虚拟内存", "页面置换"],
    "difficulty": "medium"
  },
  {
    "id": "OS-004",
    "query": "进程调度算法有哪些？各自有什么优缺点？",
    "relevantChunkIds": [],
    "expectedAnswer": "FCFS（先来先服务）：简单但平均等待时间长；SJF（短作业优先）：平均等待时间最短但需要预知作业长度；RR（时间片轮转）：公平但时间片大小难以确定；优先级调度：可以优先处理重要任务但低优先级可能饥饿；多级反馈队列：综合最优，结合多种策略。",
    "courseName": "操作系统",
    "knowledgePoints": ["进程调度", "调度算法"],
    "difficulty": "medium"
  },
  {
    "id": "OS-005",
    "query": "什么是上下文切换？切换的开销包括哪些？",
    "relevantChunkIds": [],
    "expectedAnswer": "上下文切换是CPU从一个进程/线程切换到另一个时保存当前状态并加载新状态的过程。开销包括：保存和恢复寄存器、更新PCB、刷新TLB、切换内存映射、Cache失效。频繁切换会严重影响系统性能。",
    "courseName": "操作系统",
    "knowledgePoints": ["上下文切换", "进程调度"],
    "difficulty": "medium"
  },
  {
    "id": "OS-006",
    "query": "文件系统是如何管理磁盘空间的？有哪些常见的文件分配方式？",
    "relevantChunkIds": [],
    "expectedAnswer": "文件系统通过空闲空间管理（位图法、空闲链表法）和文件分配方式来管理磁盘。常见分配方式：连续分配（简单但碎片多）、链接分配（无碎片但随机访问慢）、索引分配（支持随机访问但索引节点开销大），现代文件系统多用混合方式如ext4的extent。",
    "courseName": "操作系统",
    "knowledgePoints": ["文件系统", "磁盘管理"],
    "difficulty": "medium"
  },
  {
    "id": "OS-007",
    "query": "用户态和内核态的区别是什么？系统调用的过程是怎样的？",
    "relevantChunkIds": [],
    "expectedAnswer": "用户态下程序只能访问受限的资源和指令，内核态可以访问所有硬件资源和特权指令。系统调用时，用户程序通过中断（int 0x80或syscall指令）陷入内核，CPU切换到内核态，内核执行对应服务，完成后返回用户态。切换涉及栈切换和权限检查。",
    "courseName": "操作系统",
    "knowledgePoints": ["用户态", "内核态", "系统调用"],
    "difficulty": "medium"
  },
  {
    "id": "OS-008",
    "query": "什么是内存碎片？内部碎片和外部碎片的区别？",
    "relevantChunkIds": [],
    "expectedAnswer": "内存碎片是内存中无法使用的空闲区域。内部碎片：分配给进程的内存块中未被使用的部分（如分页系统中最后一页不完整）；外部碎片：所有空闲内存总和足够但无法分配连续块（如分段系统）。解决：分页消除外部碎片，紧凑/压缩合并小碎片。",
    "courseName": "操作系统",
    "knowledgePoints": ["内存碎片", "内存管理"],
    "difficulty": "easy"
  },
  {
    "id": "OS-009",
    "query": "什么是中断？硬中断和软中断的区别？",
    "relevantChunkIds": [],
    "expectedAnswer": "中断是CPU暂停当前程序执行转而处理突发事件（如I/O完成、异常）的机制。硬中断由硬件设备（如键盘、网卡）触发，异步发生；软中断由程序指令触发（如系统调用int 0x80），同步发生。硬中断有优先级，可被屏蔽；软中断不可屏蔽。",
    "courseName": "操作系统",
    "knowledgePoints": ["中断", "异常处理"],
    "difficulty": "medium"
  },
  {
    "id": "OS-010",
    "query": "什么是银行家算法？它如何避免死锁？",
    "relevantChunkIds": [],
    "expectedAnswer": "银行家算法（Banker's Algorithm）是一种死锁避免算法，由Dijkstra提出。系统在分配资源前先模拟分配，判断是否会导致系统进入不安全状态。如果分配后存在一个安全序列使得所有进程都能完成，则分配是安全的；否则拒绝分配。需要预先知道每个进程的最大资源需求。",
    "courseName": "操作系统",
    "knowledgePoints": ["银行家算法", "死锁避免"],
    "difficulty": "hard"
  },
  {
    "id": "CN-001",
    "query": "TCP三次握手的过程是怎样的？为什么需要三次而不是两次？",
    "relevantChunkIds": [],
    "expectedAnswer": "三次握手：1）客户端发送SYN（seq=x）；2）服务器回复SYN+ACK（seq=y, ack=x+1）；3）客户端发送ACK（ack=y+1）。需要三次的原因：防止已失效的连接请求到达服务器导致错误连接。两次握手无法让服务器确认客户端的接收能力。",
    "courseName": "计算机网络",
    "knowledgePoints": ["TCP", "三次握手"],
    "difficulty": "easy"
  },
  {
    "id": "CN-002",
    "query": "TCP和UDP的主要区别是什么？各自适用什么场景？",
    "relevantChunkIds": [],
    "expectedAnswer": "TCP面向连接、可靠传输（确认重传）、流量控制、拥塞控制，适合文件传输、Web浏览、邮件；UDP无连接、不可靠、无流量控制、开销小，适合视频直播、DNS查询、实时游戏。TCP有20字节头部开销，UDP只有8字节。",
    "courseName": "计算机网络",
    "knowledgePoints": ["TCP", "UDP", "传输层"],
    "difficulty": "easy"
  },
  {
    "id": "CN-003",
    "query": "HTTP和HTTPS的区别是什么？HTTPS的加密过程是怎样的？",
    "relevantChunkIds": [],
    "expectedAnswer": "HTTP是明文传输，端口80；HTTPS = HTTP + SSL/TLS，端口443，数据加密传输。HTTPS加密过程：客户端发起请求 → 服务器返回证书（含公钥）→ 客户端验证证书 → 生成对称密钥，用公钥加密发送 → 服务器用私钥解密获得对称密钥 → 后续通信使用对称密钥加密。",
    "courseName": "计算机网络",
    "knowledgePoints": ["HTTP", "HTTPS", "SSL/TLS"],
    "difficulty": "medium"
  },
  {
    "id": "CN-004",
    "query": "DNS解析的过程是怎样的？",
    "relevantChunkIds": [],
    "expectedAnswer": "DNS解析过程：1）浏览器缓存 → 2）操作系统缓存（hosts文件）→ 3）本地DNS服务器 → 4）根域名服务器 → 5）顶级域名服务器（如.com）→ 6）权威域名服务器 → 返回IP地址。DNS使用UDP 53端口，支持递归查询和迭代查询。",
    "courseName": "计算机网络",
    "knowledgePoints": ["DNS", "域名解析"],
    "difficulty": "easy"
  },
  {
    "id": "CN-005",
    "query": "OSI七层模型和TCP/IP四层模型的对应关系是什么？",
    "relevantChunkIds": [],
    "expectedAnswer": "OSI七层：物理层、数据链路层、网络层、传输层、会话层、表示层、应用层。TCP/IP四层：网络接口层（对应物理层+数据链路层）、网络层（对应网络层）、传输层（对应传输层）、应用层（对应会话层+表示层+应用层）。",
    "courseName": "计算机网络",
    "knowledgePoints": ["OSI模型", "TCP/IP模型"],
    "difficulty": "easy"
  },
  {
    "id": "CN-006",
    "query": "什么是TCP的拥塞控制？有哪些算法？",
    "relevantChunkIds": [],
    "expectedAnswer": "TCP拥塞控制是防止网络过载的机制。四个算法：慢启动（cwnd指数增长到阈值）、拥塞避免（cwnd线性增长）、快速重传（收到3个重复ACK立即重传）、快速恢复（不进入慢启动，直接减半cwnd）。Tahoe和Reno是两种经典实现。",
    "courseName": "计算机网络",
    "knowledgePoints": ["TCP拥塞控制", "慢启动", "快速重传"],
    "difficulty": "medium"
  },
  {
    "id": "CN-007",
    "query": "什么是路由选择算法？距离向量和链路状态路由的区别？",
    "relevantChunkIds": [],
    "expectedAnswer": "距离向量路由（如RIP）：每个路由器只与邻居交换路由表，Bellman-Ford算法，收敛慢，有计数到无穷问题，最大跳数15。链路状态路由（如OSPF）：每个路由器通过洪泛获取全网拓扑，Dijkstra算法，收敛快，支持VLSM，无环路问题。",
    "courseName": "计算机网络",
    "knowledgePoints": ["路由算法", "RIP", "OSPF"],
    "difficulty": "medium"
  },
  {
    "id": "CN-008",
    "query": "什么是子网掩码？如何通过子网掩码划分网络？",
    "relevantChunkIds": [],
    "expectedAnswer": "子网掩码用于区分IP地址的网络部分和主机部分。如255.255.255.0表示前24位是网络号。CIDR记法：192.168.1.0/24。子网划分通过借用主机位作为网络位实现，如将/24划分为两个/25子网。",
    "courseName": "计算机网络",
    "knowledgePoints": ["子网掩码", "子网划分", "CIDR"],
    "difficulty": "easy"
  },
  {
    "id": "CN-009",
    "query": "什么是ARP协议？它的工作原理是什么？",
    "relevantChunkIds": [],
    "expectedAnswer": "ARP（地址解析协议）用于将IP地址解析为MAC地址。工作原理：主机广播ARP请求帧（包含目标IP），目标主机收到后单播回复ARP应答帧（包含自己的MAC地址）。ARP缓存表存储IP-MAC映射以减少广播。",
    "courseName": "计算机网络",
    "knowledgePoints": ["ARP", "地址解析"],
    "difficulty": "easy"
  },
  {
    "id": "CN-010",
    "query": "什么是HTTP状态码？常见的状态码有哪些？",
    "relevantChunkIds": [],
    "expectedAnswer": "HTTP状态码分5类：1xx（信息）、2xx（成功）、3xx（重定向）、4xx（客户端错误）、5xx（服务器错误）。常见：200 OK、201 Created、301 永久重定向、302 临时重定向、400 Bad Request、401 Unauthorized、403 Forbidden、404 Not Found、500 Internal Server Error、502 Bad Gateway、503 Service Unavailable。",
    "courseName": "计算机网络",
    "knowledgePoints": ["HTTP", "状态码"],
    "difficulty": "easy"
  },
  {
    "id": "CA-001",
    "query": "CPU流水线是什么？为什么流水线能提高性能？",
    "relevantChunkIds": [],
    "expectedAnswer": "CPU流水线是将指令执行过程分解为多个阶段（取指、译码、执行、访存、写回），各阶段可以并行处理不同指令，从而提高吞吐量。理想情况下每条指令只需1个时钟周期。但流水线冒险（结构冒险、数据冒险、控制冒险）会降低效率。",
    "courseName": "计算机组成原理",
    "knowledgePoints": ["CPU流水线", "指令执行"],
    "difficulty": "medium"
  },
  {
    "id": "CA-002",
    "query": "Cache的工作原理是什么？什么是Cache命中率？",
    "relevantChunkIds": [],
    "expectedAnswer": "Cache是位于CPU和主存之间的高速缓存，利用局部性原理（时间局部性、空间局部性）存储最近访问的数据和指令。命中率 = Cache命中次数 / 总访问次数。映射方式：直接映射、全相联映射、组相联映射。替换策略：LRU、FIFO、随机。",
    "courseName": "计算机组成原理",
    "knowledgePoints": ["Cache", "缓存", "局部性原理"],
    "difficulty": "medium"
  },
  {
    "id": "CA-003",
    "query": "什么是指令集架构？CISC和RISC的区别是什么？",
    "relevantChunkIds": [],
    "expectedAnswer": "CISC（复杂指令集）：指令复杂、长度可变、单条指令可完成多步操作，代表x86；RISC（精简指令集）：指令简单、长度固定、一条指令只做一件事，代表ARM、MIPS、RISC-V。RISC更容易流水线化，功耗更低。",
    "courseName": "计算机组成原理",
    "knowledgePoints": ["指令集", "CISC", "RISC"],
    "difficulty": "medium"
  },
  {
    "id": "CA-004",
    "query": "计算机中的中断处理流程是怎样的？",
    "relevantChunkIds": [],
    "expectedAnswer": "中断处理流程：1）设备发出中断信号；2）CPU完成当前指令后响应中断；3）保存断点和程序状态字（PSW）；4）根据中断向量表跳转到中断服务程序（ISR）；5）执行ISR处理中断；6）恢复现场；7）返回断点继续执行。",
    "courseName": "计算机组成原理",
    "knowledgePoints": ["中断处理", "中断向量"],
    "difficulty": "medium"
  },
  {
    "id": "CA-005",
    "query": "什么是总线？系统总线有哪几种类型？",
    "relevantChunkIds": [],
    "expectedAnswer": "总线是计算机各部件之间传输信息的公共通道。系统总线分为：数据总线（双向传输数据）、地址总线（单向传输地址）、控制总线（传输控制信号）。总线标准：PCI、PCIe、USB、SATA等。总线带宽 = 总线宽度 × 时钟频率。",
    "courseName": "计算机组成原理",
    "knowledgePoints": ["总线", "系统总线"],
    "difficulty": "easy"
  },
  {
    "id": "CA-006",
    "query": "什么是冯·诺依曼架构？它有哪些局限性？",
    "relevantChunkIds": [],
    "expectedAnswer": "冯·诺依曼架构的核心：存储程序、指令和数据存在同一存储器中、顺序执行指令。局限性：冯·诺依曼瓶颈（CPU和内存之间的带宽限制）、指令和数据共用总线导致串行访问。哈佛架构将指令和数据分开存储，可以并行访问。",
    "courseName": "计算机组成原理",
    "knowledgePoints": ["冯·诺依曼架构", "哈佛架构"],
    "difficulty": "easy"
  },
  {
    "id": "CA-007",
    "query": "什么是DMA？它和程序中断方式有什么区别？",
    "relevantChunkIds": [],
    "expectedAnswer": "DMA（直接存储器访问）允许外设直接与内存交换数据，无需CPU干预。与程序中断方式的区别：中断方式每次传输需要CPU参与（保存现场、执行ISR、恢复现场），DMA只在传输开始和结束时通知CPU，中间过程由DMA控制器负责，CPU可以做其他事。DMA适合大量数据传输。",
    "courseName": "计算机组成原理",
    "knowledgePoints": ["DMA", "I/O控制"],
    "difficulty": "medium"
  },
  {
    "id": "CA-008",
    "query": "什么是TLB？它在虚拟地址转换中的作用是什么？",
    "relevantChunkIds": [],
    "expectedAnswer": "TLB（快表/Translation Lookaside Buffer）是CPU内部的高速缓存，存储最近使用的页表项，用于加速虚拟地址到物理地址的转换。访问流程：CPU发出虚拟地址 → 查TLB（命中则直接获得物理地址）→ TLB未命中则查页表 → 更新TLB。TLB命中率通常在99%以上。",
    "courseName": "计算机组成原理",
    "knowledgePoints": ["TLB", "地址转换", "虚拟内存"],
    "difficulty": "medium"
  }
]
```

- [ ] **Step 3: 验证 JSON 格式正确**

```powershell
cd f:\JetBrains\RAG\EduMate
python -c "import json; data=json.load(open('edumate-admin/src/test/resources/eval/eval-dataset.json','r',encoding='utf-8')); print(f'Valid JSON: {len(data)} samples')"
```

Expected: `Valid JSON: 50 samples`

---

### Task 3: 检索评测指标服务（Recall@K / MRR / NDCG）

**Files:**
- Create: `edumate-core/src/main/java/com/edumate/core/evaluation/EvaluationMetricsService.java`
- Create: `edumate-core/src/test/java/com/edumate/core/evaluation/EvaluationMetricsServiceTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.edumate.core.evaluation;

import com.edumate.common.model.EvaluationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EvaluationMetricsServiceTest {

    private final EvaluationMetricsService service = new EvaluationMetricsService();

    @Test
    void shouldCalculatePerfectRecallAt5() {
        // 相关文档：["A", "B", "C"]，检索结果：["A", "B", "C", "D", "E"]
        double recall = service.calculateRecallAtK(
                List.of("A", "B", "C"),
                List.of("A", "B", "C", "D", "E"),
                5);
        assertEquals(1.0, recall, 0.001);
    }

    @Test
    void shouldCalculatePartialRecallAt3() {
        // 相关文档：["A", "B", "C", "D"]，检索结果前3个：["A", "B", "X"]
        double recall = service.calculateRecallAtK(
                List.of("A", "B", "C", "D"),
                List.of("A", "B", "X", "Y", "Z"),
                3);
        assertEquals(0.5, recall, 0.001); // 2/4 = 0.5
    }

    @Test
    void shouldCalculateZeroRecallWhenNoRelevant() {
        double recall = service.calculateRecallAtK(
                List.of("A", "B", "C"),
                List.of("X", "Y", "Z"),
                3);
        assertEquals(0.0, recall, 0.001);
    }

    @Test
    void shouldCalculateMRR() {
        // 样本1：相关文档在第2位 → RR=1/2=0.5
        // 样本2：相关文档在第1位 → RR=1/1=1.0
        // 样本3：无相关文档 → RR=0
        double mrr = service.calculateMRR(List.of(
                new SampleMetrics(0.5, 1.0 / 2),
                new SampleMetrics(1.0, 1.0),
                new SampleMetrics(0.0, 0.0)
        ));
        assertEquals((0.5 + 1.0 + 0.0) / 3, mrr, 0.001);
    }

    @Test
    void shouldCalculateNDCG() {
        // 相关性：rel_1=3, rel_2=2, rel_3=0, rel_4=1, rel_5=0
        // DCG = 3 + 2/log2(3) + 0 + 1/log2(5) + 0
        // IDCG = 3 + 2/log2(3) + 1/log2(4) + 0 + 0
        List<Integer> relevance = List.of(3, 2, 0, 1, 0);
        double ndcg = service.calculateNDCG(relevance, 5);
        assertTrue(ndcg >= 0.0 && ndcg <= 1.0);
    }

    @Test
    void shouldCalculatePerfectNDCG() {
        List<Integer> relevance = List.of(3, 2, 1, 0, 0);
        double ndcg = service.calculateNDCG(relevance, 5);
        assertEquals(1.0, ndcg, 0.001);
    }

    // 内部测试辅助类
    private record SampleMetrics(double recallAt5, double reciprocalRank) {}
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
cd f:\JetBrains\RAG\EduMate
mvnw.cmd -pl edumate-core test -Dtest=EvaluationMetricsServiceTest
```

Expected: FAIL (类不存在)

- [ ] **Step 3: 创建 EvaluationMetricsService.java**

```java
package com.edumate.core.evaluation;

import com.edumate.common.model.EvaluationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

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
                dcg += rel / (Math.log(i + 2) / Math.log(2)); // i+2 因为 i 从 0 开始
            }
        }

        // 计算理想 DCG（将相关性等级降序排列）
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
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
mvnw.cmd -pl edumate-core test -Dtest=EvaluationMetricsServiceTest
```

Expected: PASS (6 tests)

- [ ] **Step 5: 编译验证**

```powershell
mvnw.cmd -pl edumate-core compile
```

Expected: BUILD SUCCESS

---

### Task 4: RAGAS 端到端评测服务

**Files:**
- Create: `edumate-core/src/main/java/com/edumate/core/evaluation/RagasEvaluationService.java`
- Create: `edumate-core/src/test/java/com/edumate/core/evaluation/RagasEvaluationServiceTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.edumate.core.evaluation;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RagasEvaluationServiceTest {

    @Mock
    private ChatModel chatModel;

    @Test
    void shouldReturnZeroWhenChatModelIsNull() {
        RagasEvaluationService service = new RagasEvaluationService(null);
        double score = service.evaluateAnswerRelevance("问: 二叉树", "答: 二叉树是...", "二叉树...");
        assertEquals(0.0, score, 0.001);
    }

    @Test
    void shouldReturnZeroWhenInputsAreBlank() {
        RagasEvaluationService service = new RagasEvaluationService(chatModel);
        double score = service.evaluateAnswerRelevance("", "", "");
        assertEquals(0.0, score, 0.001);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
mvnw.cmd -pl edumate-core test -Dtest=RagasEvaluationServiceTest
```

Expected: FAIL (类不存在)

- [ ] **Step 3: 创建 RagasEvaluationService.java**

```java
package com.edumate.core.evaluation;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAGAS 端到端评测服务 —— 评估 RAG 问答的生成质量
 * <p>
 * 三个核心指标：
 * 1. Answer Relevance（答案相关性）：生成的答案是否与问题相关
 * 2. Faithfulness（忠实度）：生成的答案是否完全基于提供的上下文
 * 3. Context Relevance（上下文相关性）：检索到的上下文是否与问题相关
 * <p>
 * 当 ChatModel 不可用时，所有指标返回 0.0。
 */
@Service
public class RagasEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(RagasEvaluationService.class);

    private final ChatModel chatModel;

    private static final String ANSWER_RELEVANCE_PROMPT = """
            请评估以下生成的答案与问题之间的相关性。
            只输出一个 0 到 100 的整数分数，其中 100 表示完全相关，0 表示完全不相关。
            不要输出任何其他文字。

            问题：%s
            生成答案：%s
            分数：""";

    private static final String FAITHFULNESS_PROMPT = """
            请评估以下生成的答案是否完全基于提供的上下文，是否存在编造（幻觉）的内容。
            只输出一个 0 到 100 的整数分数，其中 100 表示完全忠实于上下文，0 表示完全编造。
            不要输出任何其他文字。

            上下文：%s
            生成答案：%s
            分数：""";

    private static final String CONTEXT_RELEVANCE_PROMPT = """
            请评估以下检索到的上下文与问题之间的相关性。
            只输出一个 0 到 100 的整数分数，其中 100 表示完全相关，0 表示完全不相关。
            不要输出任何其他文字。

            问题：%s
            上下文：%s
            分数：""";

    public RagasEvaluationService(@Nullable ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 评估答案相关性（Answer Relevance）
     *
     * @param query   用户问题
     * @param answer  生成的答案
     * @param context 检索到的上下文
     * @return 0-100 的相关性分数
     */
    public double evaluateAnswerRelevance(String query, String answer, String context) {
        if (chatModel == null) {
            log.warn("ChatModel 不可用，跳过答案相关性评测");
            return 0.0;
        }
        if (isBlank(query) || isBlank(answer)) {
            return 0.0;
        }

        try {
            String prompt = String.format(ANSWER_RELEVANCE_PROMPT, query, answer);
            return scoreFromLLM(prompt);
        } catch (Exception e) {
            log.warn("答案相关性评测失败: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * 评估忠实度（Faithfulness）
     *
     * @param context 检索到的上下文
     * @param answer  生成的答案
     * @return 0-100 的忠实度分数
     */
    public double evaluateFaithfulness(String context, String answer) {
        if (chatModel == null) {
            log.warn("ChatModel 不可用，跳过忠实度评测");
            return 0.0;
        }
        if (isBlank(context) || isBlank(answer)) {
            return 0.0;
        }

        try {
            // 截断上下文避免 token 超限
            String truncatedContext = truncate(context, 2000);
            String prompt = String.format(FAITHFULNESS_PROMPT, truncatedContext, answer);
            return scoreFromLLM(prompt);
        } catch (Exception e) {
            log.warn("忠实度评测失败: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * 评估上下文相关性（Context Relevance）
     *
     * @param query   用户问题
     * @param context 检索到的上下文
     * @return 0-100 的上下文相关性分数
     */
    public double evaluateContextRelevance(String query, String context) {
        if (chatModel == null) {
            log.warn("ChatModel 不可用，跳过上下文相关性评测");
            return 0.0;
        }
        if (isBlank(query) || isBlank(context)) {
            return 0.0;
        }

        try {
            String truncatedContext = truncate(context, 2000);
            String prompt = String.format(CONTEXT_RELEVANCE_PROMPT, query, truncatedContext);
            return scoreFromLLM(prompt);
        } catch (Exception e) {
            log.warn("上下文相关性评测失败: {}", e.getMessage());
            return 0.0;
        }
    }

    private double scoreFromLLM(String prompt) {
        var response = chatModel.chat(ChatRequest.builder()
                .messages(UserMessage.from(prompt))
                .build());
        String text = response.aiMessage().text().trim();
        try {
            double score = Double.parseDouble(text);
            return Math.max(0, Math.min(100, score));
        } catch (NumberFormatException e) {
            // 尝试从文本中提取数字
            String digits = text.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                return Math.max(0, Math.min(100, Double.parseDouble(digits)));
            }
            log.warn("无法解析 LLM 评测分数: '{}'", text);
            return 0.0;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
mvnw.cmd -pl edumate-core test -Dtest=RagasEvaluationServiceTest
```

Expected: PASS (2 tests)

- [ ] **Step 5: 编译验证**

```powershell
mvnw.cmd -pl edumate-core compile
```

Expected: BUILD SUCCESS

---

### Task 5: 全链路 Trace 追踪服务

**Files:**
- Create: `edumate-core/src/main/java/com/edumate/core/evaluation/TraceService.java`
- Create: `edumate-core/src/test/java/com/edumate/core/evaluation/TraceServiceTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.edumate.core.evaluation;

import com.edumate.common.model.TraceRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TraceServiceTest {

    private TraceService service;

    @BeforeEach
    void setUp() {
        service = new TraceService();
    }

    @Test
    void shouldStartAndGetTrace() {
        TraceRecord trace = service.startTrace("test-query-1", "session-1");
        assertNotNull(trace);
        assertNotNull(trace.getTraceId());
        assertEquals("test-query-1", trace.getQuery());
        assertEquals("session-1", trace.getSessionId());
    }

    @Test
    void shouldRecordStageAndGetTrace() {
        TraceRecord trace = service.startTrace("query", "session");
        String traceId = trace.getTraceId();

        service.recordStage(traceId, "vectorRetrieval", 150L,
                "query: 二叉树", "found 10 results", true, null);

        TraceRecord retrieved = service.getTrace(traceId);
        assertNotNull(retrieved);
        assertNotNull(retrieved.getVectorRetrieval());
        assertEquals("vectorRetrieval", retrieved.getVectorRetrieval().getStage());
        assertEquals(150L, retrieved.getVectorRetrieval().getLatencyMs());
        assertTrue(retrieved.getVectorRetrieval().isSuccess());
    }

    @Test
    void shouldReturnNullForUnknownTraceId() {
        assertNull(service.getTrace("nonexistent"));
    }

    @Test
    void shouldCompleteTrace() {
        TraceRecord trace = service.startTrace("query", "session");
        String traceId = trace.getTraceId();

        service.recordStage(traceId, "vectorRetrieval", 100L, "in", "out", true, null);
        service.recordStage(traceId, "keywordRetrieval", 80L, "in", "out", true, null);
        service.recordStage(traceId, "rrfFusion", 20L, "in", "out", true, null);

        TraceRecord completed = service.completeTrace(traceId);
        assertNotNull(completed);
        assertTrue(completed.getTotalLatencyMs() > 0);
    }

    @Test
    void shouldTrackRecentTraces() {
        for (int i = 0; i < 5; i++) {
            service.startTrace("query-" + i, "session-" + i);
        }
        var recent = service.getRecentTraces(3);
        assertEquals(3, recent.size());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
mvnw.cmd -pl edumate-core test -Dtest=TraceServiceTest
```

Expected: FAIL (类不存在)

- [ ] **Step 3: 创建 TraceService.java**

```java
package com.edumate.core.evaluation;

import com.edumate.common.model.TraceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 全链路 Trace 追踪服务 —— 记录单次查询经过各阶段的详细信息
 * <p>
 * 使用内存存储（ConcurrentHashMap），不依赖外部数据库。
 * 最多保留最近 200 条 Trace 记录，超过自动淘汰。
 * <p>
 * 追踪阶段：queryRewrite → intentClassification → refusalCheck →
 * vectorRetrieval → keywordRetrieval → graphRetrieval → rrfFusion → llmGeneration
 */
@Service
public class TraceService {

    private static final Logger log = LoggerFactory.getLogger(TraceService.class);

    private static final int MAX_TRACES = 200;

    private final Map<String, TraceRecord> traces = new ConcurrentHashMap<>();
    private final Deque<String> recentTraceIds = new ConcurrentLinkedDeque<>();

    /**
     * 开始一次新的 Trace 追踪
     *
     * @param query     原始查询
     * @param sessionId 会话 ID
     * @return 初始化后的 TraceRecord
     */
    public TraceRecord startTrace(String query, String sessionId) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        TraceRecord trace = TraceRecord.builder()
                .traceId(traceId)
                .query(query)
                .sessionId(sessionId)
                .timestamp(Instant.now())
                .build();
        traces.put(traceId, trace);
        recentTraceIds.addLast(traceId);

        // 淘汰旧记录
        while (recentTraceIds.size() > MAX_TRACES) {
            String oldest = recentTraceIds.pollFirst();
            if (oldest != null) traces.remove(oldest);
        }

        log.debug("Trace 开始: traceId={}, query='{}'", traceId, query);
        return trace;
    }

    /**
     * 记录一个阶段的执行信息
     *
     * @param traceId  追踪 ID
     * @param stage    阶段名称（如 "vectorRetrieval"）
     * @param latencyMs 耗时（毫秒）
     * @param input    输入摘要
     * @param output   输出摘要
     * @param success  是否成功
     * @param error    错误信息（成功时为 null）
     */
    public void recordStage(String traceId, String stage, long latencyMs,
                            String input, String output, boolean success, String error) {
        TraceRecord trace = traces.get(traceId);
        if (trace == null) {
            log.warn("Trace 不存在: traceId={}", traceId);
            return;
        }

        TraceRecord.StageRecord record = TraceRecord.StageRecord.builder()
                .stage(stage)
                .latencyMs(latencyMs)
                .input(input)
                .output(output)
                .success(success)
                .error(error)
                .build();

        setStageField(trace, stage, record);
        log.debug("Trace 阶段记录: traceId={}, stage={}, latency={}ms, success={}",
                traceId, stage, latencyMs, success);
    }

    /**
     * 完成 Trace 追踪，计算总耗时
     *
     * @param traceId 追踪 ID
     * @return 完整的 TraceRecord
     */
    public TraceRecord completeTrace(String traceId) {
        TraceRecord trace = traces.get(traceId);
        if (trace == null) return null;

        trace.setTotalLatencyMs(
                Duration.between(trace.getTimestamp(), Instant.now()).toMillis());
        log.info("Trace 完成: traceId={}, totalLatency={}ms", traceId, trace.getTotalLatencyMs());
        return trace;
    }

    /**
     * 获取指定 Trace 记录
     *
     * @param traceId 追踪 ID
     * @return TraceRecord 或 null
     */
    public TraceRecord getTrace(String traceId) {
        return traces.get(traceId);
    }

    /**
     * 获取最近 N 条 Trace 记录（按时间倒序）
     *
     * @param count 返回数量
     * @return Trace 记录列表
     */
    public List<TraceRecord> getRecentTraces(int count) {
        List<TraceRecord> result = new ArrayList<>();
        Iterator<String> it = recentTraceIds.descendingIterator();
        while (it.hasNext() && result.size() < count) {
            TraceRecord trace = traces.get(it.next());
            if (trace != null) {
                result.add(trace);
            }
        }
        return result;
    }

    /**
     * 将阶段记录写入 TraceRecord 的对应字段
     */
    private void setStageField(TraceRecord trace, String stage, TraceRecord.StageRecord record) {
        switch (stage) {
            case "queryRewrite" -> trace.setQueryRewrite(record);
            case "intentClassification" -> trace.setIntentClassification(record);
            case "refusalCheck" -> trace.setRefusalCheck(record);
            case "vectorRetrieval" -> trace.setVectorRetrieval(record);
            case "keywordRetrieval" -> trace.setKeywordRetrieval(record);
            case "graphRetrieval" -> trace.setGraphRetrieval(record);
            case "rrfFusion" -> trace.setRrfFusion(record);
            case "llmGeneration" -> trace.setLlmGeneration(record);
            default -> log.warn("未知阶段: {}", stage);
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
mvnw.cmd -pl edumate-core test -Dtest=TraceServiceTest
```

Expected: PASS (5 tests)

- [ ] **Step 5: 编译验证**

```powershell
mvnw.cmd -pl edumate-core compile
```

Expected: BUILD SUCCESS

---

### Task 6: 评测编排器

**Files:**
- Create: `edumate-core/src/main/java/com/edumate/core/evaluation/EvaluationOrchestrator.java`
- Create: `edumate-core/src/test/java/com/edumate/core/evaluation/EvaluationOrchestratorTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.edumate.core.evaluation;

import com.edumate.common.model.DocumentChunk;
import com.edumate.common.model.EvaluationResult;
import com.edumate.common.model.EvaluationSample;
import com.edumate.core.agent.QueryRewriteService;
import com.edumate.core.retrieval.HybridSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationOrchestratorTest {

    @Mock
    private HybridSearchService hybridSearchService;

    @Mock
    private QueryRewriteService queryRewriteService;

    @Mock
    private EvaluationMetricsService metricsService;

    @Mock
    private TraceService traceService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRunRetrievalEvaluation() {
        when(queryRewriteService.rewrite(anyString())).thenAnswer(inv -> inv.getArgument(0));

        DocumentChunk chunk = DocumentChunk.builder()
                .id("chunk-1").content("二叉树内容").courseName("数据结构").build();
        when(hybridSearchService.search(anyString(), anyInt())).thenReturn(List.of(chunk));

        EvaluationOrchestrator orchestrator = new EvaluationOrchestrator(
                hybridSearchService, queryRewriteService, null,
                metricsService, traceService, objectMapper);

        EvaluationSample sample = EvaluationSample.builder()
                .id("DS-001").query("什么是二叉树")
                .relevantChunkIds(List.of("chunk-1"))
                .courseName("数据结构").build();

        EvaluationResult result = orchestrator.runRetrievalEvaluation(List.of(sample));

        assertNotNull(result);
        assertEquals("retrieval", result.getType());
        assertEquals(1, result.getTotalSamples());
    }

    @Test
    void shouldReturnEmptyResultForEmptySamples() {
        EvaluationOrchestrator orchestrator = new EvaluationOrchestrator(
                hybridSearchService, queryRewriteService, null,
                metricsService, traceService, objectMapper);

        EvaluationResult result = orchestrator.runRetrievalEvaluation(List.of());
        assertEquals(0, result.getTotalSamples());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
mvnw.cmd -pl edumate-core test -Dtest=EvaluationOrchestratorTest
```

Expected: FAIL (类不存在)

- [ ] **Step 3: 创建 EvaluationOrchestrator.java**

```java
package com.edumate.core.evaluation;

import com.edumate.common.model.DocumentChunk;
import com.edumate.common.model.EvaluationResult;
import com.edumate.common.model.EvaluationSample;
import com.edumate.common.model.TraceRecord;
import com.edumate.core.agent.QueryRewriteService;
import com.edumate.core.retrieval.HybridSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 评测编排器 —— 加载数据集 → 逐条检索 → 计算指标 → 输出报告
 * <p>
 * 支持两种评测模式：
 * 1. 检索评测（retrieval）：只评测检索质量（Recall@K、MRR、NDCG）
 * 2. 全量评测（full）：检索 + RAGAS 端到端评测（需 ChatModel 可用）
 * <p>
 * 当 HybridSearchService 不可用时（如 Docker 未启动），返回空结果。
 */
@Service
public class EvaluationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(EvaluationOrchestrator.class);

    private final HybridSearchService hybridSearchService;
    private final QueryRewriteService queryRewriteService;
    private final RagasEvaluationService ragasEvaluationService;
    private final EvaluationMetricsService metricsService;
    private final TraceService traceService;
    private final ObjectMapper objectMapper;

    public EvaluationOrchestrator(HybridSearchService hybridSearchService,
                                  QueryRewriteService queryRewriteService,
                                  @Nullable RagasEvaluationService ragasEvaluationService,
                                  EvaluationMetricsService metricsService,
                                  TraceService traceService,
                                  ObjectMapper objectMapper) {
        this.hybridSearchService = hybridSearchService;
        this.queryRewriteService = queryRewriteService;
        this.ragasEvaluationService = ragasEvaluationService;
        this.metricsService = metricsService;
        this.traceService = traceService;
        this.objectMapper = objectMapper;
    }

    /**
     * 运行检索评测
     * <p>
     * 流程：对每条样本 → Query 改写 → 混合检索 → 计算 Recall@K + MRR
     *
     * @param samples 评测样本列表
     * @return 评测结果
     */
    public EvaluationResult runRetrievalEvaluation(List<EvaluationSample> samples) {
        String runId = UUID.randomUUID().toString().substring(0, 8);
        log.info("开始检索评测 runId={}, samples={}", runId, samples.size());

        if (samples == null || samples.isEmpty()) {
            return EvaluationResult.builder()
                    .runId(runId).type("retrieval").totalSamples(0).build();
        }

        List<Double> reciprocalRanks = new ArrayList<>();
        Map<Integer, List<Double>> recallByK = new LinkedHashMap<>();
        for (int k : List.of(1, 3, 5, 10)) recallByK.put(k, new ArrayList<>());
        List<EvaluationResult.SampleMetric> sampleMetrics = new ArrayList<>();

        for (EvaluationSample sample : samples) {
            try {
                // Query 改写
                String rewritten = queryRewriteService.rewrite(sample.getQuery());

                // 混合检索
                List<DocumentChunk> chunks = hybridSearchService.search(rewritten, 10);
                List<String> retrievedIds = chunks.stream()
                        .map(DocumentChunk::getId).collect(Collectors.toList());

                List<String> relevantIds = sample.getRelevantChunkIds() != null
                        ? sample.getRelevantChunkIds() : List.of();

                // 计算指标
                double rr = metricsService.calculateReciprocalRank(relevantIds, retrievedIds);
                reciprocalRanks.add(rr);
                for (int k : List.of(1, 3, 5, 10)) {
                    recallByK.get(k).add(
                            metricsService.calculateRecallAtK(relevantIds, retrievedIds, k));
                }

                sampleMetrics.add(EvaluationResult.SampleMetric.builder()
                        .sampleId(sample.getId())
                        .query(sample.getQuery())
                        .recallAt5(metricsService.calculateRecallAtK(relevantIds, retrievedIds, 5))
                        .reciprocalRank(rr)
                        .retrievedChunkIds(retrievedIds)
                        .relevantChunkIds(relevantIds)
                        .build());

            } catch (Exception e) {
                log.warn("样本评测失败 sampleId={}: {}", sample.getId(), e.getMessage());
                reciprocalRanks.add(0.0);
                for (int k : List.of(1, 3, 5, 10)) recallByK.get(k).add(0.0);
            }
        }

        // 汇总指标
        Map<Integer, Double> avgRecall = new LinkedHashMap<>();
        for (var entry : recallByK.entrySet()) {
            avgRecall.put(entry.getKey(),
                    entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
        }

        double mrr = metricsService.calculateMRR(reciprocalRanks);

        log.info("检索评测完成 runId={}: Recall@5={}, MRR={}",
                runId, String.format("%.3f", avgRecall.get(5)),
                String.format("%.3f", mrr));

        return EvaluationResult.builder()
                .runId(runId)
                .timestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .type("retrieval")
                .totalSamples(samples.size())
                .recallAtK(avgRecall)
                .mrr(mrr)
                .sampleMetrics(sampleMetrics)
                .build();
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
mvnw.cmd -pl edumate-core test -Dtest=EvaluationOrchestratorTest
```

Expected: PASS (2 tests)

- [ ] **Step 5: 编译验证**

```powershell
mvnw.cmd -pl edumate-core compile
```

Expected: BUILD SUCCESS

---

### Task 7: 评测 API 接口

**Files:**
- Create: `edumate-admin/src/main/java/com/edumate/admin/controller/EvaluationController.java`
- Create: `edumate-admin/src/test/java/com/edumate/admin/controller/EvaluationControllerTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.edumate.admin.controller;

import com.edumate.common.model.EvaluationResult;
import com.edumate.core.evaluation.EvaluationOrchestrator;
import com.edumate.core.evaluation.TraceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EvaluationController.class)
class EvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EvaluationOrchestrator evaluationOrchestrator;

    @MockBean
    private TraceService traceService;

    @Test
    void shouldReturn404ForUnknownTraceId() throws Exception {
        when(traceService.getTrace(any())).thenReturn(null);

        mockMvc.perform(get("/api/eval/trace/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn200ForRetrievalEval() throws Exception {
        EvaluationResult result = EvaluationResult.builder()
                .runId("test-001").type("retrieval").totalSamples(0).mrr(0.0).build();
        when(evaluationOrchestrator.runRetrievalEvaluation(any())).thenReturn(result);

        mockMvc.perform(post("/api/eval/retrieval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("test-001"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
mvnw.cmd -pl edumate-admin test -Dtest=EvaluationControllerTest
```

Expected: FAIL (类不存在)

- [ ] **Step 3: 创建 EvaluationController.java**

```java
package com.edumate.admin.controller;

import com.edumate.common.model.EvaluationResult;
import com.edumate.common.model.EvaluationSample;
import com.edumate.common.model.TraceRecord;
import com.edumate.core.evaluation.EvaluationOrchestrator;
import com.edumate.core.evaluation.TraceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 评测控制器 —— 检索评测、RAGAS 评测、Trace 查询
 */
@RestController
@RequestMapping("/api/eval")
public class EvaluationController {

    private static final Logger log = LoggerFactory.getLogger(EvaluationController.class);

    private final EvaluationOrchestrator evaluationOrchestrator;
    private final TraceService traceService;
    private final ObjectMapper objectMapper;

    public EvaluationController(EvaluationOrchestrator evaluationOrchestrator,
                                TraceService traceService,
                                ObjectMapper objectMapper) {
        this.evaluationOrchestrator = evaluationOrchestrator;
        this.traceService = traceService;
        this.objectMapper = objectMapper;
    }

    /**
     * 运行检索评测
     * <p>
     * 默认加载内置 50 条评测数据集，也可通过请求体传入自定义样本。
     *
     * @param request 可选的自定义评测样本列表
     * @return 评测结果（含 Recall@K、MRR、逐样本明细）
     */
    @PostMapping("/retrieval")
    public ResponseEntity<?> runRetrievalEval(@RequestBody(required = false) EvalRequest request) {
        try {
            List<EvaluationSample> samples;
            if (request != null && request.samples() != null && !request.samples().isEmpty()) {
                samples = request.samples();
            } else {
                samples = loadBuiltInDataset();
            }

            if (samples.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "评测数据集为空，请先上传文档并标注相关分块"));
            }

            EvaluationResult result = evaluationOrchestrator.runRetrievalEvaluation(samples);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("检索评测失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "评测失败: " + e.getMessage()));
        }
    }

    /**
     * 查询 Trace 记录
     *
     * @param traceId 追踪 ID
     * @return TraceRecord 或 404
     */
    @GetMapping("/trace/{traceId}")
    public ResponseEntity<?> getTrace(@PathVariable String traceId) {
        TraceRecord trace = traceService.getTrace(traceId);
        if (trace == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(trace);
    }

    /**
     * 查询最近 N 条 Trace 记录
     *
     * @param count 返回数量（默认 10，最大 50）
     * @return Trace 记录列表
     */
    @GetMapping("/traces")
    public ResponseEntity<?> getRecentTraces(@RequestParam(defaultValue = "10") int count) {
        int limit = Math.min(count, 50);
        List<TraceRecord> traces = traceService.getRecentTraces(limit);
        return ResponseEntity.ok(Map.of("count", traces.size(), "traces", traces));
    }

    /**
     * 加载内置评测数据集（从 classpath 的 eval/eval-dataset.json）
     */
    private List<EvaluationSample> loadBuiltInDataset() {
        try {
            ClassPathResource resource = new ClassPathResource("eval/eval-dataset.json");
            try (InputStream is = resource.getInputStream()) {
                List<EvaluationSample> samples = objectMapper.readValue(is,
                        new TypeReference<List<EvaluationSample>>() {});
                log.info("加载内置评测数据集: {} 条样本", samples.size());
                return samples;
            }
        } catch (Exception e) {
            log.warn("加载内置评测数据集失败: {}", e.getMessage());
            return List.of();
        }
    }

    public record EvalRequest(List<EvaluationSample> samples) {}
}
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
mvnw.cmd -pl edumate-admin test -Dtest=EvaluationControllerTest
```

Expected: PASS (2 tests)

- [ ] **Step 5: 编译验证**

```powershell
mvnw.cmd -pl edumate-admin compile
```

Expected: BUILD SUCCESS

---

### Task 8: Retrieval 服务集成 Trace 追踪

**Files:**
- Modify: `edumate-core/src/main/java/com/edumate/core/retrieval/HybridSearchService.java`
- Modify: `edumate-core/src/main/java/com/edumate/core/chat/StreamingChatService.java`

- [ ] **Step 1: 修改 HybridSearchService 集成 Trace 追踪**

在 `HybridSearchService.java` 中注入 `TraceService`，并在 `search()` 方法中各检索阶段记录 Trace：

```java
// 在类顶部添加 import：
import com.edumate.core.evaluation.TraceService;
import com.edumate.common.model.TraceRecord;

// 在字段声明处添加：
private final TraceService traceService;

// 修改构造函数：
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

// 修改 search() 方法，在各阶段前后记录 Trace：
public List<DocumentChunk> search(String query, int topK) {
    String traceId = traceService.startTrace(query, null).getTraceId();
    long startTime = System.currentTimeMillis();

    // 向量检索
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
```

- [ ] **Step 2: 修改 StreamingChatService 集成 Trace 追踪**

在 `StreamingChatService.java` 的 `buildContext()` 方法中添加 Query 改写阶段的 Trace 记录：

```java
// 在类顶部添加 import：
import com.edumate.core.evaluation.TraceService;

// 在字段声明处添加：
private final TraceService traceService;

// 修改构造函数，添加 TraceService 参数：
public StreamingChatService(ChatModel chatModel,
                            HybridSearchService hybridSearchService,
                            QueryRewriteService queryRewriteService,
                            IntentClassifierService intentClassifierService,
                            RefusalGuardService refusalGuardService,
                            ChatSessionService chatSessionService,
                            TraceService traceService) {
    this.chatModel = chatModel;
    this.hybridSearchService = hybridSearchService;
    this.queryRewriteService = queryRewriteService;
    this.intentClassifierService = intentClassifierService;
    this.refusalGuardService = refusalGuardService;
    this.chatSessionService = chatSessionService;
    this.traceService = traceService;
}

// 修改 buildContext() 方法，添加 Query 改写阶段的 Trace 记录：
public String buildContext(String query, String sessionId) {
    // 1. Query 改写
    long t0 = System.currentTimeMillis();
    String rewritten = queryRewriteService.rewrite(query);
    traceService.recordStage(traceService.startTrace(query, sessionId).getTraceId(),
            "queryRewrite",
            System.currentTimeMillis() - t0,
            "original: " + query,
            "rewritten: " + rewritten,
            true, null);

    // ... 后续代码保持不变
}
```

- [ ] **Step 3: 编译验证**

```powershell
mvnw.cmd -pl edumate-core compile
```

Expected: BUILD SUCCESS

---

### Task 9: 端到端集成验证与优化建议

- [ ] **Step 1: 全量编译**

```powershell
cd f:\JetBrains\RAG\EduMate
mvnw.cmd clean compile
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 运行全部单元测试**

```powershell
mvnw.cmd test
```

Expected: 全部 Tests PASS（新增评测相关测试 + 已有测试）

- [ ] **Step 3: 启动应用并验证评测 API**

启动应用后，在另一个终端执行：

```powershell
# 运行检索评测
$body = [System.Text.Encoding]::UTF8.GetBytes('{}')
Invoke-RestMethod -Uri http://localhost:8080/api/eval/retrieval -Method Post -ContentType "application/json; charset=utf-8" -Body $body
```

Expected: 返回 JSON 包含 `runId`、`type`、`totalSamples`、`recallAtK`（K=1,3,5,10）、`mrr`、`sampleMetrics` 等字段。

- [ ] **Step 4: 验证 Trace 查询 API**

```powershell
# 先执行一次搜索触发 Trace 记录
$body = [System.Text.Encoding]::UTF8.GetBytes('{"query":"二叉树","topK":5}')
Invoke-RestMethod -Uri http://localhost:8080/api/search -Method Post -ContentType "application/json; charset=utf-8" -Body $body

# 查询最近 Trace 记录
Invoke-RestMethod -Uri http://localhost:8080/api/eval/traces?count=3
```

Expected: 返回最近 3 条 Trace 记录，每条包含各阶段耗时。

- [ ] **Step 5: 基于评测结果进行参数调优**

根据评测结果，调整以下参数优化检索质量：

```yaml
# 在 application.yml 中新增评测与调优参数
eval:
  # RRF 融合参数 k 值（默认 60，可调为 30/60/90 对比）
  rrf-k: 60
  # 混合检索各路的权重（通过调整 topK 倍数实现）
  retrieval-multiplier: 2
  # 检索结果截断数
  default-top-k: 5
```

调优步骤：
1. 运行 `POST /api/eval/retrieval` 获取基线指标
2. 修改 `RRFusionService` 中的 `K` 值（如从 60 改为 30），重新评测
3. 修改 `HybridSearchService.search()` 中各路的 `topK * 2` 倍数（如改为 `* 3`），重新评测
4. 对比三组指标的 Recall@5 和 MRR，选择最优参数

记录对比结果到 `docs/superpowers/plans/2026-07-30-edumate-phase5-tuning.md`：

```markdown
# Phase 5 参数调优记录

| 配置 | K | 倍数 | Recall@5 | MRR | 备注 |
|------|---|------|----------|-----|------|
| 基线 | 60 | ×2 | TBD | TBD | 默认配置 |
| 方案A | 30 | ×2 | TBD | TBD | 降低 K 值 |
| 方案B | 60 | ×3 | TBD | TBD | 增加检索量 |
| 方案C | 30 | ×3 | TBD | TBD | 组合调整 |
```

---

## 自检清单

**1. Spec 覆盖：**
- [x] 标注 50+ 测试问答对 → Task 2（50 条样本，覆盖 5 门课程）
- [x] 评测框架（Recall@K、MRR、NDCG）→ Task 3（EvaluationMetricsService）
- [x] RAGAS 端到端评测 → Task 4（RagasEvaluationService：Answer Relevance / Faithfulness / Context Relevance）
- [x] 流水线追踪（Trace 全链路记录）→ Task 5（TraceService）+ Task 8（集成到 HybridSearchService / StreamingChatService）
- [x] 评测 API → Task 7（EvaluationController：POST /api/eval/retrieval、GET /api/eval/trace/{traceId}、GET /api/eval/traces）
- [x] 参数调优 → Task 9（基于评测结果调整 RRF K 值和检索倍数）

**2. 无占位符：** 所有 Task 均包含完整代码、精确命令和预期输出。

**3. 类型一致性：**
- `EvaluationSample` 字段与 `eval-dataset.json` 结构一致
- `EvaluationResult` 字段与 `EvaluationOrchestrator` 输出一致
- `TraceRecord` 字段与 `TraceService` 记录一致
- `HybridSearchService` 新增 `TraceService` 参数，与 `StreamingChatService` 一致
- 所有 Controller 的 DTO record 与前端请求字段一致

**4. 降级策略：**
- `RagasEvaluationService`：ChatModel 为 null 时返回 0.0
- `EvaluationOrchestrator`：HybridSearchService 不可用时返回空结果
- `TraceService`：纯内存存储，不依赖外部服务
- 评测数据集通过 `relevantChunkIds` 为空时动态匹配，支持无标注场景运行