package com.edumate.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能出题 —— 题目模型
 *
 * 题型说明：
 *   - single_choice   : 单选题，options 含 4 个选项，answers 含 1 个正确答案
 *   - multiple_choice : 多选题，options 含 4~5 个选项，answers 含 1~多个正确答案
 *   - fill_blank      : 填空题，options 为空，answers 含 1~多个可接受的正确答案
 *   - short_answer    : 简答题，options 为空，answers 为参考答案要点列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizQuestion {

    /** 题型：single_choice / multiple_choice / fill_blank / short_answer */
    private String type;

    /** 题干 */
    private String stem;

    /** 选项列表（选择题） */
    @Builder.Default
    private List<String> options = new ArrayList<>();

    /**
     * 旧字段：单答案（向后兼容，优先使用 {@link #answers}）
     */
    private String answer;

    /**
     * 新字段：答案列表
     * <p>单选题：1 个元素</p>
     * <p>多选题：多个元素（如 ["A", "C"]）</p>
     * <p>填空题：多个可接受的等价答案（如 ["词法分析", "扫描"]）</p>
     * <p>简答题：参考答案要点列表</p>
     */
    @Builder.Default
    private List<String> answers = new ArrayList<>();

    /** 答案解析 */
    private String explanation;

    /** 关联知识点 */
    @Builder.Default
    private List<String> knowledgePoints = new ArrayList<>();

    /** 难度：easy / medium / hard */
    private String difficulty;

    /**
     * 兼容获取答案列表：优先返回 answers，否则将旧 answer 转为单元素列表
     */
    public List<String> getAnswers() {
        if (answers != null && !answers.isEmpty()) {
            return answers;
        }
        if (answer != null && !answer.isBlank()) {
            return List.of(answer.trim());
        }
        return List.of();
    }
}
