package com.connecthub.messageservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "room-service")
public interface RoomClient {

    @PutMapping("/rooms/{roomId}/last-message")
    Map<String, Object> updateLastMessageAt(@PathVariable("roomId") String roomId, @RequestParam(required = false) String timestamp);
}
