package com.connecthub.websocketservice.client;

import com.connecthub.websocketservice.dto.NotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "notification-service", path = "/notifications")
public interface NotificationClient {

    @PostMapping
    Map<String, Object> createNotification(@RequestBody NotificationRequest request);
}
