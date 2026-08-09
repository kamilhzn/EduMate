package com.edumate.core.quiz;

import com.edumate.common.model.Quiz;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class QuizServiceTest {

    // ES client = null → 纯内存模式（降级测试）
    private final QuizService quizService = new QuizService(null, new ObjectMapper());

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
