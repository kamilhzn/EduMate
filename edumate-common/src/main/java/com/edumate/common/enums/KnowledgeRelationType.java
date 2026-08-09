package com.edumate.common.enums;

/**
 * 知识图谱关系类型
 */
public enum KnowledgeRelationType {
    /** 前置知识 —— 学习当前知识点之前需要掌握的内容 */
    PREREQUISITE("前置知识"),
    /** 后继知识 —— 当前知识点是后续学习的基础 */
    SUCCESSOR("后继知识"),
    /** 关联 —— 两个知识点之间存在交叉或类比关系 */
    RELATED_TO("关联"),
    /** 属于 —— 知识点属于某个章节/课程 */
    BELONGS_TO("属于"),
    /** 应用 —— 知识点在某个场景中的应用 */
    APPLIED_IN("应用");

    private final String displayName;

    KnowledgeRelationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}