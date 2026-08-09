package com.edumate.core.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentParserServiceTest {

    private final DocumentParserService parserService = new DocumentParserService();

    @Test
    void shouldParseTxtFile(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "第一章 概述\n\n数据结构是计算机科学的基础。");

        String result = parserService.parse(testFile);

        assertThat(result).isNotNull();
        assertThat(result).contains("第一章");
        assertThat(result).contains("数据结构");
    }

    @Test
    void shouldParseMarkdownFile(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("notes.md");
        Files.writeString(testFile, "# 标题\n\n正文内容。");

        String result = parserService.parse(testFile);

        assertThat(result).contains("标题");
        assertThat(result).contains("正文内容");
    }

    @Test
    void shouldParseEmptyFile(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("empty.txt");
        Files.writeString(testFile, "");

        String result = parserService.parse(testFile);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void shouldRejectUnsupportedFormat(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("file.unknown");
        Files.writeString(testFile, "test");

        assertThatThrownBy(() -> parserService.parse(testFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的文档格式");
    }

    @Test
    void shouldRejectFileWithoutExtension(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("noextension");
        Files.writeString(testFile, "test");

        assertThatThrownBy(() -> parserService.parse(testFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无法识别的文件类型");
    }
}
