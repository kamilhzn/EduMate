package com.edumate.core.graph;

import com.edumate.common.model.DocumentChunk;
import org.neo4j.driver.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 图谱检索服务 —— 基于 Neo4j 知识图谱的关系检索
 * <p>
 * 检索策略：
 * 1. 名称匹配：查找名称与查询相关的知识点节点
 * 2. 多跳遍历：沿关系扩展 1-2 跳，获取关联知识点
 * 3. 返回关联的 DocumentChunk（通过 sourceChunkId 关联回原始文档）
 * <p>
 * 当 Driver 不可用时（Neo4j 未启动），返回空列表。
 */
@Service
public class GraphSearchService {

    private static final Logger log = LoggerFactory.getLogger(GraphSearchService.class);

    private final Driver driver;

    @Value("${neo4j.database:neo4j}")
    private String database;

    public GraphSearchService(@Nullable Driver driver) {
        this.driver = driver;
    }

    /**
     * 图谱检索 —— 查询与用户输入相关的知识点及其关联内容
     *
     * @param query 用户查询
     * @param topK  返回结果数量上限
     * @return 关联的文档分块列表
     */
    public List<DocumentChunk> search(String query, int topK) {
        if (driver == null) {
            log.warn("Neo4j Driver 不可用，返回空图谱检索结果");
            return List.of();
        }

        try (Session session = driver.session(SessionConfig.forDatabase(database))) {
            // 策略：名称模糊匹配 + 1-2 跳图遍历，返回关联的知识点
            String cypher = """
                    MATCH (k:KnowledgePoint)
                    WHERE k.name CONTAINS $query
                    OPTIONAL MATCH (k)-[r]->(related:KnowledgePoint)
                    WHERE type(r) IN ['PREREQUISITE', 'SUCCESSOR', 'RELATED_TO', 'APPLIED_IN']
                    RETURN DISTINCT
                        k.name AS name,
                        k.description AS description,
                        k.courseName AS courseName,
                        k.chapterPath AS chapterPath,
                        k.sourceChunkId AS sourceChunkId,
                        k.description AS content
                    UNION
                    MATCH (related:KnowledgePoint)-[r]->(k:KnowledgePoint)
                    WHERE k.name CONTAINS $query
                      AND type(r) IN ['PREREQUISITE', 'SUCCESSOR', 'RELATED_TO', 'APPLIED_IN']
                    RETURN DISTINCT
                        related.name AS name,
                        related.description AS description,
                        related.courseName AS courseName,
                        related.chapterPath AS chapterPath,
                        related.sourceChunkId AS sourceChunkId,
                        related.description AS content
                    LIMIT $limit
                    """;

            List<DocumentChunk> results = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();

            var records = session.run(cypher,
                    Map.of("query", query, "limit", topK * 2)).list();

            for (var record : records) {
                String sourceChunkId = record.get("sourceChunkId").asString(null);
                String id = sourceChunkId != null ? sourceChunkId : record.get("name").asString();
                if (seenIds.add(id)) {
                    results.add(DocumentChunk.builder()
                            .id(id)
                            .content(record.get("content").asString(""))
                            .courseName(record.get("courseName").asString(""))
                            .chapterPath(record.get("chapterPath").asString(""))
                            .build());
                }
            }

            log.debug("图谱检索: query='{}' → {} 条结果", query, results.size());
            return results.stream().limit(topK).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("图谱检索失败", e);
            return List.of();
        }
    }
}