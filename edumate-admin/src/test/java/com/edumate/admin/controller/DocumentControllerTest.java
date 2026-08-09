package com.edumate.admin.controller;

import com.edumate.core.parser.DocumentParserService;
import com.edumate.core.parser.HierarchicalChunkerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentParserService parserService;

    @Autowired
    private HierarchicalChunkerService chunkerService;

    @Test
    void controllerAndServicesShouldBeWired() throws Exception {
        // 验证 MockMvc 和核心服务均可注入
        org.assertj.core.api.Assertions.assertThat(mockMvc).isNotNull();
        org.assertj.core.api.Assertions.assertThat(parserService).isNotNull();
        org.assertj.core.api.Assertions.assertThat(chunkerService).isNotNull();
    }

    @Test
    void uploadTextFileShouldReturnChunks() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "第一章 概述\n这是第一节的内容。\n## 第二章 入门\n这是第二节的内容。".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("courseName", "数据结构")
                        .param("semester", "2026-春"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("test.txt"))
                .andExpect(jsonPath("$.courseName").value("数据结构"))
                .andExpect(jsonPath("$.semester").value("2026-春"))
                .andExpect(jsonPath("$.chunkCount").isNumber());
    }
}