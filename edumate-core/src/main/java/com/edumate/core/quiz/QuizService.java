package com.edumate.core.quiz;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.edumate.common.model.Quiz;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class QuizService {

    private static final Logger log = LoggerFactory.getLogger(QuizService.class);
    private static final String QUIZ_INDEX = "edumate-quizzes";

    private final ConcurrentHashMap<String, Quiz> quizStore = new ConcurrentHashMap<>();
    private final ElasticsearchClient esClient;
    private final ObjectMapper objectMapper;

    public QuizService(@Nullable ElasticsearchClient esClient, ObjectMapper objectMapper) {
        this.esClient = esClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 启动时初始化：创建 ES 索引（如不存在），并从 ES 恢复已保存的题库到内存
     */
    @PostConstruct
    public void init() {
        if (esClient == null) {
            log.warn("ES 不可用，题库持久化已禁用（仅内存模式）");
            return;
        }
        createIndexIfNotExists();
        loadQuizzesFromES();
    }

    private void createIndexIfNotExists() {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(QUIZ_INDEX)).value();
            if (!exists) {
                esClient.indices().create(c -> c
                        .index(QUIZ_INDEX)
                        .mappings(m -> m
                                .properties("courseName", p -> p.keyword(k -> k))
                                .properties("source", p -> p.keyword(k -> k))
                                .properties("difficulty", p -> p.keyword(k -> k))
                                .properties("createdAt", p -> p.date(d -> d))
                                .properties("name", p -> p.text(t -> t))
                                .properties("count", p -> p.integer(i -> i))
                                .properties("questions", p -> p.object(o -> o.enabled(true)))));
                log.info("创建 ES 题库索引: {}", QUIZ_INDEX);
            }
        } catch (Exception e) {
            log.warn("ES 题库索引创建失败（可能已存在）: {}", e.getMessage());
        }
    }

    /**
     * 从 ES 加载所有题库到内存
     */
    @SuppressWarnings("unchecked")
    private void loadQuizzesFromES() {
        try {
            SearchResponse<Map> response = esClient.search(s -> s
                    .index(QUIZ_INDEX)
                    .query(q -> q.matchAll(m -> m))
                    .size(500),
                    Map.class);

            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source == null) continue;
                Quiz quiz = mapToQuiz(source);
                if (quiz != null) {
                    quizStore.put(quiz.getId(), quiz);
                }
            }
            log.info("从 ES 恢复题库: {} 条", quizStore.size());
        } catch (Exception e) {
            log.warn("从 ES 加载题库失败: {}", e.getMessage());
        }
    }

    public List<Quiz> listQuizzes() {
        return quizStore.values().stream()
                .sorted(Comparator.comparing(Quiz::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public Quiz saveQuiz(Quiz quiz) {
        quizStore.put(quiz.getId(), quiz);
        log.info("保存题库: id={}, name={}, questions={}", quiz.getId(), quiz.getName(), quiz.getCount());

        // 持久化到 ES
        if (esClient != null) {
            try {
                Map<String, Object> doc = objectMapper.convertValue(quiz, Map.class);
                esClient.index(i -> i
                        .index(QUIZ_INDEX)
                        .id(quiz.getId())
                        .document(doc));
                log.debug("题库已持久化到 ES: id={}", quiz.getId());
            } catch (Exception e) {
                log.warn("题库持久化到 ES 失败（内存已保存）: {}", e.getMessage());
            }
        }
        return quiz;
    }

    public Optional<Quiz> getQuizById(String id) {
        return Optional.ofNullable(quizStore.get(id));
    }

    public boolean deleteQuiz(String id) {
        Quiz removed = quizStore.remove(id);
        if (removed != null) {
            log.info("删除题库: id={}, name={}", id, removed.getName());

            // 从 ES 删除
            if (esClient != null) {
                try {
                    esClient.delete(d -> d.index(QUIZ_INDEX).id(id));
                    log.debug("题库已从 ES 删除: id={}", id);
                } catch (Exception e) {
                    log.warn("从 ES 删除题库失败: {}", e.getMessage());
                }
            }
            return true;
        }
        return false;
    }

    /**
     * 将 ES 返回的 Map 转换为 Quiz 对象
     */
    @SuppressWarnings("unchecked")
    private Quiz mapToQuiz(Map<String, Object> source) {
        try {
            return objectMapper.convertValue(source, Quiz.class);
        } catch (Exception e) {
            log.warn("题库数据反序列化失败: {}", e.getMessage());
            return null;
        }
    }
}
