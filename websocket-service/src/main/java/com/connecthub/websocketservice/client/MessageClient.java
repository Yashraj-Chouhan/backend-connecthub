package com.connecthub.websocketservice.client;

import com.connecthub.websocketservice.dto.ChatMessage;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "message-service", path = "/messages")
public interface MessageClient {

    @PostMapping
    ChatMessage saveMessage(@RequestBody ChatMessage message);
}
