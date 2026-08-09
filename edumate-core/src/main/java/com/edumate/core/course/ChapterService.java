package com.edumate.core.course;

import com.edumate.common.model.Chapter;
import com.edumate.common.model.Section;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChapterService {

    private static final Logger log = LoggerFactory.getLogger(ChapterService.class);

    private final ElasticsearchClient esClient;
    private final CourseService courseService;

    @Value("${elasticsearch.index-name:edumate-keywords}")
    private String indexName;

    public ChapterService(@Nullable ElasticsearchClient esClient, CourseService courseService) {
        this.esClient = esClient;
        this.courseService = courseService;
    }

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
            SearchResponse<Map> response = esClient.search(s -> s
                    .index(indexName)
                    .query(q -> q.term(t -> t.field("course_name.keyword").value(courseName)))
                    .size(1000)
                    .source(src -> src.filter(f -> f.includes("chapter_path"))),
                    Map.class);

            Map<String, Chapter> chapterMap = new LinkedHashMap<>();
            Map<String, List<Section>> sectionMap = new LinkedHashMap<>();

            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = (Map<String, Object>) hit.source();
                if (source == null) continue;
                String chapterPath = String.valueOf(source.getOrDefault("chapter_path", ""));
                if (chapterPath.isEmpty()) continue;

                parseChapterPath(chapterPath, chapterMap, sectionMap);
            }

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
            List<Chapter> chapters = getChapters(courseId);
            log.info("getSectionContent: courseName={}, courseId={}, chapterId={}, sectionId={}, 章节数={}",
                    courseName, courseId, chapterId, sectionId, chapters.size());
            for (Chapter ch : chapters) {
                log.debug("  章节: id={}, title={}, sections={}", ch.getId(), ch.getTitle(),
                        ch.getSections() != null ? ch.getSections().size() : 0);
            }

            String sectionPath = null;
            String matchedChapterTitle = null;
            for (Chapter ch : chapters) {
                if (ch.getId().equals(chapterId)) {
                    matchedChapterTitle = ch.getTitle();
                    log.info("getSectionContent: 匹配到章节 title={}, sections={}",
                            ch.getTitle(), ch.getSections() != null ? ch.getSections().size() : 0);
                    for (Section sec : ch.getSections()) {
                        log.debug("    小节: id={}, title={}", sec.getId(), sec.getTitle());
                        if (sec.getId().equals(sectionId)) {
                            sectionPath = ch.getTitle() + " > " + sec.getTitle();
                            log.info("getSectionContent: 匹配到小节, sectionPath={}", sectionPath);
                            break;
                        }
                    }
                    if (sectionPath == null) {
                        sectionPath = ch.getTitle();
                        log.warn("getSectionContent: 未匹配到小节 sectionId={}, 回退到章级, sectionPath={}",
                                sectionId, sectionPath);
                    }
                    break;
                }
            }

            if (matchedChapterTitle == null) {
                log.warn("getSectionContent: 未匹配到章节 chapterId={}, 可用章节: {}",
                        chapterId, chapters.stream().map(Chapter::getTitle).toList());
            }

            if (sectionPath == null) {
                log.warn("getSectionContent: sectionPath 为 null, 返回'内容不存在'");
                return "内容不存在";
            }

            String finalPath = sectionPath;
            log.info("getSectionContent: ES 查询 courseName={}, chapter_path matchPhrase={}",
                    courseName, finalPath);
            SearchResponse<Map> response = esClient.search(s -> s
                    .index(indexName)
                    .query(q -> q.bool(b -> b
                            .must(m -> m.term(t -> t.field("course_name.keyword").value(courseName)))
                            .must(m -> m.matchPhrase(mp -> mp.field("chapter_path").query(finalPath)))
                    ))
                    .size(50)
                    .source(src -> src.filter(f -> f.includes("content", "chapter_path"))),
                    Map.class);

            long hitCount = response.hits().total() != null ? response.hits().total().value() : 0;
            log.info("getSectionContent: ES 查询命中 {} 条", hitCount);

            String result = response.hits().hits().stream()
                    .map(hit -> {
                        Map<String, Object> source = (Map<String, Object>) hit.source();
                        return source != null ? String.valueOf(source.getOrDefault("content", "")) : "";
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));

            if (result.isEmpty()) {
                log.warn("getSectionContent: 拼接后内容为空, hitCount={}", hitCount);
            }
            return result;

        } catch (IOException e) {
            log.error("查询小节内容失败: {}", e.getMessage());
            return "加载内容失败: " + e.getMessage();
        }
    }

    private void parseChapterPath(String chapterPath,
                                   Map<String, Chapter> chapterMap,
                                   Map<String, List<Section>> sectionMap) {
        String[] parts = chapterPath.split(" > ");
        if (parts.length == 0) return;

        String chapterTitle = parts[0].trim();
        if (chapterTitle.isEmpty()) return;

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