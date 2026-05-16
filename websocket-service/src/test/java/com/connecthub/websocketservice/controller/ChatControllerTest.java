package com.connecthub.websocketservice.controller;

import com.connecthub.websocketservice.dto.ChatMessage;
import com.connecthub.websocketservice.service.ChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    @InjectMocks
    private ChatController controller;

    @Test
    void delegatesStompMessageMappings() {
        ChatMessage message = ChatMessage.builder().roomId("room-1").sender("alice").build();

        controller.sendMessage(message);
        controller.typing(message);
        controller.readReceipt(message);

        verify(chatService).handleMessage(message);
        verify(chatService).handleTyping(message);
        verify(chatService).handleReadReceipt(message);
    }
}
