package com.edumate.core.evaluation;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * RAGAS 端到端评测服务 —— 评估 RAG 问答的生成质量
 * <p>
 * 三个核心指标：
 * 1. Answer Relevance（答案相关性）：生成的答案是否与问题相关
 * 2. Faithfulness（忠实度）：生成的答案是否完全基于提供的上下文
 * 3. Context Relevance（上下文相关性）：检索到的上下文是否与问题相关
 * <p>
 * 当 ChatModel 不可用时，所有指标返回 0.0。
 */
@Service
public class RagasEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(RagasEvaluationService.class);

    private final ChatModel chatModel;

    private static final String ANSWER_RELEVANCE_PROMPT = """
            请评估以下生成的答案与问题之间的相关性。
            只输出一个 0 到 100 的整数分数，其中 100 表示完全相关，0 表示完全不相关。
            不要输出任何其他文字。

            问题：%s
            生成答案：%s
            分数：""";

    private static final String FAITHFULNESS_PROMPT = """
            请评估以下生成的答案是否完全基于提供的上下文，是否存在编造（幻觉）的内容。
            只输出一个 0 到 100 的整数分数，其中 100 表示完全忠实于上下文，0 表示完全编造。
            不要输出任何其他文字。

            上下文：%s
            生成答案：%s
            分数：""";

    private static final String CONTEXT_RELEVANCE_PROMPT = """
            请评估以下检索到的上下文与问题之间的相关性。
            只输出一个 0 到 100 的整数分数，其中 100 表示完全相关，0 表示完全不相关。
            不要输出任何其他文字。

            问题：%s
            上下文：%s
            分数：""";

    public RagasEvaluationService(@Nullable ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 评估答案相关性（Answer Relevance）
     *
     * @param query   用户问题
     * @param answer  生成的答案
     * @param context 检索到的上下文
     * @return 0-100 的相关性分数
     */
    public double evaluateAnswerRelevance(String query, String answer, String context) {
        if (chatModel == null) {
            log.warn("ChatModel 不可用，跳过答案相关性评测");
            return 0.0;
        }
        if (isBlank(query) || isBlank(answer)) {
            return 0.0;
        }

        try {
            String prompt = String.format(ANSWER_RELEVANCE_PROMPT, query, answer);
            return scoreFromLLM(prompt);
        } catch (Exception e) {
            log.warn("答案相关性评测失败: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * 评估忠实度（Faithfulness）
     *
     * @param context 检索到的上下文
     * @param answer  生成的答案
     * @return 0-100 的忠实度分数
     */
    public double evaluateFaithfulness(String context, String answer) {
        if (chatModel == null) {
            log.warn("ChatModel 不可用，跳过忠实度评测");
            return 0.0;
        }
        if (isBlank(context) || isBlank(answer)) {
            return 0.0;
        }

        try {
            // 截断上下文避免 token 超限
            String truncatedContext = truncate(context, 2000);
            String prompt = String.format(FAITHFULNESS_PROMPT, truncatedContext, answer);
            return scoreFromLLM(prompt);
        } catch (Exception e) {
            log.warn("忠实度评测失败: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * 评估上下文相关性（Context Relevance）
     *
     * @param query   用户问题
     * @param context 检索到的上下文
     * @return 0-100 的上下文相关性分数
     */
    public double evaluateContextRelevance(String query, String context) {
        if (chatModel == null) {
            log.warn("ChatModel 不可用，跳过上下文相关性评测");
            return 0.0;
        }
        if (isBlank(query) || isBlank(context)) {
            return 0.0;
        }

        try {
            String truncatedContext = truncate(context, 2000);
            String prompt = String.format(CONTEXT_RELEVANCE_PROMPT, query, truncatedContext);
            return scoreFromLLM(prompt);
        } catch (Exception e) {
            log.warn("上下文相关性评测失败: {}", e.getMessage());
            return 0.0;
        }
    }

    private double scoreFromLLM(String prompt) {
        var response = chatModel.chat(UserMessage.from(prompt));
        String text = response.aiMessage().text().trim();
        try {
            double score = Double.parseDouble(text);
            return Math.max(0, Math.min(100, score));
        } catch (NumberFormatException e) {
            // 尝试从文本中提取数字
            String digits = text.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                return Math.max(0, Math.min(100, Double.parseDouble(digits)));
            }
            log.warn("无法解析 LLM 评测分数: '{}'", text);
            return 0.0;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}