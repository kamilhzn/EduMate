package com.edumate.core.agent;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class QueryRewriteServiceTest {

    @Mock
    private ChatModel chatModel;

    @Test
    void shouldReturnOriginalQueryWhenChatModelIsNull() {
        QueryRewriteService service = new QueryRewriteService(null);
        String result = service.rewrite("二叉树");
        assertEquals("二叉树", result);
    }

    @Test
    void shouldReturnOriginalQueryWhenInputIsBlank() {
        QueryRewriteService service = new QueryRewriteService(chatModel);
        String result = service.rewrite("");
        assertEquals("", result);
    }
}