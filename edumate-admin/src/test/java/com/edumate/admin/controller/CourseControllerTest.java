package com.edumate.admin.controller;

import com.edumate.common.model.Chapter;
import com.edumate.common.model.Course;
import com.edumate.common.model.Section;
import com.edumate.core.course.ChapterService;
import com.edumate.core.course.CourseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

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

    @Test
    void shouldReturn404ForNonExistentCourse() throws Exception {
        when(courseService.getCourseById("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/courses/nonexistent"))
                .andExpect(status().isNotFound());
    }
}