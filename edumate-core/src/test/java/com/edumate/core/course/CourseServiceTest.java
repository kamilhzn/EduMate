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