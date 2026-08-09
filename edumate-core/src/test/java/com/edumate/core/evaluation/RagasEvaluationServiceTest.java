package com.edumate.core.evaluation;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RagasEvaluationServiceTest {

    @Mock
    private ChatModel chatModel;

    @Test
    void shouldReturnZeroWhenChatModelIsNull() {
        RagasEvaluationService service = new RagasEvaluationService(null);
        double score = service.evaluateAnswerRelevance("问: 二叉树", "答: 二叉树是...", "二叉树...");
        assertEquals(0.0, score, 0.001);
    }

    @Test
    void shouldReturnZeroWhenInputsAreBlank() {
        RagasEvaluationService service = new RagasEvaluationService(chatModel);
        double score = service.evaluateAnswerRelevance("", "", "");
        assertEquals(0.0, score, 0.001);
    }
}