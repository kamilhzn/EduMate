package com.edumate.admin.controller;

import com.edumate.core.retrieval.HybridSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SearchController 集成测试
 * <p>
 * 需要 Docker 服务运行（Qdrant + ES），否则 EmbeddingStore Bean 创建会失败。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HybridSearchService hybridSearchService;

    @Test
    void controllerAndServicesShouldBeWired() {
        org.assertj.core.api.Assertions.assertThat(mockMvc).isNotNull();
        org.assertj.core.api.Assertions.assertThat(hybridSearchService).isNotNull();
    }

    @Test
    void searchShouldReturnResultsArray() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType("application/json")
                        .content("{\"query\":\"数据结构\",\"topK\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.query").value("数据结构"));
    }

    @Test
    void searchWithEmptyQueryShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType("application/json")
                        .content("{\"query\":\"\",\"topK\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("query 不能为空"));
    }

    @Test
    void searchWithDefaultTopK() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType("application/json")
                        .content("{\"query\":\"测试查询\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultsCount").isNumber());
    }
}