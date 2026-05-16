package com.connecthub.messageservice.listener;

import com.connecthub.messageservice.dto.MessageRequest;
import com.connecthub.messageservice.entity.Message;
import com.connecthub.messageservice.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
/**
 * Consumes incoming chat events from Kafka, stores the durable message record,
 * and republishes the saved message for real-time broadcast.
 */
@RequiredArgsConstructor
public class MessageKafkaListener {

    private final MessageService messageService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "chat.message.incoming", groupId = "message-service-group")
    public void listenIncomingMessage(MessageRequest request) {
        log.info("Received incoming message from kafka room={}", request.getRoomId());
        try {
            Message savedMessage = messageService.saveMessage(request);
            kafkaTemplate.send("chat.message.broadcast", savedMessage);
            log.info("Successfully persisted and published broadcast message for room={}", request.getRoomId());
        } catch (Exception e) {
            log.error("Failed to process incoming message from Kafka", e);
        }
    }
}
