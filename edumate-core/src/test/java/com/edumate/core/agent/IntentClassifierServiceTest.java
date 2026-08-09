package com.edumate.core.agent;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class IntentClassifierServiceTest {

    @Mock
    private ChatModel chatModel;

    @Test
    void shouldReturnCourseQaWhenChatModelIsNull() {
        IntentClassifierService service = new IntentClassifierService(null);
        assertEquals(IntentClassifierService.Intent.COURSE_QA, service.classify("二叉树"));
    }

    @Test
    void shouldReturnCourseQaWhenInputIsBlank() {
        IntentClassifierService service = new IntentClassifierService(chatModel);
        assertEquals(IntentClassifierService.Intent.COURSE_QA, service.classify(""));
    }
}