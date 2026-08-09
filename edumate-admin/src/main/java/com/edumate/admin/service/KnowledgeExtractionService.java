package com.edumate.admin.service;

import com.edumate.common.model.DocumentChunk;
import com.edumate.common.model.KnowledgePoint;
import com.edumate.common.model.KnowledgeRelation;
import com.edumate.common.enums.KnowledgeRelationType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识抽取服务 —— 使用 LLM 从文档分块中提取结构化的知识点和关系
 * <p>
 * 当 ChatModel 不可用时，返回空列表（降级模式）。
 */
@Service
public class KnowledgeExtractionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeExtractionService.class);

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String EXTRACTION_PROMPT = """
            你是一个课程知识图谱构建专家。请从以下课程文档内容中提取所有知识点及其关系。

            ## 提取规则
            1. 每个知识点必须有 name（名称）和 description（描述）
            2. 关系类型包括：
               - PREREQUISITE（前置知识）：学习该知识点前需要掌握的内容
               - SUCCESSOR（后继知识）：该知识点是后续学习的基础
               - RELATED_TO（关联）：两个知识点之间存在交叉或类比关系
               - BELONGS_TO（属于）：知识点属于某个章节/课程
               - APPLIED_IN（应用）：知识点在某个场景中的应用
            3. 只提取文档中明确提到的知识点，不要编造
            4. 输出严格 JSON 数组格式

            ## 文档内容
            课程：%s
            章节：%s
            内容：
            %s

            ## 输出格式
            请输出如下格式的 JSON 数组（不要包含其他文字）：
            [
              {
                "name": "知识点名称",
                "description": "知识点描述",
                "relations": [
                  {"type": "PREREQUISITE", "targetName": "目标知识点名称", "description": "关系描述"}
                ]
              }
            ]
            """;

    public KnowledgeExtractionService(@Nullable ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 从文档分块中提取知识点和关系
     *
     * @param chunks 文档分块列表
     * @return 提取到的知识点列表（含关系）
     */
    public List<KnowledgePoint> extract(List<DocumentChunk> chunks) {
        if (chatModel == null) {
            log.warn("ChatModel 不可用，跳过知识抽取");
            return List.of();
        }
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        List<KnowledgePoint> allPoints = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        for (DocumentChunk chunk : chunks) {
            try {
                String prompt = String.format(EXTRACTION_PROMPT,
                        chunk.getCourseName(),
                        chunk.getChapterPath() != null ? chunk.getChapterPath() : "",
                        chunk.getContent());

                ChatResponse response = chatModel.chat(UserMessage.from(prompt));
                String text = response.aiMessage().text();
                List<KnowledgePointRaw> rawPoints = parseResponse(text);

                for (KnowledgePointRaw raw : rawPoints) {
                    if (seenNames.add(raw.name)) {
                        allPoints.add(KnowledgePoint.builder()
                                .name(raw.name)
                                .description(raw.description)
                                .courseName(chunk.getCourseName())
                                .chapterPath(chunk.getChapterPath())
                                .sourceChunkId(chunk.getId())
                                .relations(raw.relations != null ? raw.relations.stream()
                                        .map(r -> KnowledgeRelation.builder()
                                                .type(parseRelationType(r.type))
                                                .targetName(r.targetName)
                                                .description(r.description)
                                                .build())
                                        .collect(Collectors.toList()) : List.of())
                                .build());
                    }
                }
            } catch (Exception e) {
                log.warn("知识抽取失败 (chunk={}): {}", chunk.getId(), e.getMessage());
            }
        }

        log.info("知识抽取完成: {} 个分块 → {} 个知识点", chunks.size(), allPoints.size());
        return allPoints;
    }

    private List<KnowledgePointRaw> parseResponse(String response) throws JsonProcessingException {
        String json = response.trim();
        if (json.startsWith("```json")) {
            json = json.substring(7);
        }
        if (json.startsWith("```")) {
            json = json.substring(3);
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        json = json.trim();

        return objectMapper.readValue(json, new TypeReference<List<KnowledgePointRaw>>() {});
    }

    private KnowledgeRelationType parseRelationType(String type) {
        try {
            return KnowledgeRelationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return KnowledgeRelationType.RELATED_TO;
        }
    }

    /**
     * LLM 响应的原始 JSON 结构（用于反序列化）
     */
    private static class KnowledgePointRaw {
        public String name;
        public String description;
        public List<RelationRaw> relations;
    }

    private static class RelationRaw {
        public String type;
        public String targetName;
        public String description;
    }
}