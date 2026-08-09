package com.edumate.admin.controller;

import com.edumate.common.model.Chapter;
import com.edumate.common.model.Course;
import com.edumate.common.model.Section;
import com.edumate.core.course.ChapterService;
import com.edumate.core.course.CourseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(CourseController.class);

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

    /**
     * 兼容旧版 URL 格式：/api/courses/{courseId}/sections/{sectionId}
     * 自动查找 sectionId 所属的 chapterId 后转发到标准端点
     */
    @GetMapping("/{courseId}/sections/{sectionId}")
    public ResponseEntity<?> getSectionContentLegacy(
            @PathVariable String courseId,
            @PathVariable String sectionId) {

        log.info("收到旧版请求: courseId={}, sectionId={}", courseId, sectionId);

        List<Chapter> chapters = chapterService.getChapters(courseId);
        for (Chapter ch : chapters) {
            if (ch.getSections() != null) {
                for (Section sec : ch.getSections()) {
                    if (sec.getId().equals(sectionId)) {
                        log.info("旧版请求: 在章节 '{}' (id={}) 中找到小节 '{}'", ch.getTitle(), ch.getId(), sec.getTitle());
                        String content = chapterService.getSectionContent(courseId, ch.getId(), sectionId);
                        if (content == null || content.equals("课程不存在") || content.equals("内容不存在")) {
                            return ResponseEntity.notFound().build();
                        }
                        return ResponseEntity.ok(Map.of(
                                "courseId", courseId,
                                "chapterId", ch.getId(),
                                "sectionId", sectionId,
                                "content", content
                        ));
                    }
                }
            }
        }

        log.warn("旧版请求: 在所有章节中未找到 sectionId={}", sectionId);
        return ResponseEntity.notFound().build();
    }

    public record CreateCourseRequest(String name) {}
}