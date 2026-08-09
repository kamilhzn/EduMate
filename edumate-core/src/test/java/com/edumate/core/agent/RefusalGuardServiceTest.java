package com.edumate.core.agent;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RefusalGuardServiceTest {

    @Mock
    private ChatModel chatModel;

    @Test
    void shouldNotRefuseWhenChatModelIsNull() {
        RefusalGuardService service = new RefusalGuardService(null);
        assertFalse(service.shouldRefuse("二叉树").shouldRefuse());
    }

    @Test
    void shouldRefuseBasedOnKeywords() {
        RefusalGuardService service = new RefusalGuardService(chatModel);
        assertTrue(service.shouldRefuse("帮我代写作业").shouldRefuse());
        assertTrue(service.shouldRefuse("给我考试的答案").shouldRefuse());
        assertFalse(service.shouldRefuse("什么是红黑树").shouldRefuse());
    }
}