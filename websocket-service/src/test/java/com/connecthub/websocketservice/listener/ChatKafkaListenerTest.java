package com.connecthub.websocketservice.listener;

import com.connecthub.websocketservice.dto.ChatMessage;
import com.connecthub.websocketservice.service.ChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatKafkaListenerTest {

    @Mock
    private ChatService chatService;

    @InjectMocks
    private ChatKafkaListener listener;

    @Test
    void delegatesBroadcastAndTranscriptMessages() {
        ChatMessage message = ChatMessage.builder().roomId("room-1").build();

        listener.listenBroadcastMessage(message);
        listener.listenTranscriptMessage(message);

        verify(chatService).handleBroadcastMessage(message);
        verify(chatService).handleTranscriptMessage(message);
    }

    @Test
    void swallowsListenerFailures() {
        ChatMessage message = ChatMessage.builder().roomId("room-1").build();
        doThrow(new RuntimeException("boom")).when(chatService).handleBroadcastMessage(message);
        doThrow(new RuntimeException("boom")).when(chatService).handleTranscriptMessage(message);

        assertThatNoException().isThrownBy(() -> {
            listener.listenBroadcastMessage(message);
            listener.listenTranscriptMessage(message);
        });
    }
}
