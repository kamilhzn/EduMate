package com.edumate.core.agent;

import com.edumate.common.model.QuizQuestion;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class QuizAgentServiceTest {

    @Mock
    private ChatModel chatModel;

    @Test
    void shouldReturnEmptyListWhenChatModelIsNull() {
        QuizAgentService service = new QuizAgentService(null);
        List<QuizQuestion> result = service.generate("数据结构", null, 3, "easy");
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyListWhenCourseIsBlank() {
        QuizAgentService service = new QuizAgentService(chatModel);
        List<QuizQuestion> result = service.generate("", null, 3, "easy");
        assertTrue(result.isEmpty());
    }
}