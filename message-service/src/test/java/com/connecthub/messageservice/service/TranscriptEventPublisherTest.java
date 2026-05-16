package com.connecthub.messageservice.service;

import com.connecthub.messageservice.entity.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TranscriptEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void publishSkipsNullMessages() {
        TranscriptEventPublisher publisher = new TranscriptEventPublisher(kafkaTemplate);

        publisher.publish(null);

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void publishSendsTranscriptEvent() {
        TranscriptEventPublisher publisher = new TranscriptEventPublisher(kafkaTemplate);
        Message message = Message.builder()
                .id(9L)
                .sender("user-1")
                .roomId("room-1")
                .content("hello")
                .messageType("VOICE_NOTE")
                .transcript("hello")
                .transcriptSourceLanguage("en")
                .build();

        publisher.publish(message);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(org.mockito.ArgumentMatchers.eq("chat.message.transcribed"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).hasFieldOrPropertyWithValue("messageId", 9L);
        assertThat(payloadCaptor.getValue()).hasFieldOrPropertyWithValue("eventType", "MESSAGE_TRANSCRIBED");
    }
}
