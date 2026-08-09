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