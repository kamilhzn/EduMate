package com.edumate.core.chat;

import com.edumate.common.model.ChatMessage;
import com.edumate.common.model.DocumentChunk;
import com.edumate.core.agent.IntentClassifierService;
import com.edumate.core.agent.QueryRewriteService;
import com.edumate.core.agent.RefusalGuardService;
import com.edumate.core.evaluation.TraceService;
import com.edumate.core.retrieval.HybridSearchService;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 流式问答服务 —— 编排检索→改写→生成→流式输出全流程
 * <p>
 * 流程：拒答检查 → 意图识别 → Query 改写 → 混合检索 → 构建上下文 → 流式生成
 */
@Service
public class StreamingChatService {

    /** 构建上下文的结果，包含完整 Prompt 和检索到的引用源 */
    public record BuildContextResult(String prompt, List<DocumentChunk> chunks) {}

    private static final Logger log = LoggerFactory.getLogger(StreamingChatService.class);

    private final ChatModel chatModel;
    private final HybridSearchService hybridSearchService;
    private final QueryRewriteService queryRewriteService;
    private final IntentClassifierService intentClassifierService;
    private final RefusalGuardService refusalGuardService;
    private final ChatSessionService chatSessionService;
    private final TraceService traceService;

    public StreamingChatService(ChatModel chatModel,
                                HybridSearchService hybridSearchService,
                                QueryRewriteService queryRewriteService,
                                IntentClassifierService intentClassifierService,
                                RefusalGuardService refusalGuardService,
                                ChatSessionService chatSessionService,
                                TraceService traceService) {
        this.chatModel = chatModel;
        this.hybridSearchService = hybridSearchService;
        this.queryRewriteService = queryRewriteService;
        this.intentClassifierService = intentClassifierService;
        this.refusalGuardService = refusalGuardService;
        this.chatSessionService = chatSessionService;
        this.traceService = traceService;
    }

    /**
     * 构建带检索上下文的完整 Prompt
     *
     * @param query     用户查询
     * @param sessionId 会话 ID（用于多轮对话上下文）
     * @return 拼接了检索结果和对话历史的 Prompt
     */
    public String buildContext(String query, String sessionId) {
        // 1. Query 改写
        long t0 = System.currentTimeMillis();
        String rewritten = queryRewriteService.rewrite(query);
        traceService.recordStage(traceService.startTrace(query, sessionId).getTraceId(),
                "queryRewrite",
                System.currentTimeMillis() - t0,
                "original: " + query,
                "rewritten: " + rewritten,
                true, null);

        // 2. 混合检索
        List<DocumentChunk> chunks = hybridSearchService.search(rewritten, 5);

        // 3. 多轮对话历史
        List<ChatMessage> history = chatSessionService.getHistory(sessionId);

        // 4. 拼接 Prompt
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个课程学习助手，请根据以下参考资料回答用户问题。\n\n");

        if (!chunks.isEmpty()) {
            sb.append("## 参考资料\n");
            for (int i = 0; i < chunks.size(); i++) {
                sb.append("[").append(i + 1).append("] ")
                        .append(chunks.get(i).getContent()).append("\n");
            }
            sb.append("\n");
        }

        if (!history.isEmpty()) {
            sb.append("## 对话历史\n");
            for (ChatMessage msg : history) {
                sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## 用户问题\n").append(query).append("\n\n");
        sb.append("请基于参考资料回答，如果参考资料不包含相关信息，请如实说明。");

        return sb.toString();
    }

    /**
     * 构建带检索上下文的完整 Prompt，同时返回检索到的引用源
     *
     * @param query     用户查询
     * @param sessionId 会话 ID（用于多轮对话上下文）
     * @return BuildContextResult 包含 prompt 和 chunks
     */
    public BuildContextResult buildContextWithChunks(String query, String sessionId) {
        // 1. Query 改写
        long t0 = System.currentTimeMillis();
        String rewritten = queryRewriteService.rewrite(query);
        traceService.recordStage(traceService.startTrace(query, sessionId).getTraceId(),
                "queryRewrite",
                System.currentTimeMillis() - t0,
                "original: " + query,
                "rewritten: " + rewritten,
                true, null);

        // 2. 混合检索
        List<DocumentChunk> chunks = hybridSearchService.search(rewritten, 5);

        // 3. 多轮对话历史
        List<ChatMessage> history = chatSessionService.getHistory(sessionId);

        // 4. 拼接 Prompt
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个课程学习助手，请根据以下参考资料回答用户问题。\n\n");

        if (!chunks.isEmpty()) {
            sb.append("## 参考资料\n");
            for (int i = 0; i < chunks.size(); i++) {
                sb.append("[").append(i + 1).append("] ")
                        .append(chunks.get(i).getContent()).append("\n");
            }
            sb.append("\n");
        }

        if (!history.isEmpty()) {
            sb.append("## 对话历史\n");
            for (ChatMessage msg : history) {
                sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## 用户问题\n").append(query).append("\n\n");
        sb.append("请基于参考资料回答，如果参考资料不包含相关信息，请如实说明。");

        return new BuildContextResult(sb.toString(), chunks);
    }
}