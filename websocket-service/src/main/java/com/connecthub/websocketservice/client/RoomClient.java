package com.connecthub.websocketservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

@FeignClient(name = "room-service", path = "/rooms")
public interface RoomClient {

    @GetMapping("/{roomId}")
    Map<String, Object> getRoom(@PathVariable("roomId") String roomId);

    @GetMapping("/{roomId}/members")
    List<Map<String, Object>> getMembers(@PathVariable("roomId") String roomId);
}
