package com.edumate.core.agent;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Query 改写服务 —— 将用户简短口语化查询扩展为更精准的检索查询
 * <p>
 * 例如："B+树" → "B+树 定义 结构 特点 应用场景"
 * <p>
 * 当 ChatModel 不可用时，返回原始查询。
 */
@Service
public class QueryRewriteService {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriteService.class);

    private final ChatModel chatModel;

    private static final String REWRITE_PROMPT = """
            你是一个课程学习助手。请将用户的简短查询改写为更完整、更利于检索的查询语句。
            规则：
            1. 保留原意，扩展关键术语和同义词
            2. 补充该知识点常见的关联概念
            3. 不要添加用户未提及的内容
            4. 直接输出改写后的查询，不要解释

            用户查询：%s
            改写结果：""";

    public QueryRewriteService(@Nullable ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 改写查询语句
     *
     * @param query 原始查询
     * @return 改写后的查询（若 LLM 不可用则返回原始查询）
     */
    public String rewrite(String query) {
        if (chatModel == null) {
            return query;
        }
        if (query == null || query.isBlank()) {
            return query;
        }

        try {
            String prompt = String.format(REWRITE_PROMPT, query);
            var response = chatModel.chat(UserMessage.from(prompt));
            String rewritten = response.aiMessage().text().trim();
            log.debug("Query 改写: '{}' → '{}'", query, rewritten);
            return rewritten.isBlank() ? query : rewritten;
        } catch (Exception e) {
            log.warn("Query 改写失败: {}", e.getMessage());
            return query;
        }
    }
}