package com.connecthub.websocketservice.controller;

import com.connecthub.websocketservice.dto.ChatMessage;
import com.connecthub.websocketservice.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
/**
 * Receives client STOMP messages and forwards them to the chat service for
 * routing, persistence, or broadcast handling.
 */
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @MessageMapping("/chat.send")
    public void sendMessage(ChatMessage message) {
        chatService.handleMessage(message);
    }

    @MessageMapping("/chat.typing")
    public void typing(ChatMessage message) {
        chatService.handleTyping(message);
    }

    @MessageMapping("/chat.read")
    public void readReceipt(ChatMessage message) {
        chatService.handleReadReceipt(message);
    }
}
