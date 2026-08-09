package com.edumate.core.agent;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * 意图识别服务 —— 分类用户查询意图
 * <p>
 * 当 ChatModel 不可用时，默认返回 COURSE_QA。
 */
@Service
public class IntentClassifierService {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifierService.class);

    private final ChatModel chatModel;

    /** 意图枚举 */
    public enum Intent {
        COURSE_QA,          // 课程问答
        QUIZ_GENERATION,    // 出题请求
        CONCEPT_EXPLANATION,// 概念解释
        REFUSAL             // 需拒答（代写作业等）
    }

    private static final String CLASSIFY_PROMPT = """
            你是一个课程学习助手。请判断用户输入的意图，只输出以下标签之一：
            - COURSE_QA：询问课程知识、概念、习题解答
            - QUIZ_GENERATION：要求生成题目、出题、模拟考试
            - CONCEPT_EXPLANATION：要求解释某个概念或术语
            - REFUSAL：要求代写作业、考试作弊、提供完整作业答案等违规请求

            用户输入：%s
            意图标签：""";

    public IntentClassifierService(@Nullable ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 分类用户意图
     *
     * @param query 用户输入
     * @return 意图标签
     */
    public Intent classify(String query) {
        if (chatModel == null) {
            return Intent.COURSE_QA;
        }
        if (query == null || query.isBlank()) {
            return Intent.COURSE_QA;
        }

        try {
            String prompt = String.format(CLASSIFY_PROMPT, query);
            var response = chatModel.chat(UserMessage.from(prompt));
            String label = response.aiMessage().text().trim().toUpperCase();
            for (Intent intent : Intent.values()) {
                if (label.contains(intent.name())) {
                    log.debug("意图识别: '{}' → {}", query, intent);
                    return intent;
                }
            }
            return Intent.COURSE_QA;
        } catch (Exception e) {
            log.warn("意图识别失败: {}", e.getMessage());
            return Intent.COURSE_QA;
        }
    }
}