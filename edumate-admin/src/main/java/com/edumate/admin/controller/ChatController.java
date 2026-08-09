package com.edumate.admin.controller;

import com.edumate.common.model.ChatMessage;
import com.edumate.common.model.DocumentChunk;
import com.edumate.core.agent.IntentClassifierService;
import com.edumate.core.agent.RefusalGuardService;
import com.edumate.core.chat.ChatSessionService;
import com.edumate.core.chat.StreamingChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 流式问答控制器 —— SSE 逐 token 推送
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatModel chatModel;
    private final StreamingChatService streamingChatService;
    private final IntentClassifierService intentClassifierService;
    private final RefusalGuardService refusalGuardService;
    private final ChatSessionService chatSessionService;
    private final ObjectMapper objectMapper;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(ChatModel chatModel,
                          StreamingChatService streamingChatService,
                          IntentClassifierService intentClassifierService,
                          RefusalGuardService refusalGuardService,
                          ChatSessionService chatSessionService,
                          ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.streamingChatService = streamingChatService;
        this.intentClassifierService = intentClassifierService;
        this.refusalGuardService = refusalGuardService;
        this.chatSessionService = chatSessionService;
        this.objectMapper = objectMapper;
    }

    /**
     * SSE 流式问答
     *
     * @param request 包含 query（必填）和 sessionId（可选，不传则自动生成）
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseBodyEmitter streamChat(@RequestBody ChatController.StreamRequest request) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(300_000L); // 5 分钟超时

        String query = request.query();
        String sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();

        executor.execute(() -> {
            try {
                // 1. 拒答检查
                var refusal = refusalGuardService.shouldRefuse(query);
                if (refusal.shouldRefuse()) {
                    emitter.send(formatSSE("message", refusal.reason()), MediaType.TEXT_PLAIN);
                    emitter.send(formatSSE("done", "[DONE]"), MediaType.TEXT_PLAIN);
                    emitter.complete();
                    return;
                }

                // 2. 保存用户消息
                chatSessionService.addMessage(sessionId,
                        ChatMessage.builder().role("user").content(query).timestamp(Instant.now()).build());

                // 3. 构建上下文（含检索引用源）
                var contextResult = streamingChatService.buildContextWithChunks(query, sessionId);

                // 3a. 发送引用源元数据（供前端渲染可点击的引用卡片）
                if (!contextResult.chunks().isEmpty()) {
                    List<Map<String, Object>> refs = new ArrayList<>();
                    for (int i = 0; i < contextResult.chunks().size(); i++) {
                        DocumentChunk chunk = contextResult.chunks().get(i);
                        String snippet = chunk.getContent();
                        if (snippet != null && snippet.length() > 80) {
                            snippet = snippet.substring(0, 80) + "...";
                        }
                        refs.add(Map.of(
                                "index", i + 1,
                                "courseName", chunk.getCourseName() != null ? chunk.getCourseName() : "",
                                "chapterPath", chunk.getChapterPath() != null ? chunk.getChapterPath() : "",
                                "snippet", snippet != null ? snippet : ""
                        ));
                    }
                    String refsJson = objectMapper.writeValueAsString(refs);
                    emitter.send(formatSSE("references", refsJson), MediaType.TEXT_PLAIN);
                }

                // 4. 流式生成（非流式 ChatModel 降级为一次性返回）
                var response = chatModel.chat(UserMessage.from(contextResult.prompt()));
                String fullAnswer = response.aiMessage().text();

                // 模拟逐 token 推送（将完整回复按字符分批发送）
                for (int i = 0; i < fullAnswer.length(); i += 5) {
                    int end = Math.min(i + 5, fullAnswer.length());
                    String chunk = fullAnswer.substring(i, end);
                    emitter.send(formatSSE("message", chunk), MediaType.TEXT_PLAIN);
                    Thread.sleep(20); // 模拟流式延迟
                }

                // 5. 保存助手消息
                chatSessionService.addMessage(sessionId,
                        ChatMessage.builder().role("assistant").content(fullAnswer).timestamp(Instant.now()).build());

                // 6. 发送完成信号（含 sessionId）
                String doneJson = objectMapper.writeValueAsString(
                        Map.of("status", "DONE", "sessionId", sessionId));
                emitter.send(formatSSE("done", doneJson), MediaType.TEXT_PLAIN);
                emitter.complete();

            } catch (Exception e) {
                log.error("流式问答异常", e);
                try {
                    emitter.send(formatSSE("error", "服务内部错误: " + e.getMessage()), MediaType.TEXT_PLAIN);
                } catch (IOException ex) {
                    log.error("SSE 发送失败", ex);
                }
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(() -> log.debug("SSE 连接完成: session={}", sessionId));
        emitter.onTimeout(() -> log.warn("SSE 连接超时: session={}", sessionId));

        return emitter;
    }

    /**
     * 手动构建 SSE 事件字符串，确保 data: 和值在同一行
     * 解决 Spring SseEmitter.event().data() 将 data: 前缀和值分在不同行的问题
     */
    private String formatSSE(String event, String data) {
        StringBuilder sb = new StringBuilder();
        sb.append("event:").append(event).append('\n');
        // 多行数据按 SSE 规范每行加 data: 前缀
        String[] lines = data.split("\n", -1);
        for (String line : lines) {
            sb.append("data:").append(line).append('\n');
        }
        sb.append('\n'); // 空行结束事件
        return sb.toString();
    }

    public record StreamRequest(String query, String sessionId) {}
}