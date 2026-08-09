package com.edumate.core.parser;

import com.edumate.common.model.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 层级感知切分服务 —— 按"章→节→小节→知识点"结构切分文档
 */
@Service
public class HierarchicalChunkerService {

    /** 匹配章节标题：第X章 / 第Y节 / X.Y 等 */
    private static final Pattern CHAPTER_PATTERN =
            Pattern.compile("^(第[一二三四五六七八九十\\d]+章)\\s*(.*)", Pattern.MULTILINE);
    private static final Pattern SECTION_PATTERN =
            Pattern.compile("^(\\d+\\.\\d+)\\s+(.*)", Pattern.MULTILINE);

    /** 块大小上限（字符数），超过则二次切分 */
    private static final int MAX_CHUNK_SIZE = 2000;
    /** 块间重叠大小（字符数） */
    private static final int OVERLAP_SIZE = 200;

    public List<DocumentChunk> chunk(String content, String courseName, String semester) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        String currentChapter = "";
        String currentSection = "";
        StringBuilder currentChunk = new StringBuilder();

        String[] lines = content.split("\n");
        for (String line : lines) {
            Matcher chapterMatcher = CHAPTER_PATTERN.matcher(line);
            Matcher sectionMatcher = SECTION_PATTERN.matcher(line);

            if (chapterMatcher.find()) {
                // 发现新章，先保存当前积累的块
                flushChunk(chunks, currentChunk, currentChapter, currentSection, courseName);
                currentChapter = chapterMatcher.group(1) + " " + chapterMatcher.group(2).trim();
                currentSection = "";
                currentChunk = new StringBuilder();
            } else if (sectionMatcher.find()) {
                // 发现新节
                flushChunk(chunks, currentChunk, currentChapter, currentSection, courseName);
                currentSection = sectionMatcher.group(1) + " " + sectionMatcher.group(2).trim();
                currentChunk = new StringBuilder();
            }

            currentChunk.append(line).append("\n");

            // 如果当前块超过上限，切分
            if (currentChunk.length() > MAX_CHUNK_SIZE) {
                flushChunk(chunks, currentChunk, currentChapter, currentSection, courseName);
                currentChunk = new StringBuilder();
                // 保留重叠上下文
                String overlap = currentChunk.length() > OVERLAP_SIZE
                        ? currentChunk.substring(currentChunk.length() - OVERLAP_SIZE)
                        : currentChunk.toString();
                currentChunk = new StringBuilder(overlap);
            }
        }

        // 最后一块
        flushChunk(chunks, currentChunk, currentChapter, currentSection, courseName);

        return chunks;
    }

    private void flushChunk(List<DocumentChunk> chunks, StringBuilder content,
                            String chapter, String section, String courseName) {
        if (content.isEmpty()) {
            return;
        }
        String chapterPath = buildChapterPath(chapter, section);
        chunks.add(DocumentChunk.builder()
                .id(UUID.randomUUID().toString())
                .content(content.toString().trim())
                .courseName(courseName)
                .chapterPath(chapterPath)
                .build());
        content.setLength(0);
    }

    private String buildChapterPath(String chapter, String section) {
        if (section.isEmpty()) {
            return chapter;
        }
        return chapter + " > " + section;
    }
}