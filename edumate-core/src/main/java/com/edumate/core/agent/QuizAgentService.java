package com.edumate.core.agent;

import com.edumate.common.model.QuizQuestion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 智能出题 Agent —— ReAct 模式（规划 → 执行 → 验证）
 * <p>
 * Step 1 - Plan（规划）：根据课程和章节，确定出题范围和题型分布
 * Step 2 - Execute（执行）：调用 LLM 生成题目及标准答案
 * Step 3 - Verify（验证）：调用 LLM 自检题目质量，过滤不合格题目
 * <p>
 * 当 ChatModel 不可用时，返回空列表。
 */
@Service
public class QuizAgentService {

    private static final Logger log = LoggerFactory.getLogger(QuizAgentService.class);

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String GENERATE_PROMPT = """
            你是一位大学课程出题专家。请根据以下信息生成 %d 道 %s 难度的题目。

            课程：%s
            %s

            ## 出题要求
            1. 题型分布：单选题 30%%，多选题 20%%，填空题 20%%，简答题 30%%
            2. 难度说明：easy=基础概念，medium=综合应用，hard=深入分析
            3. 每道题必须包含：题干(stem)、答案列表(answers)、解析(explanation)、关联知识点(knowledgePoints)

            ## 各题型格式说明
            - 单选题(type=single_choice)：options 含 4 个选项(A/B/C/D)，answers 含 1 个正确选项字母（如 ["A"]）
            - 多选题(type=multiple_choice)：options 含 4~5 个选项，answers 含多个正确选项字母（如 ["A", "C"]）
            - 填空题(type=fill_blank)：options 为空数组，answers 含 1~多个可接受的等价答案（如 ["词法分析", "扫描"]）
            - 简答题(type=short_answer)：options 为空数组，answers 为参考答案要点列表（如 ["要点1", "要点2"]）

            ## 输出格式
            请输出严格 JSON 数组（不要包含其他文字）：
            [
              {
                "type": "single_choice",
                "stem": "题干",
                "options": ["A. ...", "B. ...", "C. ...", "D. ..."],
                "answers": ["A"],
                "explanation": "解析",
                "knowledgePoints": ["知识点1"],
                "difficulty": "easy"
              },
              {
                "type": "fill_blank",
                "stem": "____是编译器的第一个阶段，负责将源代码字符流切分为记号序列。",
                "options": [],
                "answers": ["词法分析", "扫描"],
                "explanation": "词法分析阶段（又称扫描阶段）将字符流转换为记号流。",
                "knowledgePoints": ["编译器阶段"],
                "difficulty": "easy"
              }
            ]
            """;

    public QuizAgentService(@Nullable ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 生成题目
     *
     * @param courseName 课程名称
     * @param chapter    章节（可选）
     * @param count      题目数量
     * @param difficulty 难度：easy / medium / hard
     * @return 题目列表
     */
    public List<QuizQuestion> generate(String courseName, @Nullable String chapter,
                                        int count, String difficulty) {
        if (chatModel == null) {
            log.warn("ChatModel 不可用，跳过出题");
            return List.of();
        }
        if (courseName == null || courseName.isBlank()) {
            return List.of();
        }

        // Step 1: Plan（规划）—— 内置在 Prompt 中
        // Step 2: Execute（生成题目）
        String chapterInfo = (chapter != null && !chapter.isBlank())
                ? "章节：" + chapter : "";
        String prompt = String.format(GENERATE_PROMPT, count, difficulty, courseName, chapterInfo);

        try {
            var response = chatModel.chat(UserMessage.from(prompt));
            String text = response.aiMessage().text();

            List<QuizQuestion> questions = parseResponse(text);
            log.info("出题 Agent 完成: {} 课程 → {} 道题目", courseName, questions.size());

            // Step 3: Verify（验证）—— 过滤掉明显不合格的题目
            List<QuizQuestion> valid = questions.stream()
                    .filter(q -> q.getStem() != null && !q.getStem().isBlank())
                    .filter(q -> {
                        List<String> answers = q.getAnswers();
                        return answers != null && !answers.isEmpty();
                    })
                    .toList();
            if (valid.size() < questions.size()) {
                log.warn("出题验证: 过滤了 {} 道不合格题目", questions.size() - valid.size());
            }
            return valid;
        } catch (Exception e) {
            log.error("出题 Agent 失败", e);
            return List.of();
        }
    }

    private List<QuizQuestion> parseResponse(String response) throws JsonProcessingException {
        String json = response.trim();
        if (json.startsWith("```json")) {
            json = json.substring(7);
        }
        if (json.startsWith("```")) {
            json = json.substring(3);
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        json = json.trim();
        return objectMapper.readValue(json, new TypeReference<List<QuizQuestion>>() {});
    }
}