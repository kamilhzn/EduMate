# EduMate 后端 API 补齐实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为前端 Vue3 SPA 补齐后端缺失的 7 个 API 端点：课程 CRUD、章节查询、小节内容、题库列表/删除，使前后端完整对接。

**Architecture:** 由于项目无关系型数据库，采用内存级存储（ConcurrentHashMap）管理课程和题库元数据，章节/小节通过 Elasticsearch 对 `chapterPath` 字段聚合推导。不引入新依赖，复用现有 `KeywordIndexService` 的 ES 客户端。所有新 Controller 沿用现有 `@RestController` + `ResponseEntity` 模式。

**Tech Stack:** Spring Boot 3.4.5, Java 21, Elasticsearch Java Client, ConcurrentHashMap, Lombok

---

## 代码审查总结

### 已有 API（7 个 ✅）

| 端点 | 文件 | 状态 |
|------|------|------|
| `POST /api/documents/upload` | `DocumentController.java` | 正常 |
| `POST /api/chat/stream` | `ChatController.java` | 正常 |
| `POST /api/search` | `SearchController.java` | 正常 |
| `POST /api/quiz/generate` | `QuizController.java` | 正常 |
| `POST /api/eval/retrieval` | `EvaluationController.java` | 正常 |
| `GET /api/eval/trace/{id}` | `EvaluationController.java` | 正常 |
| `GET /api/eval/traces` | `EvaluationController.java` | 正常 |

### 缺失 API（7 个 ❌）

| 端点 | 前端调用位置 | 用途 |
|------|-------------|------|
| `GET /api/courses` | `KnowledgeBase.vue`, `ChatTutor.vue`, `DocumentUpload.vue` | 获取课程列表 |
| `POST /api/courses` | `CourseSelector.vue` | 创建课程 |
| `GET /api/courses/:id` | `KnowledgeBaseCourse.vue` | 获取课程详情 |
| `GET /api/courses/:id/chapters` | `KnowledgeBaseCourse.vue`, `KnowledgeBaseReader.vue` | 获取章节列表 |
| `GET /api/courses/:id/chapters/:chapterId/sections/:sectionId` | `KnowledgeBaseReader.vue` | 获取小节内容 |
| `GET /api/quizzes` | `QuizBank.vue` | 获取题库列表 |
| `DELETE /api/quizzes/:id` | `QuizBank.vue` | 删除题库 |

### 架构约束

- **无关系型数据库**：项目仅依赖 Docker 容器（Qdrant/ES/Neo4j/Redis），无 MySQL/PostgreSQL
- **课程无实体 ID**：现有 `CourseMetadata` 仅含 `courseName` 字符串，无唯一标识
- **文档分块存储**：`DocumentChunk` 含 `courseName` + `chapterPath`（如 "第1章 绪论 > 1.1 什么是数据结构"）字段，已索引到 ES
- **题库不持久化**：`QuizController.generate()` 调用 LLM 生成题目后直接返回，不存储
- **CORS 已配置**：`CorsConfig.java` 使用 `CorsFilter` Bean，配置了 `addAllowedOriginPattern("*")`
- **降级设计惯例**：所有外部服务依赖使用 `@Nullable` 注入，不可用时优雅降级

### 设计方案

**课程存储**：使用 `ConcurrentHashMap<String, Course>` 内存存储，`courseId` = `UUID`，`courseName` 作为唯一键。启动时从 ES 聚合已有 `course_name` 自动回填。

**章节/小节查询**：通过 ES 对 `chapter_path` 字段做聚合查询，从已有分块数据中推导出课程的章节结构，不新增存储。

**小节内容**：通过 ES 精确匹配 `course_name` + `chapter_path` 查询对应分块内容，拼接返回。

**题库存储**：使用 `ConcurrentHashMap<String, Quiz>` 内存存储，`QuizController.generate()` 生成后自动存入。

---

## 文件变更清单

| 操作 | 文件 | 说明 |
|------|------|------|
| **Create** | `edumate-common/.../model/Course.java` | 课程实体模型 |
| **Create** | `edumate-common/.../model/Chapter.java` | 章节模型 |
| **Create** | `edumate-common/.../model/Section.java` | 小节模型 |
| **Create** | `edumate-common/.../model/Quiz.java` | 题库实体模型 |
| **Create** | `edumate-core/.../course/CourseService.java` | 课程业务逻辑 |
| **Create** | `edumate-core/.../course/ChapterService.java` | 章节/小节查询逻辑 |
| **Create** | `edumate-core/.../quiz/QuizService.java` | 题库存储逻辑 |
| **Create** | `edumate-admin/.../controller/CourseController.java` | 课程 API 端点 |
| **Modify** | `edumate-admin/.../controller/QuizController.java` | 新增 list/delete 端点 |
| **Create** | `edumate-admin/.../controller/CourseControllerTest.java` | 课程端点测试 |
| **Create** | `edumate-core/.../course/CourseServiceTest.java` | 课程服务测试 |
| **Create** | `edumate-core/.../quiz/QuizServiceTest.java` | 题库服务测试 |

---

### Task 1: 创建数据模型（Course, Chapter, Section, Quiz）

**Files:**
- Create: `f:\JetBrains\RAG\EduMate\edumate-common\src\main\java\com\edumate\common\model\Course.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-common\src\main\java\com\edumate\common\model\Chapter.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-common\src\main\java\com\edumate\common\model\Section.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-common\src\main\java\com\edumate\common\model\Quiz.java`

- [ ] **Step 1: 创建 Course.java**

```java
package com.edumate.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 课程实体 —— 对应前端课程管理
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Course {
    /** 唯一标识（UUID） */
    private String id;
    /** 课程名称（唯一） */
    private String name;
    /** 封面颜色索引（0-4） */
    private int coverColorIndex;
    /** 章节数量 */
    private int chapterCount;
    /** 创建时间 */
    @Builder.Default
    private Instant createdAt = Instant.now();
}
```

- [ ] **Step 2: 创建 Chapter.java**

```java
package com.edumate.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 章节模型 —— 从 ES 聚合推导
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Chapter {
    /** 章节 ID（用 chapterPath 的 hash 作为标识） */
    private String id;
    /** 章节标题，如 "第1章 绪论" */
    private String title;
    /** 排序序号 */
    private int order;
    /** 下属小节列表 */
    private List<Section> sections;
}
```

- [ ] **Step 3: 创建 Section.java**

```java
package com.edumate.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 小节模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Section {
    /** 小节 ID（用完整 chapterPath 的 hash 作为标识） */
    private String id;
    /** 小节标题，如 "1.1 什么是数据结构" */
    private String title;
    /** 排序序号 */
    private int order;
    /** 所属章节 ID */
    private String chapterId;
}
```

- [ ] **Step 4: 创建 Quiz.java**

```java
package com.edumate.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 题库实体 —— 用于前端题库列表展示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quiz {
    /** 唯一标识 */
    private String id;
    /** 题库名称 */
    private String name;
    /** 所属课程名称 */
    private String courseName;
    /** 出题来源：section / chapter / course */
    private String source;
    /** 题目数量 */
    private int count;
    /** 难度 */
    private String difficulty;
    /** 题目列表 */
    private List<QuizQuestion> questions;
    /** 创建时间 */
    @Builder.Default
    private Instant createdAt = Instant.now();
}
```

---

### Task 2: 创建 CourseService

**Files:**
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\main\java\com\edumate\core\course\CourseService.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\test\java\com\edumate\core\course\CourseServiceTest.java`

- [ ] **Step 1: 创建 CourseService.java**

```java
package com.edumate.core.course;

import com.edumate.common.model.Course;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 课程管理服务 —— 内存存储 + ES 数据回填
 * <p>
 * 课程数据存储在 ConcurrentHashMap 中，启动时从 ES 聚合已有 course_name 自动回填。
 * 当 ES 不可用时，仅使用内存中的课程数据。
 */
@Service
public class CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseService.class);

    private final ConcurrentHashMap<String, Course> courseStore = new ConcurrentHashMap<>();
    private final ElasticsearchClient esClient;

    @Value("${elasticsearch.index-name:edumate-keywords}")
    private String indexName;

    public CourseService(@Nullable ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    /**
     * 获取所有课程列表
     */
    public List<Course> listCourses() {
        if (courseStore.isEmpty()) {
            syncFromES();
        }
        return courseStore.values().stream()
                .sorted(Comparator.comparing(Course::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 根据 ID 获取课程
     */
    public Optional<Course> getCourseById(String id) {
        if (courseStore.isEmpty()) {
            syncFromES();
        }
        return Optional.ofNullable(courseStore.get(id));
    }

    /**
     * 根据名称查找课程
     */
    public Optional<Course> getCourseByName(String name) {
        if (courseStore.isEmpty()) {
            syncFromES();
        }
        return courseStore.values().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst();
    }

    /**
     * 创建新课程
     */
    public Course createCourse(String name) {
        // 检查是否已存在
        Optional<Course> existing = getCourseByName(name);
        if (existing.isPresent()) {
            return existing.get();
        }

        String id = UUID.randomUUID().toString();
        int colorIndex = courseStore.size() % 5;
        Course course = Course.builder()
                .id(id)
                .name(name)
                .coverColorIndex(colorIndex)
                .chapterCount(0)
                .build();

        courseStore.put(id, course);
        log.info("创建课程: id={}, name={}", id, name);
        return course;
    }

    /**
     * 更新课程的章节数量
     */
    public void updateChapterCount(String courseId, int count) {
        Course course = courseStore.get(courseId);
        if (course != null) {
            course.setChapterCount(count);
        }
    }

    /**
     * 从 ES 聚合已有课程数据，回填内存存储
     */
    private void syncFromES() {
        if (esClient == null) {
            log.debug("ES 不可用，跳过课程数据同步");
            return;
        }

        try {
            SearchResponse<Void> response = esClient.search(s -> s
                    .index(indexName)
                    .size(0)
                    .aggregations("courses", a -> a
                            .terms(t -> t.field("course_name").size(50))
                            .aggregations("chapters", a2 -> a2
                                    .terms(t2 -> t2.field("chapter_path").size(100))
                            )),
                    Void.class);

            var coursesAgg = response.aggregations().get("courses");
            if (coursesAgg == null || !coursesAgg.isSterms()) return;

            int colorIdx = 0;
            for (StringTermsBucket bucket : coursesAgg.sterms().buckets().array()) {
                String courseName = bucket.key().stringValue();
                long docCount = bucket.docCount();

                // 统计该课程下有多少个不同的章节
                var chaptersAgg = bucket.aggregations().get("chapters");
                int chapterCount = 0;
                if (chaptersAgg != null && chaptersAgg.isSterms()) {
                    chapterCount = chaptersAgg.sterms().buckets().array().size();
                }

                // 如果内存中还不存在，则创建
                boolean exists = courseStore.values().stream()
                        .anyMatch(c -> c.getName().equals(courseName));
                if (!exists) {
                    String id = UUID.randomUUID().toString();
                    Course course = Course.builder()
                            .id(id)
                            .name(courseName)
                            .coverColorIndex(colorIdx % 5)
                            .chapterCount(chapterCount)
                            .build();
                    courseStore.put(id, course);
                    colorIdx++;
                    log.info("从 ES 同步课程: name={}, chapters={}", courseName, chapterCount);
                }
            }
        } catch (IOException e) {
            log.warn("ES 课程数据同步失败: {}", e.getMessage());
        }
    }
}
```

- [ ] **Step 2: 创建 CourseServiceTest.java**

```java
package com.edumate.core.course;

import com.edumate.common.model.Course;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    private CourseService courseService;

    @BeforeEach
    void setUp() {
        courseService = new CourseService(null);
    }

    @Test
    void shouldCreateCourse() {
        Course course = courseService.createCourse("数据结构");
        assertThat(course.getId()).isNotNull();
        assertThat(course.getName()).isEqualTo("数据结构");
    }

    @Test
    void shouldNotDuplicateCourse() {
        Course c1 = courseService.createCourse("数据结构");
        Course c2 = courseService.createCourse("数据结构");
        assertThat(c1.getId()).isEqualTo(c2.getId());
    }

    @Test
    void shouldListCourses() {
        courseService.createCourse("数据结构");
        courseService.createCourse("计算机网络");
        List<Course> courses = courseService.listCourses();
        assertThat(courses).hasSize(2);
    }

    @Test
    void shouldFindCourseById() {
        Course created = courseService.createCourse("操作系统");
        Optional<Course> found = courseService.getCourseById(created.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("操作系统");
    }
}
```

---

### Task 3: 创建 ChapterService

**Files:**
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\main\java\com\edumate\core\course\ChapterService.java`

- [ ] **Step 1: 创建 ChapterService.java**

```java
package com.edumate.core.course;

import com.edumate.common.model.Chapter;
import com.edumate.common.model.Section;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 章节查询服务 —— 从 ES 分块数据推导章节/小节结构
 * <p>
 * 不依赖额外存储，完全基于 ES 中已有的 chapterPath 字段。
 * chapterPath 格式如 "第1章 绪论" 或 "第1章 绪论 > 1.1 什么是数据结构"
 */
@Service
public class ChapterService {

    private static final Logger log = LoggerFactory.getLogger(ChapterService.class);

    private final ElasticsearchClient esClient;
    private final CourseService courseService;

    @Value("${elasticsearch.index-name:edumate-keywords}")
    private String indexName;

    private static final Pattern SECTION_PATTERN =
            Pattern.compile("^(\\d+\\.\\d+)\\s+(.*)");

    public ChapterService(@Nullable ElasticsearchClient esClient, CourseService courseService) {
        this.esClient = esClient;
        this.courseService = courseService;
    }

    /**
     * 获取课程的所有章节（含小节）
     *
     * @param courseId 课程 ID
     * @return 章节列表（按顺序）
     */
    public List<Chapter> getChapters(String courseId) {
        var courseOpt = courseService.getCourseById(courseId);
        if (courseOpt.isEmpty()) {
            return List.of();
        }
        String courseName = courseOpt.get().getName();

        if (esClient == null) {
            log.debug("ES 不可用，返回空章节列表");
            return List.of();
        }

        try {
            // 查询该课程所有的 chapterPath
            SearchResponse<Map> response = esClient.search(s -> s
                    .index(indexName)
                    .query(q -> q.term(t -> t.field("course_name").value(courseName)))
                    .size(1000)
                    .source(src -> src.includes("chapter_path")),
                    Map.class);

            // 解析 chapterPath，构建章节树
            Map<String, Chapter> chapterMap = new LinkedHashMap<>();
            Map<String, List<Section>> sectionMap = new LinkedHashMap<>();

            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = (Map<String, Object>) hit.source();
                if (source == null) continue;
                String chapterPath = String.valueOf(source.getOrDefault("chapter_path", ""));
                if (chapterPath.isEmpty()) continue;

                parseChapterPath(chapterPath, chapterMap, sectionMap);
            }

            // 组装章节 + 小节
            List<Chapter> chapters = new ArrayList<>();
            int order = 1;
            for (Map.Entry<String, Chapter> entry : chapterMap.entrySet()) {
                Chapter chapter = entry.getValue();
                chapter.setOrder(order++);
                chapter.setSections(sectionMap.getOrDefault(entry.getKey(), List.of()));
                chapters.add(chapter);
            }

            return chapters;
        } catch (IOException e) {
            log.error("查询章节失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取小节内容
     *
     * @param courseId  课程 ID
     * @param chapterId 章节 ID
     * @param sectionId 小节 ID
     * @return 小节 Markdown 文本内容
     */
    public String getSectionContent(String courseId, String chapterId, String sectionId) {
        var courseOpt = courseService.getCourseById(courseId);
        if (courseOpt.isEmpty()) {
            return "课程不存在";
        }
        String courseName = courseOpt.get().getName();

        if (esClient == null) {
            return "ES 服务不可用，无法加载内容";
        }

        try {
            // 先找到章节标题
            List<Chapter> chapters = getChapters(courseId);
            String sectionPath = null;
            for (Chapter ch : chapters) {
                if (ch.getId().equals(chapterId)) {
                    for (Section sec : ch.getSections()) {
                        if (sec.getId().equals(sectionId)) {
                            sectionPath = ch.getTitle() + " > " + sec.getTitle();
                            break;
                        }
                    }
                    // 如果没有小节，则使用章节路径
                    if (sectionPath == null) {
                        sectionPath = ch.getTitle();
                    }
                    break;
                }
            }

            if (sectionPath == null) {
                return "内容不存在";
            }

            // 查询对应 chapterPath 的分块
            String finalPath = sectionPath;
            SearchResponse<Map> response = esClient.search(s -> s
                    .index(indexName)
                    .query(q -> q.bool(b -> b
                            .must(m -> m.term(t -> t.field("course_name").value(courseName)))
                            .must(m -> m.matchPhrase(mp -> mp.field("chapter_path").query(finalPath)))
                    ))
                    .size(50)
                    .source(src -> src.includes("content", "chapter_path")),
                    Map.class);

            return response.hits().hits().stream()
                    .map(hit -> {
                        Map<String, Object> source = (Map<String, Object>) hit.source();
                        return source != null ? String.valueOf(source.getOrDefault("content", "")) : "";
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));

        } catch (IOException e) {
            log.error("查询小节内容失败: {}", e.getMessage());
            return "加载内容失败: " + e.getMessage();
        }
    }

    /**
     * 解析 chapterPath 字符串，构建章节+小节结构
     * <p>
     * 格式示例：
     * - "第1章 绪论" → 章节
     * - "第1章 绪论 > 1.1 什么是数据结构" → 章节 + 小节
     */
    private void parseChapterPath(String chapterPath,
                                   Map<String, Chapter> chapterMap,
                                   Map<String, List<Section>> sectionMap) {
        String[] parts = chapterPath.split(" > ");
        if (parts.length == 0) return;

        String chapterTitle = parts[0].trim();
        if (chapterTitle.isEmpty()) return;

        // 确保章节存在
        chapterMap.computeIfAbsent(chapterTitle, k -> {
            String id = Integer.toHexString(k.hashCode());
            return Chapter.builder().id(id).title(k).sections(new ArrayList<>()).build();
        });

        if (parts.length >= 2) {
            String sectionTitle = parts[1].trim();
            if (sectionTitle.isEmpty()) return;

            sectionMap.computeIfAbsent(chapterTitle, k -> new ArrayList<>());

            List<Section> sections = sectionMap.get(chapterTitle);
            boolean exists = sections.stream().anyMatch(s -> s.getTitle().equals(sectionTitle));
            if (!exists) {
                String secId = Integer.toHexString((chapterTitle + " > " + sectionTitle).hashCode());
                Section section = Section.builder()
                        .id(secId)
                        .title(sectionTitle)
                        .order(sections.size() + 1)
                        .chapterId(chapterMap.get(chapterTitle).getId())
                        .build();
                sections.add(section);
            }
        }
    }
}
```

---

### Task 4: 创建 QuizService

**Files:**
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\main\java\com\edumate\core\quiz\QuizService.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\test\java\com\edumate\core\quiz\QuizServiceTest.java`

- [ ] **Step 1: 创建 QuizService.java**

```java
package com.edumate.core.quiz;

import com.edumate.common.model.Quiz;
import com.edumate.common.model.QuizQuestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 题库管理服务 —— 内存存储
 * <p>
 * 题库数据存储在 ConcurrentHashMap 中，服务器重启后丢失。
 * 未来可扩展为 Redis 或数据库存储。
 */
@Service
public class QuizService {

    private static final Logger log = LoggerFactory.getLogger(QuizService.class);

    private final ConcurrentHashMap<String, Quiz> quizStore = new ConcurrentHashMap<>();

    /**
     * 获取所有题库列表
     */
    public List<Quiz> listQuizzes() {
        return quizStore.values().stream()
                .sorted(Comparator.comparing(Quiz::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 保存题库
     */
    public Quiz saveQuiz(Quiz quiz) {
        quizStore.put(quiz.getId(), quiz);
        log.info("保存题库: id={}, name={}, questions={}", quiz.getId(), quiz.getName(), quiz.getCount());
        return quiz;
    }

    /**
     * 根据 ID 获取题库
     */
    public Optional<Quiz> getQuizById(String id) {
        return Optional.ofNullable(quizStore.get(id));
    }

    /**
     * 删除题库
     */
    public boolean deleteQuiz(String id) {
        Quiz removed = quizStore.remove(id);
        if (removed != null) {
            log.info("删除题库: id={}, name={}", id, removed.getName());
            return true;
        }
        return false;
    }
}
```

- [ ] **Step 2: 创建 QuizServiceTest.java**

```java
package com.edumate.core.quiz;

import com.edumate.common.model.Quiz;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class QuizServiceTest {

    private final QuizService quizService = new QuizService();

    @Test
    void shouldSaveAndListQuizzes() {
        Quiz quiz = Quiz.builder()
                .id("q1")
                .name("数据结构-综合测试")
                .courseName("数据结构")
                .count(10)
                .source("course")
                .build();

        quizService.saveQuiz(quiz);
        List<Quiz> quizzes = quizService.listQuizzes();
        assertThat(quizzes).hasSize(1);
        assertThat(quizzes.get(0).getName()).isEqualTo("数据结构-综合测试");
    }

    @Test
    void shouldDeleteQuiz() {
        Quiz quiz = Quiz.builder().id("q1").name("test").courseName("test").count(5).build();
        quizService.saveQuiz(quiz);

        boolean deleted = quizService.deleteQuiz("q1");
        assertThat(deleted).isTrue();
        assertThat(quizService.listQuizzes()).isEmpty();
    }

    @Test
    void shouldReturnEmptyForNonExistentQuiz() {
        Optional<Quiz> result = quizService.getQuizById("nonexistent");
        assertThat(result).isEmpty();
    }
}
```

---

### Task 5: 创建 CourseController

**Files:**
- Create: `f:\JetBrains\RAG\EduMate\edumate-admin\src\main\java\com\edumate\admin\controller\CourseController.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-admin\src\test\java\com\edumate\admin\controller\CourseControllerTest.java`

- [ ] **Step 1: 创建 CourseController.java**

```java
package com.edumate.admin.controller;

import com.edumate.common.model.Chapter;
import com.edumate.common.model.Course;
import com.edumate.core.course.ChapterService;
import com.edumate.core.course.CourseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 课程管理控制器 —— 课程 CRUD + 章节/小节查询
 */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;
    private final ChapterService chapterService;

    public CourseController(CourseService courseService, ChapterService chapterService) {
        this.courseService = courseService;
        this.chapterService = chapterService;
    }

    /**
     * 获取所有课程列表
     */
    @GetMapping
    public ResponseEntity<List<Course>> listCourses() {
        return ResponseEntity.ok(courseService.listCourses());
    }

    /**
     * 获取单个课程详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getCourse(@PathVariable String id) {
        return courseService.getCourseById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 创建新课程
     */
    @PostMapping
    public ResponseEntity<?> createCourse(@RequestBody CreateCourseRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "课程名称不能为空"));
        }

        Course course = courseService.createCourse(request.name().trim());
        return ResponseEntity.ok(course);
    }

    /**
     * 获取课程的章节列表
     */
    @GetMapping("/{courseId}/chapters")
    public ResponseEntity<?> getChapters(@PathVariable String courseId) {
        var courseOpt = courseService.getCourseById(courseId);
        if (courseOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Chapter> chapters = chapterService.getChapters(courseId);
        courseService.updateChapterCount(courseId, chapters.size());

        return ResponseEntity.ok(chapters);
    }

    /**
     * 获取小节内容
     */
    @GetMapping("/{courseId}/chapters/{chapterId}/sections/{sectionId}")
    public ResponseEntity<?> getSectionContent(
            @PathVariable String courseId,
            @PathVariable String chapterId,
            @PathVariable String sectionId) {

        String content = chapterService.getSectionContent(courseId, chapterId, sectionId);
        if (content == null || content.equals("课程不存在") || content.equals("内容不存在")) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "courseId", courseId,
                "chapterId", chapterId,
                "sectionId", sectionId,
                "content", content
        ));
    }

    public record CreateCourseRequest(String name) {}
}
```

- [ ] **Step 2: 创建 CourseControllerTest.java**

```java
package com.edumate.admin.controller;

import com.edumate.common.model.Chapter;
import com.edumate.common.model.Course;
import com.edumate.common.model.Section;
import com.edumate.core.course.ChapterService;
import com.edumate.core.course.CourseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CourseController.class)
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourseService courseService;

    @MockitoBean
    private ChapterService chapterService;

    @Test
    void shouldListCourses() throws Exception {
        Course course = Course.builder().id("c1").name("数据结构").chapterCount(5).build();
        when(courseService.listCourses()).thenReturn(List.of(course));

        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("数据结构"));
    }

    @Test
    void shouldCreateCourse() throws Exception {
        Course course = Course.builder().id("c1").name("数据结构").build();
        when(courseService.createCourse("数据结构")).thenReturn(course);

        mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"数据结构\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("数据结构"));
    }

    @Test
    void shouldRejectEmptyCourseName() throws Exception {
        mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetChapters() throws Exception {
        when(courseService.getCourseById("c1")).thenReturn(Optional.of(
                Course.builder().id("c1").name("数据结构").build()));
        Section section = Section.builder().id("s1").title("1.1 绪论").order(1).build();
        Chapter chapter = Chapter.builder().id("ch1").title("第1章 绪论").order(1)
                .sections(List.of(section)).build();
        when(chapterService.getChapters("c1")).thenReturn(List.of(chapter));

        mockMvc.perform(get("/api/courses/c1/chapters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("第1章 绪论"));
    }
}
```

---

### Task 6: 修改 QuizController 新增 list/delete 端点

**Files:**
- Modify: `f:\JetBrains\RAG\EduMate\edumate-admin\src\main\java\com\edumate\admin\controller\QuizController.java`

- [ ] **Step 1: 修改 QuizController.java**

在 `QuizController` 中注入 `QuizService`，修改 `generate()` 方法使其自动保存，并新增 `listQuizzes()` 和 `deleteQuiz()` 端点。

**修改后的完整文件：**

```java
package com.edumate.admin.controller;

import com.edumate.common.model.Quiz;
import com.edumate.common.model.QuizQuestion;
import com.edumate.core.agent.QuizAgentService;
import com.edumate.core.quiz.QuizService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 智能出题控制器
 */
@RestController
@RequestMapping("/api")
public class QuizController {

    private final QuizAgentService quizAgentService;
    private final QuizService quizService;

    public QuizController(QuizAgentService quizAgentService, QuizService quizService) {
        this.quizAgentService = quizAgentService;
        this.quizService = quizService;
    }

    /**
     * 生成题目并自动保存到题库
     */
    @PostMapping("/quiz/generate")
    public ResponseEntity<?> generate(@RequestBody QuizRequest request) {
        if (request.courseName() == null || request.courseName().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "courseName 不能为空"));
        }

        int count = request.count() > 0 ? request.count() : 5;
        String difficulty = request.difficulty() != null ? request.difficulty() : "medium";

        List<QuizQuestion> questions = quizAgentService.generate(
                request.courseName(), request.chapter(), count, difficulty);

        // 生成题库名称
        String source;
        String name;
        if (request.section() != null && !request.section().isBlank()) {
            source = "section";
            name = request.courseName() + "-" + request.chapter() + "-" + request.section() + "-节测试";
        } else if (request.chapter() != null && !request.chapter().isBlank()) {
            source = "chapter";
            name = request.courseName() + "-" + request.chapter() + "-章测试";
        } else {
            source = "course";
            name = request.courseName() + "-综合测试";
        }

        // 保存到内存题库
        Quiz quiz = Quiz.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .courseName(request.courseName())
                .source(source)
                .count(questions.size())
                .difficulty(difficulty)
                .questions(questions)
                .createdAt(Instant.now())
                .build();
        quizService.saveQuiz(quiz);

        return ResponseEntity.ok(quiz);
    }

    /**
     * 获取所有题库列表
     */
    @GetMapping("/quizzes")
    public ResponseEntity<List<Quiz>> listQuizzes() {
        return ResponseEntity.ok(quizService.listQuizzes());
    }

    /**
     * 删除题库
     */
    @DeleteMapping("/quizzes/{id}")
    public ResponseEntity<?> deleteQuiz(@PathVariable String id) {
        boolean deleted = quizService.deleteQuiz(id);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        }
        return ResponseEntity.notFound().build();
    }

    public record QuizRequest(String courseName, String chapter, String section, int count, String difficulty) {}
}
```

---

### Task 7: 验证 CORS 配置

**Files:**
- Read: `f:\JetBrains\RAG\EduMate\edumate-admin\src\main\java\com\edumate\admin\config\CorsConfig.java`

- [ ] **Step 1: 确认 CorsConfig 已存在且正确**

文件已存在，内容为：
```java
@Configuration
public class CorsConfig {
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
```

确认 CORS 配置无需修改。如果重启后仍出现 CORS 错误，可能原因：Spring Security 未配置但也不会拦截（项目未引入 Spring Security），应检查是否有其他 Filter 拦截。

---

## 完整 API 端点清单（补齐后）

| 方法 | 端点 | 功能 | 状态 |
|------|------|------|------|
| `POST` | `/api/documents/upload` | 文档上传 | ✅ 已有 |
| `POST` | `/api/chat/stream` | SSE 流式问答 | ✅ 已有 |
| `POST` | `/api/search` | 混合检索 | ✅ 已有 |
| `POST` | `/api/quiz/generate` | 智能出题 | ✅ 已修改 |
| `POST` | `/api/eval/retrieval` | 检索评测 | ✅ 已有 |
| `GET` | `/api/eval/trace/{id}` | Trace 查询 | ✅ 已有 |
| `GET` | `/api/eval/traces` | 最近 Trace | ✅ 已有 |
| `GET` | `/api/courses` | 获取课程列表 | 🆕 新增 |
| `POST` | `/api/courses` | 创建课程 | 🆕 新增 |
| `GET` | `/api/courses/:id` | 课程详情 | 🆕 新增 |
| `GET` | `/api/courses/:id/chapters` | 章节列表 | 🆕 新增 |
| `GET` | `/api/courses/:id/chapters/:chapterId/sections/:sectionId` | 小节内容 | 🆕 新增 |
| `GET` | `/api/quizzes` | 题库列表 | 🆕 新增 |
| `DELETE` | `/api/quizzes/:id` | 删除题库 | 🆕 新增 |