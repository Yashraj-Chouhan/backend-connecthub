package com.connecthub.websocketservice.listener;

import com.connecthub.websocketservice.dto.ChatMessage;
import com.connecthub.websocketservice.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
/**
 * Receives saved chat messages from Kafka so they can be broadcast to WebSocket
 * subscribers after durable persistence has succeeded.
 */
@RequiredArgsConstructor
public class ChatKafkaListener {

    private final ChatService chatService;

    @KafkaListener(topics = "chat.message.broadcast", groupId = "websocket-service-group")
    public void listenBroadcastMessage(ChatMessage message) {
        log.info("Received broadcast message from Kafka, room={}", message.getRoomId());
        try {
            chatService.handleBroadcastMessage(message);
        } catch (Exception e) {
            log.error("Failed to handle broadcast message from Kafka", e);
        }
    }

    @KafkaListener(topics = "chat.message.transcribed", groupId = "websocket-service-group")
    public void listenTranscriptMessage(ChatMessage message) {
        log.info("Received transcript update from Kafka, room={}", message.getRoomId());
        try {
            chatService.handleTranscriptMessage(message);
        } catch (Exception e) {
            log.error("Failed to handle transcript update from Kafka", e);
        }
    }
}
