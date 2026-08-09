package com.edumate.admin.controller;

import com.edumate.common.model.Quiz;
import com.edumate.common.model.QuizQuestion;
import com.edumate.core.agent.QuizAgentService;
import com.edumate.core.quiz.QuizService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QuizController.class)
class QuizControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuizAgentService quizAgentService;

    @MockitoBean
    private QuizService quizService;

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
    void shouldReturnQuizWithSavedQuestions() throws Exception {
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
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.courseName").value("数据结构"))
                .andExpect(jsonPath("$.source").value("course"));
    }

    @Test
    void shouldListQuizzes() throws Exception {
        Quiz quiz = Quiz.builder().id("q1").name("数据结构-综合测试")
                .courseName("数据结构").count(5).source("course").build();
        when(quizService.listQuizzes()).thenReturn(List.of(quiz));

        mockMvc.perform(get("/api/quizzes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("数据结构-综合测试"));
    }

    @Test
    void shouldDeleteQuiz() throws Exception {
        when(quizService.deleteQuiz("q1")).thenReturn(true);

        mockMvc.perform(delete("/api/quizzes/q1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("删除成功"));
    }

    @Test
    void shouldReturn404WhenDeletingNonExistentQuiz() throws Exception {
        when(quizService.deleteQuiz("nonexistent")).thenReturn(false);

        mockMvc.perform(delete("/api/quizzes/nonexistent"))
                .andExpect(status().isNotFound());
    }
}