package com.edumate.core.agent;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 拒答守卫服务 —— 两层过滤：关键词规则 + LLM 判断
 * <p>
 * 当 ChatModel 不可用时，仅使用关键词规则。
 */
@Service
public class RefusalGuardService {

    private static final Logger log = LoggerFactory.getLogger(RefusalGuardService.class);

    private final ChatModel chatModel;

    /** 关键词黑名单 */
    private static final List<Pattern> BLOCKED_PATTERNS = List.of(
            Pattern.compile("代写|代做|帮.*写.*作业|帮.*做.*作业"),
            Pattern.compile("考试.*答案|期末.*答案|试卷.*答案"),
            Pattern.compile("作弊|抄袭|替考")
    );

    /** 拒答结果 */
    public record RefusalResult(boolean shouldRefuse, String reason) {
        public static RefusalResult allow() {
            return new RefusalResult(false, "");
        }

        public static RefusalResult refuse(String reason) {
            return new RefusalResult(true, reason);
        }
    }

    public RefusalGuardService(@Nullable ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 判断是否应拒答
     *
     * @param query 用户输入
     * @return 拒答结果
     */
    public RefusalResult shouldRefuse(String query) {
        if (query == null || query.isBlank()) {
            return RefusalResult.allow();
        }

        // 第一层：关键词规则
        for (Pattern pattern : BLOCKED_PATTERNS) {
            if (pattern.matcher(query).find()) {
                log.info("拒答（关键词匹配）: '{}'", query);
                return RefusalResult.refuse("抱歉，我不能帮你完成作业或提供考试答案。请通过自主学习和练习来掌握知识。");
            }
        }

        if (chatModel == null) {
            return RefusalResult.allow();
        }

        // 第二层：LLM 判断（可选，关键词已覆盖大部分场景）
        try {
            String prompt = String.format("""
                    判断以下用户请求是否涉及代写作业、考试作弊等学术不端行为。
                    只回答 YES 或 NO。
                    用户请求：%s
                    回答：""", query);
            var response = chatModel.chat(UserMessage.from(prompt));
            if (response.aiMessage().text().trim().toUpperCase().startsWith("YES")) {
                log.info("拒答（LLM 判断）: '{}'", query);
                return RefusalResult.refuse("抱歉，我不能帮你完成作业或提供考试答案。请通过自主学习和练习来掌握知识。");
            }
        } catch (Exception e) {
            log.warn("拒答 LLM 判断失败: {}", e.getMessage());
        }

        return RefusalResult.allow();
    }
}