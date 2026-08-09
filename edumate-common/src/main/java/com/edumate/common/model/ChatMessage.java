package com.edumate.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 对话消息 —— 用于多轮会话存储
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    /** 消息角色：user / assistant */
    private String role;
    /** 消息内容 */
    private String content;
    /** 时间戳 */
    @Builder.Default
    private Instant timestamp = Instant.now();
}