package com.connecthub.websocketservice.client;

import com.connecthub.websocketservice.dto.UserSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "auth-service", path = "/auth")
public interface AuthClient {

    @GetMapping("/users/{userId}")
    UserSummaryResponse getUserById(@PathVariable("userId") String userId);

    @PutMapping("/users/{userId}/status")
    Map<String, Object> updateStatus(@PathVariable("userId") String userId,
                                     @RequestParam String status);
}
