package com.edumate.core.graph;

import com.edumate.common.model.KnowledgePoint;
import com.edumate.common.model.KnowledgeRelation;
import org.neo4j.driver.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱存储服务 —— 将知识点和关系写入 Neo4j
 * <p>
 * 使用原生 Neo4j Java Driver，通过 Cypher MERGE 实现 upsert 语义。
 * 当 Driver 不可用时（Neo4j 未启动），操作降级为空操作。
 */
@Service
public class KnowledgeGraphService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphService.class);

    private final Driver driver;

    @Value("${neo4j.database:neo4j}")
    private String database;

    public KnowledgeGraphService(@Nullable Driver driver) {
        this.driver = driver;
    }

    /**
     * 批量保存知识点到 Neo4j
     * <p>
     * 每个知识点创建为 KnowledgePoint 节点，
     * 每个关系创建为有向边（带类型和描述属性）。
     */
    public void savePoints(List<KnowledgePoint> points) {
        if (driver == null) {
            log.warn("Neo4j Driver 不可用，跳过图谱存储");
            return;
        }
        if (points == null || points.isEmpty()) {
            return;
        }

        try (Session session = driver.session(SessionConfig.forDatabase(database))) {
            Transaction tx = session.beginTransaction();

            for (KnowledgePoint point : points) {
                // 创建知识点节点
                tx.run("""
                        MERGE (k:KnowledgePoint {name: $name})
                        SET k.description = $description,
                            k.courseName = $courseName,
                            k.chapterPath = $chapterPath,
                            k.sourceChunkId = $sourceChunkId
                        """,
                        Map.of(
                                "name", point.getName(),
                                "description", point.getDescription() != null ? point.getDescription() : "",
                                "courseName", point.getCourseName() != null ? point.getCourseName() : "",
                                "chapterPath", point.getChapterPath() != null ? point.getChapterPath() : "",
                                "sourceChunkId", point.getSourceChunkId() != null ? point.getSourceChunkId() : ""
                        ));

                // 创建关系
                if (point.getRelations() != null) {
                    for (KnowledgeRelation rel : point.getRelations()) {
                        // 先确保目标节点存在（占位节点）
                        tx.run("""
                                MERGE (target:KnowledgePoint {name: $targetName})
                                ON CREATE SET target.description = '',
                                    target.courseName = $courseName,
                                    target.chapterPath = '',
                                    target.sourceChunkId = ''
                                """,
                                Map.of(
                                        "targetName", rel.getTargetName(),
                                        "courseName", point.getCourseName() != null ? point.getCourseName() : ""
                                ));

                        // 创建关系
                        String relType = rel.getType() != null ? rel.getType().name() : "RELATED_TO";
                        tx.run("""
                                MATCH (a:KnowledgePoint {name: $fromName})
                                MATCH (b:KnowledgePoint {name: $toName})
                                MERGE (a)-[r:%s]->(b)
                                SET r.description = $description
                                """.formatted(relType),
                                Map.of(
                                        "fromName", point.getName(),
                                        "toName", rel.getTargetName(),
                                        "description", rel.getDescription() != null ? rel.getDescription() : ""
                                ));
                    }
                }
            }

            tx.commit();
            log.info("图谱存储完成: {} 个知识点", points.size());
        } catch (Exception e) {
            log.error("图谱存储失败", e);
        }
    }
}