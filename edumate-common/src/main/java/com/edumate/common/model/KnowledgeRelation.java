package com.edumate.common.model;

import com.edumate.common.enums.KnowledgeRelationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识点关系 —— 知识图谱中的边
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRelation {
    /** 关系类型 */
    private KnowledgeRelationType type;
    /** 目标知识点名称 */
    private String targetName;
    /** 关系描述 */
    private String description;
}