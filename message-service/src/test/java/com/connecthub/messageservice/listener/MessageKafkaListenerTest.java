package com.connecthub.messageservice.listener;

import com.connecthub.messageservice.dto.MessageRequest;
import com.connecthub.messageservice.entity.Message;
import com.connecthub.messageservice.service.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageKafkaListenerTest {

    @Mock
    private MessageService messageService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void listenIncomingMessagePersistsAndPublishes() {
        MessageKafkaListener listener = new MessageKafkaListener(messageService, kafkaTemplate);
        MessageRequest request = new MessageRequest();
        request.setRoomId("room-1");
        Message saved = Message.builder().id(1L).roomId("room-1").build();

        when(messageService.saveMessage(request)).thenReturn(saved);

        listener.listenIncomingMessage(request);

        verify(messageService).saveMessage(request);
        verify(kafkaTemplate).send("chat.message.broadcast", saved);
    }

    @Test
    void listenIncomingMessageSwallowsProcessingFailures() {
        MessageKafkaListener listener = new MessageKafkaListener(messageService, kafkaTemplate);
        MessageRequest request = new MessageRequest();
        request.setRoomId("room-1");

        doThrow(new RuntimeException("boom")).when(messageService).saveMessage(request);

        listener.listenIncomingMessage(request);

        verify(messageService).saveMessage(request);
        verifyNoInteractions(kafkaTemplate);
    }
}
