package com.edumate.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 知识点 —— 知识图谱中的节点
 * <p>
 * 用于 API 层传输，与 Neo4j 内部存储结构解耦。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgePoint {
    /** 知识点名称（唯一标识），如 "红黑树" */
    private String name;
    /** 知识点描述 */
    private String description;
    /** 所属课程 */
    private String courseName;
    /** 章节路径 */
    private String chapterPath;
    /** 来源分块 ID */
    private String sourceChunkId;
    /** 关联的知识点及关系 */
    private List<KnowledgeRelation> relations;
}