package com.edumate.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 评测样本 —— 一条标注好的问答对 + 标准答案（Ground Truth）
 * <p>
 * 用于评测检索质量（Recall@K、MRR）和生成质量（RAGAS）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationSample {
    /** 样本唯一 ID */
    private String id;
    /** 用户自然语言查询 */
    private String query;
    /** 期望检索到的文档分块 ID 列表（Ground Truth） */
    private List<String> relevantChunkIds;
    /** 期望的标准答案文本（用于 RAGAS 评测） */
    private String expectedAnswer;
    /** 所属课程名称 */
    private String courseName;
    /** 考察的知识点标签 */
    private List<String> knowledgePoints;
    /** 难度级别：easy / medium / hard */
    @Builder.Default
    private String difficulty = "medium";
}