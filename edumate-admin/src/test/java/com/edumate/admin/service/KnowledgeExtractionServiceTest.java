package com.edumate.admin.service;

import com.edumate.common.model.DocumentChunk;
import com.edumate.common.model.KnowledgePoint;
import com.edumate.common.enums.KnowledgeRelationType;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeExtractionServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ChatResponse chatResponse;

    @Mock
    private AiMessage aiMessage;

    private KnowledgeExtractionService extractionService;

    @BeforeEach
    void setUp() {
        extractionService = new KnowledgeExtractionService(chatModel);
    }

    @Test
    void shouldExtractKnowledgePointsFromChunks() {
        String llmResponse = """
                [
                  {
                    "name": "红黑树",
                    "description": "一种自平衡二叉搜索树，通过颜色标记节点来维持平衡",
                    "relations": [
                      {
                        "type": "PREREQUISITE",
                        "targetName": "二叉搜索树",
                        "description": "红黑树是二叉搜索树的扩展"
                      },
                      {
                        "type": "RELATED_TO",
                        "targetName": "AVL树",
                        "description": "都是自平衡二叉搜索树，平衡策略不同"
                      }
                    ]
                  },
                  {
                    "name": "二叉搜索树",
                    "description": "左子树节点值小于根节点，右子树节点值大于根节点的二叉树",
                    "relations": []
                  }
                ]""";

        when(chatModel.chat(any(UserMessage.class))).thenReturn(chatResponse);
        when(chatResponse.aiMessage()).thenReturn(aiMessage);
        when(aiMessage.text()).thenReturn(llmResponse);

        List<DocumentChunk> chunks = List.of(
                DocumentChunk.builder()
                        .id("chunk-1")
                        .content("红黑树是一种自平衡二叉搜索树，通过颜色标记节点来维持平衡。它是二叉搜索树的扩展。")
                        .courseName("数据结构")
                        .chapterPath("第3章 > 3.2节 > 红黑树")
                        .build()
        );

        List<KnowledgePoint> points = extractionService.extract(chunks);

        assertThat(points).hasSize(2);
        assertThat(points.get(0).getName()).isEqualTo("红黑树");
        assertThat(points.get(0).getRelations()).hasSize(2);
        assertThat(points.get(0).getRelations().get(0).getType())
                .isEqualTo(KnowledgeRelationType.PREREQUISITE);
    }

    @Test
    void shouldReturnEmptyListWhenChatModelIsNull() {
        List<DocumentChunk> chunks = List.of(
                DocumentChunk.builder()
                        .id("chunk-1")
                        .content("测试内容")
                        .courseName("数据结构")
                        .build()
        );

        KnowledgeExtractionService serviceWithNullModel = new KnowledgeExtractionService(null);
        List<KnowledgePoint> points = serviceWithNullModel.extract(chunks);

        assertThat(points).isEmpty();
    }

    @Test
    void shouldReturnEmptyListForEmptyChunks() {
        List<KnowledgePoint> points = extractionService.extract(List.of());
        assertThat(points).isEmpty();
    }
}