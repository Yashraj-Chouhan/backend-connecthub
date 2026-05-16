package com.connecthub.messageservice.service;

import com.connecthub.messageservice.dto.MessageRealtimeEvent;
import com.connecthub.messageservice.entity.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TranscriptEventPublisher {

    private static final String TOPIC = "chat.message.transcribed";
    private static final String EVENT_TYPE = "MESSAGE_TRANSCRIBED";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(Message message) {
        if (message == null) {
            return;
        }

        MessageRealtimeEvent event = MessageRealtimeEvent.builder()
                .messageId(message.getId())
                .sender(message.getSender())
                .roomId(message.getRoomId())
                .content(message.getContent())
                .originalContent(message.getContent())
                .timestamp(message.getTimestamp())
                .messageType(message.getMessageType())
                .attachmentName(message.getAttachmentName())
                .attachmentPath(message.getAttachmentPath())
                .attachmentContentType(message.getAttachmentContentType())
                .attachmentSize(message.getAttachmentSize())
                .replyToMessageId(message.getReplyToMessageId())
                .deleted(message.isDeleted())
                .editedAt(message.getEditedAt())
                .deletedAt(message.getDeletedAt())
                .detectedLanguage(message.getDetectedLanguage())
                .transcript(message.getTranscript())
                .transcriptSourceLanguage(message.getTranscriptSourceLanguage())
                .transcriptUpdatedAt(message.getTranscriptUpdatedAt())
                .eventType(EVENT_TYPE)
                .build();

        kafkaTemplate.send(TOPIC, event);
    }
}
