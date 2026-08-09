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

    public List<Course> listCourses() {
        syncFromES();
        return courseStore.values().stream()
                .sorted(Comparator.comparing(Course::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public Optional<Course> getCourseById(String id) {
        syncFromES();
        return Optional.ofNullable(courseStore.get(id));
    }

    public Optional<Course> getCourseByName(String name) {
        syncFromES();
        return courseStore.values().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst();
    }

    public Course createCourse(String name) {
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

    public void updateChapterCount(String courseId, int count) {
        Course course = courseStore.get(courseId);
        if (course != null) {
            course.setChapterCount(count);
        }
    }

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
                            .terms(t -> t.field("course_name.keyword").size(50))
                            .aggregations("chapters", a2 -> a2
                                    .terms(t2 -> t2.field("chapter_path.keyword").size(100))
                            )),
                    Void.class);

            var coursesAgg = response.aggregations().get("courses");
            if (coursesAgg == null || !coursesAgg.isSterms()) return;

            int colorIdx = 0;
            for (StringTermsBucket bucket : coursesAgg.sterms().buckets().array()) {
                String courseName = bucket.key().stringValue();
                long docCount = bucket.docCount();

                var chaptersAgg = bucket.aggregations().get("chapters");
                int chapterCount = 0;
                if (chaptersAgg != null && chaptersAgg.isSterms()) {
                    chapterCount = chaptersAgg.sterms().buckets().array().size();
                }

                Course existingCourse = courseStore.values().stream()
                        .filter(c -> c.getName().equals(courseName))
                        .findFirst()
                        .orElse(null);
                if (existingCourse != null) {
                    existingCourse.setChapterCount(chapterCount);
                    log.debug("更新课程章节数: name={}, chapters={}", courseName, chapterCount);
                } else {
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