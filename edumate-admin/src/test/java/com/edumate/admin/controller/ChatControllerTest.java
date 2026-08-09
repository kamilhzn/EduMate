package com.edumate.admin.controller;

import com.edumate.core.agent.IntentClassifierService;
import com.edumate.core.agent.RefusalGuardService;
import com.edumate.core.chat.ChatSessionService;
import com.edumate.core.chat.StreamingChatService;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatModel chatModel;

    @MockitoBean
    private StreamingChatService streamingChatService;

    @MockitoBean
    private IntentClassifierService intentClassifierService;

    @MockitoBean
    private RefusalGuardService refusalGuardService;

    @MockitoBean
    private ChatSessionService chatSessionService;

    @Test
    void shouldReturn400WhenQueryIsBlank() throws Exception {
        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}