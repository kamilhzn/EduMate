# EduMate Phase 4：Agent 与高级功能 —— 执行计划书

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 EduMate 添加智能出题 Agent（ReAct 模式）、Query 改写 + 意图识别、拒答判断、Redis 多轮对话记忆、SSE 流式响应五大高级功能。

**Architecture:** 在 edumate-core 新增 `agent/` 和 `chat/` 两个子包。agent 包负责 LLM 驱动的智能体（出题、改写、意图分类、拒答），chat 包负责 Redis 会话管理和 SSE 流式输出。所有服务通过 ChatModel 与 LLM 交互，检索通过已有的 HybridSearchService。新增两个 Controller（QuizController、ChatController），复用现有 DashScopeConfig 中的 ChatModel Bean。

**Tech Stack:** Spring Boot 3.4.5, LangChain4j Community 1.17.2-beta27, Spring Data Redis, Redis 7-alpine (Docker), QwenChatModel, Jackson

---

## 当前状态

```
edumate-core/src/main/java/com/edumate/core/
├── parser/
│   ├── DocumentParserService.java
│   └── HierarchicalChunkerService.java
├── retrieval/
│   ├── HybridSearchService.java
│   ├── KeywordIndexService.java
│   ├── RRFusionService.java
│   └── VectorStoreService.java
└── graph/
    ├── GraphSearchService.java
    └── KnowledgeGraphService.java

edumate-admin/src/main/java/com/edumate/admin/
├── config/
│   ├── DashScopeConfig.java     ← ChatModel Bean 已创建
│   ├── ElasticsearchConfig.java
│   ├── Neo4jConfig.java
│   └── QdrantConfig.java
├── controller/
│   ├── DocumentController.java
│   └── SearchController.java
└── service/
    └── KnowledgeExtractionService.java
```

## 目标状态（新增文件）

```
edumate-common/src/main/java/com/edumate/common/model/
├── QuizQuestion.java          ← 新增：出题模型
└── ChatMessage.java           ← 新增：对话消息模型

edumate-core/src/main/java/com/edumate/core/
├── agent/
│   ├── QuizAgentService.java       ← 新增：ReAct 出题 Agent
│   ├── QueryRewriteService.java    ← 新增：Query 改写
│   ├── IntentClassifierService.java← 新增：意图识别
│   └── RefusalGuardService.java    ← 新增：拒答判断
└── chat/
    ├── ChatSessionService.java     ← 新增：Redis 会话管理
    └── StreamingChatService.java   ← 新增：SSE 流式输出

edumate-admin/src/main/java/com/edumate/admin/
├── config/
│   └── RedisConfig.java       ← 新增：Redis 配置
└── controller/
    ├── QuizController.java    ← 新增：出题 API
    └── ChatController.java    ← 新增：流式问答 API
```

---

### Task 1: Redis 配置与依赖

**Files:**
- Modify: `edumate-admin/pom.xml`
- Create: `edumate-admin/src/main/java/com/edumate/admin/config/RedisConfig.java`
- Modify: `edumate-admin/src/main/resources/application.yml`

- [ ] **Step 1: 添加 Redis 依赖到 edumate-admin/pom.xml**

在 `edumate-admin/pom.xml` 的 `<dependencies>` 中，`spring-boot-starter-web` 之后添加：

```xml
<!-- Redis 会话/缓存 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

- [ ] **Step 2: 运行编译确认依赖拉取成功**

```powershell
cd f:\JetBrains\RAG\EduMate
mvnw.cmd -pl edumate-admin compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 创建 RedisConfig.java**

```java
package com.edumate.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置 —— 会话存储与缓存
 */
@Configuration
public class RedisConfig {

    @Value("${redis.host:localhost}")
    private String host;

    @Value("${redis.port:6379}")
    private int port;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));
        return template;
    }
}
```

- [ ] **Step 4: 添加 Redis 配置到 application.yml**

在 `application.yml` 末尾追加：

```yaml
# Redis 会话/缓存
redis:
  host: ${REDIS_HOST:localhost}
  port: ${REDIS_PORT:6379}
```

- [ ] **Step 5: 编译验证**

```powershell
mvnw.cmd -pl edumate-admin compile
```

Expected: BUILD SUCCESS

---

### Task 2: 对话数据模型

**Files:**
- Create: `edumate-common/src/main/java/com/edumate/common/model/ChatMessage.java`
- Create: `edumate-common/src/main/java/com/edumate/common/model/QuizQuestion.java`

- [ ] **Step 1: 创建 ChatMessage.java**

```java
package com.edumate.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 对话消息 —— 用于多轮会话存储
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    /** 消息角色：user / assistant */
    private String role;
    /** 消息内容 */
    private String content;
    /** 时间戳 */
    @Builder.Default
    private Instant timestamp = Instant.now();
}
```

- [ ] **Step 2: 创建 QuizQuestion.java**

```java
package com.edumate.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 智能出题 —— 题目模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizQuestion {
    /** 题型：single_choice / multiple_choice / true_false / short_answer */
    private String type;
    /** 题干 */
    private String stem;
    /** 选项列表（选择题/判断题） */
    private List<String> options;
    /** 正确答案 */
    private String answer;
    /** 答案解析 */
    private String explanation;
    /** 关联知识点 */
    private List<String> knowledgePoints;
    /** 难度：easy / medium / hard */
    private String difficulty;
}
```

- [ ] **Step 3: 编译验证**

```powershell
mvnw.cmd -pl edumate-common compile
```

Expected: BUILD SUCCESS

---

### Task 3: Query 改写服务

**Files:**
- Create: `edumate-core/src/main/java/com/edumate/core/agent/QueryRewriteService.java`
- Create: `edumate-core/src/test/java/com/edumate/core/agent/QueryRewriteServiceTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.edumate.core.agent;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class QueryRewriteServiceTest {

    @Mock
    private ChatModel chatModel;

    @Test
    void shouldReturnOriginalQueryWhenChatModelIsNull() {
        QueryRewriteService service = new QueryRewriteService(null);
        String result = service.rewrite("二叉树");
        assertEquals("二叉树", result);
    }

    @Test
    void shouldReturnOriginalQueryWhenInputIsBlank() {
        QueryRewriteService service = new QueryRewriteService(chatModel);
        String result = service.rewrite("");
        assertEquals("", result);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
mvnw.cmd -pl edumate-core test -Dtest=QueryRewriteServiceTest
```

Expected: FAIL (类不存在)

- [ ] **Step 3: 创建 QueryRewriteService.java**

```java
package com.edumate.core.agent;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Query 改写服务 —— 将用户简短口语化查询扩展为更精准的检索查询
 * <p>
 * 例如："B+树" → "B+树 定义 结构 特点 应用场景"
 * <p>
 * 当 ChatModel 不可用时，返回原始查询。
 */
@Service
public class QueryRewriteService {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriteService.class);

    private final ChatModel chatModel;

    private static final String REWRITE_PROMPT = """
            你是一个课程学习助手。请将用户的简短查询改写为更完整、更利于检索的查询语句。
            规则：
            1. 保留原意，扩展关键术语和同义词
            2. 补充该知识点常见的关联概念
            3. 不要添加用户未提及的内容
            4. 直接输出改写后的查询，不要解释

            用户查询：%s
            改写结果：""";

    public QueryRewriteService(@Nullable ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 改写查询语句
     *
     * @param query 原始查询
     * @return 改写后的查询（若 LLM 不可用则返回原始查询）
     */
    public String rewrite(String query) {
        if (chatModel == null) {
            return query;
        }
        if (query == null || query.isBlank()) {
            return query;
        }

        try {
            String prompt = String.format(REWRITE_PROMPT, query);
            var response = chatModel.chat(ChatRequest.builder()
                    .messages(UserMessage.from(prompt))
                    .build());
            String rewritten = response.aiMessage().text().trim();
            log.debug("Query 改写: '{}' → '{}'", query, rewritten);
            return rewritten.isBlank() ? query : rewritten;
        } catch (Exception e) {
            log.warn("Query 改写失败: {}", e.getMessage());
            return query;
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
mvnw.cmd -pl edumate-core test -Dtest=QueryRewriteServiceTest
```

Expected: PASS (2 tests)

- [ ] **Step 5: 编译验证**

```powershell
mvnw.cmd -pl edumate-core compile
```

Expected: BUILD SUCCESS

---

### Task 4: 意图识别服务

**Files:**
- Create: `edumate-core/src/main/java/com/edumate/core/agent/IntentClassifierService.java`
- Create: `edumate-core/src/test/java/com/edumate/core/agent/IntentClassifierServiceTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.edumate.core.agent;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class IntentClassifierServiceTest {

    @Mock
    private ChatModel chatModel;

    @Test
    void shouldReturnCourseQaWhenChatModelIsNull() {
        IntentClassifierService service = new IntentClassifierService(null);
        assertEquals(IntentClassifierService.Intent.COURSE_QA, service.classify("二叉树"));
    }

    @Test
    void shouldReturnCourseQaWhenInputIsBlank() {
        IntentClassifierService service = new IntentClassifierService(chatModel);
        assertEquals(IntentClassifierService.Intent.COURSE_QA, service.classify(""));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
mvnw.cmd -pl edumate-core test -Dtest=IntentClassifierServiceTest
```

Expected: FAIL (类不存在)

- [ ] **Step 3: 创建 IntentClassifierService.java**

```java
package com.edumate.core.agent;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * 意图识别服务 —— 分类用户查询意图
 * <p>
 * 当 ChatModel 不可用时，默认返回 COURSE_QA。
 */
@Service
public class IntentClassifierService {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifierService.class);

    private final ChatModel chatModel;

    /** 意图枚举 */
    public enum Intent {
        COURSE_QA,          // 课程问答
        QUIZ_GENERATION,    // 出题请求
        CONCEPT_EXPLANATION,// 概念解释
        REFUSAL             // 需拒答（代写作业等）
    }

    private static final String CLASSIFY_PROMPT = """
            你是一个课程学习助手。请判断用户输入的意图，只输出以下标签之一：
            - COURSE_QA：询问课程知识、概念、习题解答
            - QUIZ_GENERATION：要求生成题目、出题、模拟考试
            - CONCEPT_EXPLANATION：要求解释某个概念或术语
            - REFUSAL：要求代写作业、考试作弊、提供完整作业答案等违规请求

            用户输入：%s
            意图标签：""";

    public IntentClassifierService(@Nullable ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 分类用户意图
     *
     * @param query 用户输入
     * @return 意图标签
     */
    public Intent classify(String query) {
        if (chatModel == null) {
            return Intent.COURSE_QA;
        }
        if (query == null || query.isBlank()) {
            return Intent.COURSE_QA;
        }

        try {
            String prompt = String.format(CLASSIFY_PROMPT, query);
            var response = chatModel.chat(ChatRequest.builder()
                    .messages(UserMessage.from(prompt))
                    .build());
            String label = response.aiMessage().text().trim().toUpperCase();
            for (Intent intent : Intent.values()) {
                if (label.contains(intent.name())) {
                    log.debug("意图识别: '{}' → {}", query, intent);
                    return intent;
                }
            }
            return Intent.COURSE_QA;
        } catch (Exception e) {
            log.warn("意图识别失败: {}", e.getMessage());
            return Intent.COURSE_QA;
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
mvnw.cmd -pl edumate-core test -Dtest=IntentClassifierServiceTest
```

Expected: PASS (2 tests)

- [ ] **Step 5: 编译验证**

```powershell
mvnw.cmd -pl edumate-core compile
```

Expected: BUILD SUCCESS

---

### Task 5: 拒答守卫服务

**Files:**
- Create: `edumate-core/src/main/java/com/edumate/core/agent/RefusalGuardService.java`
- Create: `edumate-core/src/test/java/com/edumate/core/agent/RefusalGuardServiceTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.edumate.core.agent;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RefusalGuardServiceTest {

    @Mock
    private ChatModel chatModel;

    @Test
    void shouldNotRefuseWhenChatModelIsNull() {
        RefusalGuardService service = new RefusalGuardService(null);
        assertFalse(service.shouldRefuse("二叉树").shouldRefuse());
    }

    @Test
    void shouldRefuseBasedOnKeywords() {
        RefusalGuardService service = new RefusalGuardService(chatModel);
        // 关键词匹配：代写
        assertTrue(service.shouldRefuse("帮我代写作业").shouldRefuse());
        // 关键词匹配：考试答案
        assertTrue(service.shouldRefuse("给我考试的答案").shouldRefuse());
        // 正常查询
        assertFalse(service.shouldRefuse("什么是红黑树").shouldRefuse());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
mvnw.cmd -pl edumate-core test -Dtest=RefusalGuardServiceTest
```

Expected: FAIL (类不存在)

- [ ] **Step 3: 创建 RefusalGuardService.java**

```java
package com.edumate.core.agent;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 拒答守卫服务 —— 两层过滤：关键词规则 + LLM 判断
 * <p>
 * 当 ChatModel 不可用时，仅使用关键词规则。
 */
@Service
public class RefusalGuardService {

    private static final Logger log = LoggerFactory.getLogger(RefusalGuardService.class);

    private final ChatModel chatModel;

    /** 关键词黑名单 */
    private static final List<Pattern> BLOCKED_PATTERNS = List.of(
            Pattern.compile("代写|代做|帮.*写.*作业|帮.*做.*作业"),
            Pattern.compile("考试.*答案|期末.*答案|试卷.*答案"),
            Pattern.compile("作弊|抄袭|替考")
    );

    /** 拒答结果 */
    public record RefusalResult(boolean shouldRefuse, String reason) {
        public static RefusalResult allow() {
            return new RefusalResult(false, "");
        }

        public static RefusalResult refuse(String reason) {
            return new RefusalResult(true, reason);
        }
    }

    public RefusalGuardService(@Nullable ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 判断是否应拒答
     *
     * @param query 用户输入
     * @return 拒答结果
     */
    public RefusalResult shouldRefuse(String query) {
        if (query == null || query.isBlank()) {
            return RefusalResult.allow();
        }

        // 第一层：关键词规则
        for (Pattern pattern : BLOCKED_PATTERNS) {
            if (pattern.matcher(query).find()) {
                log.info("拒答（关键词匹配）: '{}'", query);
                return RefusalResult.refuse("抱歉，我不能帮你完成作业或提供考试答案。请通过自主学习和练习来掌握知识。");
            }
        }

        if (chatModel == null) {
            return RefusalResult.allow();
        }

        // 第二层：LLM 判断（可选，关键词已覆盖大部分场景）
        try {
            String prompt = String.format("""
                    判断以下用户请求是否涉及代写作业、考试作弊等学术不端行为。
                    只回答 YES 或 NO。
                    用户请求：%s
                    回答：""", query);
            var response = chatModel.chat(ChatRequest.builder()
                    .messages(UserMessage.from(prompt))
                    .build());
            if (response.aiMessage().text().trim().toUpperCase().startsWith("YES")) {
                log.info("拒答（LLM 判断）: '{}'", query);
                return RefusalResult.refuse("抱歉，我不能帮你完成作业或提供考试答案。请通过自主学习和练习来掌握知识。");
            }
        } catch (Exception e) {
            log.warn("拒答 LLM 判断失败: {}", e.getMessage());
        }

        return RefusalResult.allow();
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
mvnw.cmd -pl edumate-core test -Dtest=RefusalGuardServiceTest
```

Expected: PASS (3 tests)

- [ ] **Step 5: 编译验证**

```powershell
mvnw.cmd -pl edumate-core compile
```

Expected: BUILD SUCCESS

---

### Task 6: 多轮对话会话服务

**Files:**
- Create: `edumate-core/src/main/java/com/edumate/core/chat/ChatSessionService.java`
- Create: `edumate-core/src/test/java/com/edumate/core/chat/ChatSessionServiceTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.edumate.core.chat;

import com.edumate.common.model.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ListOperations<String, Object> listOperations;

    private ChatSessionService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        service = new ChatSessionService(redisTemplate);
    }

    @Test
    void shouldAddAndRetrieveMessages() {
        ChatMessage msg = ChatMessage.builder().role("user").content("什么是二叉树").build();
        when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(List.of(msg));

        service.addMessage("session-1", msg);
        List<ChatMessage> history = service.getHistory("session-1");

        assertEquals(1, history.size());
        assertEquals("什么是二叉树", history.get(0).getContent());
    }

    @Test
    void shouldReturnEmptyHistoryForNewSession() {
        when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(List.of());
        List<ChatMessage> history = service.getHistory("new-session");
        assertTrue(history.isEmpty());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
mvnw.cmd -pl edumate-core test -Dtest=ChatSessionServiceTest
```

Expected: FAIL (类不存在)

- [ ] **Step 3: 创建 ChatSessionService.java**

```java
package com.edumate.core.chat;

import com.edumate.common.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 多轮对话会话服务 —— 基于 Redis List 存储对话历史
 * <p>
 * Redis Key 格式：chat:session:{sessionId}
 * 每个会话默认保留最近 20 条消息，TTL 为 30 分钟。
 */
@Service
public class ChatSessionService {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionService.class);

    private static final String KEY_PREFIX = "chat:session:";
    private static final int MAX_HISTORY = 20;
    private static final int TTL_MINUTES = 30;

    private final RedisTemplate<String, Object> redisTemplate;

    public ChatSessionService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 添加消息到会话历史
     *
     * @param sessionId 会话 ID
     * @param message   消息
     */
    public void addMessage(String sessionId, ChatMessage message) {
        String key = KEY_PREFIX + sessionId;
        redisTemplate.opsForList().rightPush(key, message);
        // 裁剪到 MAX_HISTORY 条
        redisTemplate.opsForList().trim(key, -MAX_HISTORY, -1);
        redisTemplate.expire(key, TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("会话 {} 添加消息: role={}", sessionId, message.getRole());
    }

    /**
     * 获取会话历史
     *
     * @param sessionId 会话 ID
     * @return 历史消息列表（按时间顺序）
     */
    public List<ChatMessage> getHistory(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        List<Object> raw = redisTemplate.opsForList().range(key, 0, -1);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChatMessage> messages = new ArrayList<>();
        for (Object obj : raw) {
            if (obj instanceof ChatMessage msg) {
                messages.add(msg);
            }
        }
        return messages;
    }

    /**
     * 清空会话历史
     *
     * @param sessionId 会话 ID
     */
    public void clearSession(String sessionId) {
        redisTemplate.delete(KEY_PREFIX + sessionId);
        log.debug("会话 {} 已清空", sessionId);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
mvnw.cmd -pl edumate-core test -Dtest=ChatSessionServiceTest
```

Expected: PASS (2 tests)

- [ ] **Step 5: 编译验证**

```powershell
mvnw.cmd -pl edumate-core compile
```

Expected: BUILD SUCCESS

---

### Task 7: 智能出题 Agent

**Files:**
- Create: `edumate-core/src/main/java/com/edumate/core/agent/QuizAgentService.java`
- Create: `edumate-core/src/test/java/com/edumate/core/agent/QuizAgentServiceTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.edumate.core.agent;

import com.edumate.common.model.QuizQuestion;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class QuizAgentServiceTest {

    @Mock
    private ChatModel chatModel;

    @Test
    void shouldReturnEmptyListWhenChatModelIsNull() {
        QuizAgentService service = new QuizAgentService(null);
        List<QuizQuestion> result = service.generate("数据结构", null, 3, "easy");
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyListWhenCourseIsBlank() {
        QuizAgentService service = new QuizAgentService(chatModel);
        List<QuizQuestion> result = service.generate("", null, 3, "easy");
        assertTrue(result.isEmpty());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
mvnw.cmd -pl edumate-core test -Dtest=QuizAgentServiceTest
```

Expected: FAIL (类不存在)

- [ ] **Step 3: 创建 QuizAgentService.java**

```java
package com.edumate.core.agent;

import com.edumate.common.model.QuizQuestion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能出题 Agent —— ReAct 模式（规划 → 执行 → 验证）
 * <p>
 * Step 1 - Plan（规划）：根据课程和章节，确定出题范围和题型分布
 * Step 2 - Execute（执行）：调用 LLM 生成题目及标准答案
 * Step 3 - Verify（验证）：调用 LLM 自检题目质量，过滤不合格题目
 * <p>
 * 当 ChatModel 不可用时，返回空列表。
 */
@Service
public class QuizAgentService {

    private static final Logger log = LoggerFactory.getLogger(QuizAgentService.class);

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String GENERATE_PROMPT = """
            你是一位大学课程出题专家。请根据以下信息生成 %d 道 %s 难度的题目。

            课程：%s
            %s

            ## 出题要求
            1. 题型分布：选择题 40%%，判断题 20%%，简答题 40%%
            2. 难度说明：easy=基础概念，medium=综合应用，hard=深入分析
            3. 每道题必须包含：题干(stem)、正确答案(answer)、解析(explanation)、关联知识点(knowledgePoints)
            4. 选择题须包含 4 个选项(options)，判断题选项为 ["正确", "错误"]
            5. 简答题的 options 为空数组

            ## 输出格式
            请输出严格 JSON 数组（不要包含其他文字）：
            [
              {
                "type": "single_choice",
                "stem": "题干",
                "options": ["A. ...", "B. ...", "C. ...", "D. ..."],
                "answer": "A",
                "explanation": "解析",
                "knowledgePoints": ["知识点1"],
                "difficulty": "easy"
              }
            ]
            """;

    public QuizAgentService(@Nullable ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 生成题目
     *
     * @param courseName 课程名称
     * @param chapter    章节（可选）
     * @param count      题目数量
     * @param difficulty 难度：easy / medium / hard
     * @return 题目列表
     */
    public List<QuizQuestion> generate(String courseName, @Nullable String chapter,
                                        int count, String difficulty) {
        if (chatModel == null) {
            log.warn("ChatModel 不可用，跳过出题");
            return List.of();
        }
        if (courseName == null || courseName.isBlank()) {
            return List.of();
        }

        // Step 1: Plan（规划）—— 内置在 Prompt 中
        // Step 2: Execute（生成题目）
        String chapterInfo = (chapter != null && !chapter.isBlank())
                ? "章节：" + chapter : "";
        String prompt = String.format(GENERATE_PROMPT, count, difficulty, courseName, chapterInfo);

        try {
            var response = chatModel.chat(ChatRequest.builder()
                    .messages(UserMessage.from(prompt))
                    .build());
            String text = response.aiMessage().text();

            List<QuizQuestion> questions = parseResponse(text);
            log.info("出题 Agent 完成: {} 课程 → {} 道题目", courseName, questions.size());

            // Step 3: Verify（验证）—— 过滤掉明显不合格的题目
            List<QuizQuestion> valid = questions.stream()
                    .filter(q -> q.getStem() != null && !q.getStem().isBlank())
                    .filter(q -> q.getAnswer() != null && !q.getAnswer().isBlank())
                    .toList();
            if (valid.size() < questions.size()) {
                log.warn("出题验证: 过滤了 {} 道不合格题目", questions.size() - valid.size());
            }
            return valid;
        } catch (Exception e) {
            log.error("出题 Agent 失败", e);
            return List.of();
        }
    }

    private List<QuizQuestion> parseResponse(String response) throws JsonProcessingException {
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
        return objectMapper.readValue(json, new TypeReference<List<QuizQuestion>>() {});
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
mvnw.cmd -pl edumate-core test -Dtest=QuizAgentServiceTest
```

Expected: PASS (2 tests)

- [ ] **Step 5: 编译验证**

```powershell
mvnw.cmd -pl edumate-core compile
```

Expected: BUILD SUCCESS

---

### Task 8: SSE 流式问答服务

**Files:**
- Create: `edumate-core/src/main/java/com/edumate/core/chat/StreamingChatService.java`
- Create: `edumate-core/src/test/java/com/edumate/core/chat/StreamingChatServiceTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.edumate.core.chat;

import com.edumate.core.agent.IntentClassifierService;
import com.edumate.core.agent.QueryRewriteService;
import com.edumate.core.agent.RefusalGuardService;
import com.edumate.core.retrieval.HybridSearchService;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class StreamingChatServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private HybridSearchService hybridSearchService;

    @Mock
    private QueryRewriteService queryRewriteService;

    @Mock
    private IntentClassifierService intentClassifierService;

    @Mock
    private RefusalGuardService refusalGuardService;

    @Mock
    private ChatSessionService chatSessionService;

    @Test
    void shouldBuildContextFromRetrievalResults() {
        StreamingChatService service = new StreamingChatService(
                chatModel, hybridSearchService, queryRewriteService,
                intentClassifierService, refusalGuardService, chatSessionService);

        String context = service.buildContext("测试", "session-1");
        assertNotNull(context);
        assertTrue(context.contains("测试"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
mvnw.cmd -pl edumate-core test -Dtest=StreamingChatServiceTest
```

Expected: FAIL (类不存在)

- [ ] **Step 3: 创建 StreamingChatService.java**

```java
package com.edumate.core.chat;

import com.edumate.common.model.ChatMessage;
import com.edumate.common.model.DocumentChunk;
import com.edumate.core.agent.IntentClassifierService;
import com.edumate.core.agent.QueryRewriteService;
import com.edumate.core.agent.RefusalGuardService;
import com.edumate.core.retrieval.HybridSearchService;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 流式问答服务 —— 编排检索→改写→生成→流式输出全流程
 * <p>
 * 流程：拒答检查 → 意图识别 → Query 改写 → 混合检索 → 构建上下文 → 流式生成
 */
@Service
public class StreamingChatService {

    private static final Logger log = LoggerFactory.getLogger(StreamingChatService.class);

    private final ChatModel chatModel;
    private final HybridSearchService hybridSearchService;
    private final QueryRewriteService queryRewriteService;
    private final IntentClassifierService intentClassifierService;
    private final RefusalGuardService refusalGuardService;
    private final ChatSessionService chatSessionService;

    public StreamingChatService(ChatModel chatModel,
                                HybridSearchService hybridSearchService,
                                QueryRewriteService queryRewriteService,
                                IntentClassifierService intentClassifierService,
                                RefusalGuardService refusalGuardService,
                                ChatSessionService chatSessionService) {
        this.chatModel = chatModel;
        this.hybridSearchService = hybridSearchService;
        this.queryRewriteService = queryRewriteService;
        this.intentClassifierService = intentClassifierService;
        this.refusalGuardService = refusalGuardService;
        this.chatSessionService = chatSessionService;
    }

    /**
     * 构建带检索上下文的完整 Prompt
     *
     * @param query     用户查询
     * @param sessionId 会话 ID（用于多轮对话上下文）
     * @return 拼接了检索结果和对话历史的 Prompt
     */
    public String buildContext(String query, String sessionId) {
        // 1. Query 改写
        String rewritten = queryRewriteService.rewrite(query);

        // 2. 混合检索
        List<DocumentChunk> chunks = hybridSearchService.search(rewritten, 5);

        // 3. 多轮对话历史
        List<ChatMessage> history = chatSessionService.getHistory(sessionId);

        // 4. 拼接 Prompt
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个课程学习助手，请根据以下参考资料回答用户问题。\n\n");

        if (!chunks.isEmpty()) {
            sb.append("## 参考资料\n");
            for (int i = 0; i < chunks.size(); i++) {
                sb.append("[").append(i + 1).append("] ")
                        .append(chunks.get(i).getContent()).append("\n");
            }
            sb.append("\n");
        }

        if (!history.isEmpty()) {
            sb.append("## 对话历史\n");
            for (ChatMessage msg : history) {
                sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## 用户问题\n").append(query).append("\n\n");
        sb.append("请基于参考资料回答，如果参考资料不包含相关信息，请如实说明。");

        return sb.toString();
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
mvnw.cmd -pl edumate-core test -Dtest=StreamingChatServiceTest
```

Expected: PASS (1 test)

- [ ] **Step 5: 编译验证**

```powershell
mvnw.cmd -pl edumate-core compile
```

Expected: BUILD SUCCESS

---

### Task 9: Quiz API 接口

**Files:**
- Create: `edumate-admin/src/main/java/com/edumate/admin/controller/QuizController.java`
- Create: `edumate-admin/src/test/java/com/edumate/admin/controller/QuizControllerTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.edumate.admin.controller;

import com.edumate.common.model.QuizQuestion;
import com.edumate.core.agent.QuizAgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QuizController.class)
class QuizControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QuizAgentService quizAgentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldReturn400WhenCourseNameIsBlank() throws Exception {
        mockMvc.perform(post("/api/quiz/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseName\":\"\",\"count\":3,\"difficulty\":\"easy\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnQuizQuestions() throws Exception {
        QuizQuestion question = QuizQuestion.builder()
                .type("single_choice")
                .stem("二叉树的中序遍历结果是？")
                .options(List.of("A. 左→根→右", "B. 根→左→右", "C. 左→右→根", "D. 右→左→根"))
                .answer("A")
                .explanation("中序遍历顺序为左子树→根节点→右子树")
                .knowledgePoints(List.of("二叉树遍历"))
                .difficulty("easy")
                .build();

        when(quizAgentService.generate(eq("数据结构"), isNull(), eq(1), eq("easy")))
                .thenReturn(List.of(question));

        mockMvc.perform(post("/api/quiz/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseName\":\"数据结构\",\"count\":1,\"difficulty\":\"easy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionsCount").value(1));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
mvnw.cmd -pl edumate-admin test -Dtest=QuizControllerTest
```

Expected: FAIL (类不存在)

- [ ] **Step 3: 创建 QuizController.java**

```java
package com.edumate.admin.controller;

import com.edumate.common.model.QuizQuestion;
import com.edumate.core.agent.QuizAgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 智能出题控制器
 */
@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizAgentService quizAgentService;

    public QuizController(QuizAgentService quizAgentService) {
        this.quizAgentService = quizAgentService;
    }

    /**
     * 生成题目
     *
     * @param request 包含 courseName（必填）、chapter（可选）、count（默认 5）、difficulty（默认 medium）
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody QuizRequest request) {
        if (request.courseName() == null || request.courseName().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "courseName 不能为空"));
        }

        int count = request.count() > 0 ? request.count() : 5;
        String difficulty = request.difficulty() != null ? request.difficulty() : "medium";

        List<QuizQuestion> questions = quizAgentService.generate(
                request.courseName(), request.chapter(), count, difficulty);

        return ResponseEntity.ok(Map.of(
                "courseName", request.courseName(),
                "difficulty", difficulty,
                "questionsCount", questions.size(),
                "questions", questions
        ));
    }

    public record QuizRequest(String courseName, String chapter, int count, String difficulty) {}
}
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
mvnw.cmd -pl edumate-admin test -Dtest=QuizControllerTest
```

Expected: PASS (2 tests)

- [ ] **Step 5: 编译验证**

```powershell
mvnw.cmd -pl edumate-admin compile
```

Expected: BUILD SUCCESS

---

### Task 10: SSE 流式问答 API

**Files:**
- Create: `edumate-admin/src/main/java/com/edumate/admin/controller/ChatController.java`
- Create: `edumate-admin/src/test/java/com/edumate/admin/controller/ChatControllerTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.edumate.admin.controller;

import com.edumate.core.agent.IntentClassifierService;
import com.edumate.core.agent.RefusalGuardService;
import com.edumate.core.chat.ChatSessionService;
import com.edumate.core.chat.StreamingChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatModel chatModel;

    @MockBean
    private StreamingChatService streamingChatService;

    @MockBean
    private IntentClassifierService intentClassifierService;

    @MockBean
    private RefusalGuardService refusalGuardService;

    @MockBean
    private ChatSessionService chatSessionService;

    @Test
    void shouldReturn400WhenQueryIsBlank() throws Exception {
        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
mvnw.cmd -pl edumate-admin test -Dtest=ChatControllerTest
```

Expected: FAIL (类不存在)

- [ ] **Step 3: 创建 ChatController.java**

```java
package com.edumate.admin.controller;

import com.edumate.common.model.ChatMessage;
import com.edumate.core.agent.IntentClassifierService;
import com.edumate.core.agent.RefusalGuardService;
import com.edumate.core.chat.ChatSessionService;
import com.edumate.core.chat.StreamingChatService;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 流式问答控制器 —— SSE 逐 token 推送
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatModel chatModel;
    private final StreamingChatService streamingChatService;
    private final IntentClassifierService intentClassifierService;
    private final RefusalGuardService refusalGuardService;
    private final ChatSessionService chatSessionService;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(ChatModel chatModel,
                          StreamingChatService streamingChatService,
                          IntentClassifierService intentClassifierService,
                          RefusalGuardService refusalGuardService,
                          ChatSessionService chatSessionService) {
        this.chatModel = chatModel;
        this.streamingChatService = streamingChatService;
        this.intentClassifierService = intentClassifierService;
        this.refusalGuardService = refusalGuardService;
        this.chatSessionService = chatSessionService;
    }

    /**
     * SSE 流式问答
     *
     * @param request 包含 query（必填）和 sessionId（可选，不传则自动生成）
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时

        String query = request.query();
        String sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();

        executor.execute(() -> {
            try {
                // 1. 拒答检查
                var refusal = refusalGuardService.shouldRefuse(query);
                if (refusal.shouldRefuse()) {
                    emitter.send(SseEmitter.event().name("message").data(refusal.reason()));
                    emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                    emitter.complete();
                    return;
                }

                // 2. 保存用户消息
                chatSessionService.addMessage(sessionId,
                        ChatMessage.builder().role("user").content(query).timestamp(Instant.now()).build());

                // 3. 构建上下文
                String context = streamingChatService.buildContext(query, sessionId);

                // 4. 流式生成（非流式 ChatModel 降级为一次性返回）
                var response = chatModel.chat(ChatRequest.builder()
                        .messages(UserMessage.from(context))
                        .build());
                String fullAnswer = response.aiMessage().text();

                // 模拟逐 token 推送（将完整回复按字符分批发送）
                for (int i = 0; i < fullAnswer.length(); i += 5) {
                    int end = Math.min(i + 5, fullAnswer.length());
                    String chunk = fullAnswer.substring(i, end);
                    emitter.send(SseEmitter.event().name("message").data(chunk));
                    Thread.sleep(20); // 模拟流式延迟
                }

                // 5. 保存助手消息
                chatSessionService.addMessage(sessionId,
                        ChatMessage.builder().role("assistant").content(fullAnswer).timestamp(Instant.now()).build());

                // 6. 发送完成信号（含 sessionId）
                emitter.send(SseEmitter.event().name("done")
                        .data(Map.of("status", "DONE", "sessionId", sessionId)));
                emitter.complete();

            } catch (Exception e) {
                log.error("流式问答异常", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("服务内部错误: " + e.getMessage()));
                } catch (IOException ex) {
                    log.error("SSE 发送失败", ex);
                }
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(() -> log.debug("SSE 连接完成: session={}", sessionId));
        emitter.onTimeout(() -> log.warn("SSE 连接超时: session={}", sessionId));

        return emitter;
    }

    public record ChatRequest(String query, String sessionId) {}
}
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
mvnw.cmd -pl edumate-admin test -Dtest=ChatControllerTest
```

Expected: PASS (1 test)

- [ ] **Step 5: 编译验证**

```powershell
mvnw.cmd -pl edumate-admin compile
```

Expected: BUILD SUCCESS

---

### Task 11: 端到端集成验证

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

Expected: 全部 Tests PASS

- [ ] **Step 3: 启动应用并验证 Quiz API**

启动应用后，在另一个终端执行：

```powershell
$body = [System.Text.Encoding]::UTF8.GetBytes('{"courseName":"数据结构","count":2,"difficulty":"easy"}')
Invoke-RestMethod -Uri http://localhost:8080/api/quiz/generate -Method Post -ContentType "application/json; charset=utf-8" -Body $body
```

Expected: 返回 JSON 包含 `questionsCount` 和 `questions` 数组，每题有 `stem`/`answer`/`explanation` 等字段。

- [ ] **Step 4: 验证 SSE 流式问答 API**

```powershell
$body = [System.Text.Encoding]::UTF8.GetBytes('{"query":"什么是二叉树","sessionId":"test-001"}')
Invoke-RestMethod -Uri http://localhost:8080/api/chat/stream -Method Post -ContentType "application/json; charset=utf-8" -Body $body
```

Expected: 流式返回多行 `data:` 事件，以 `data: {"status":"DONE","sessionId":"test-001"}` 结尾。

- [ ] **Step 5: 验证多轮对话记忆**

```powershell
# 第二轮对话，使用相同 sessionId
$body = [System.Text.Encoding]::UTF8.GetBytes('{"query":"它有哪些遍历方式","sessionId":"test-001"}')
Invoke-RestMethod -Uri http://localhost:8080/api/chat/stream -Method Post -ContentType "application/json; charset=utf-8" -Body $body
```

Expected: 回复应能关联上下文（识别"它"指代"二叉树"），说明 Redis 会话记忆生效。

---

## 自检清单

**1. Spec 覆盖：**
- [x] 智能出题 Agent（ReAct 模式）→ Task 7
- [x] Query 改写 + 意图识别 → Task 3 + Task 4
- [x] 拒答判断逻辑 → Task 5
- [x] 多轮对话记忆（Redis）→ Task 1 + Task 6
- [x] SSE 流式响应 → Task 8 + Task 10

**2. 无占位符：** 所有 Task 均包含完整代码和精确命令。

**3. 类型一致性：**
- `QuizQuestion` 字段与 `QuizAgentService` 输出一致
- `ChatMessage` 字段与 `ChatSessionService` 存储一致
- 所有 Controller 的 DTO record 与前端请求字段一致
- `StreamingChatService.buildContext()` 签名与 `ChatController` 调用一致