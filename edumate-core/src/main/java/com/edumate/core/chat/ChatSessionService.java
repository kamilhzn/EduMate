package com.edumate.core.chat;

import com.edumate.common.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 多轮对话会话服务 —— 基于 Redis List 存储对话历史
 * <p>
 * Redis Key 格式：chat:session:{sessionId}
 * 每个会话默认保留最近 20 条消息，TTL 为 30 分钟。
 */
@Service
public class ChatSessionService {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionService.class);

    private static final String KEY_PREFIX = "chat:session:";
    private static final int MAX_HISTORY = 20;
    private static final int TTL_MINUTES = 30;

    private final RedisTemplate<String, Object> redisTemplate;

    public ChatSessionService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 添加消息到会话历史
     *
     * @param sessionId 会话 ID
     * @param message   消息
     */
    public void addMessage(String sessionId, ChatMessage message) {
        String key = KEY_PREFIX + sessionId;
        redisTemplate.opsForList().rightPush(key, message);
        // 裁剪到 MAX_HISTORY 条
        redisTemplate.opsForList().trim(key, -MAX_HISTORY, -1);
        redisTemplate.expire(key, TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("会话 {} 添加消息: role={}", sessionId, message.getRole());
    }

    /**
     * 获取会话历史
     *
     * @param sessionId 会话 ID
     * @return 历史消息列表（按时间顺序）
     */
    public List<ChatMessage> getHistory(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        List<Object> raw = redisTemplate.opsForList().range(key, 0, -1);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChatMessage> messages = new ArrayList<>();
        for (Object obj : raw) {
            if (obj instanceof ChatMessage msg) {
                messages.add(msg);
            }
        }
        return messages;
    }

    /**
     * 清空会话历史
     *
     * @param sessionId 会话 ID
     */
    public void clearSession(String sessionId) {
        redisTemplate.delete(KEY_PREFIX + sessionId);
        log.debug("会话 {} 已清空", sessionId);
    }
}