package com.edumate.core.chat;

import com.edumate.common.model.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ListOperations<String, Object> listOperations;

    private ChatSessionService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        service = new ChatSessionService(redisTemplate);
    }

    @Test
    void shouldAddAndRetrieveMessages() {
        ChatMessage msg = ChatMessage.builder().role("user").content("什么是二叉树").build();
        when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(List.of(msg));

        service.addMessage("session-1", msg);
        List<ChatMessage> history = service.getHistory("session-1");

        assertEquals(1, history.size());
        assertEquals("什么是二叉树", history.get(0).getContent());
    }

    @Test
    void shouldReturnEmptyHistoryForNewSession() {
        when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(List.of());
        List<ChatMessage> history = service.getHistory("new-session");
        assertTrue(history.isEmpty());
    }
}