package com.connecthub.presenceservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
/**
 * Reads and writes current user presence in Redis and emits presence change
 * events for any future consumers.
 */
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    private final StringRedisTemplate redis;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String PREFIX = "user:status:";

    public void setOnline(String userId) {
        try {
            redis.opsForValue().set(PREFIX + userId, "ONLINE");
            kafkaTemplate.send("user.presence", userId + ":ONLINE");
        } catch (Exception ex) {
            log.error("Failed to write ONLINE status for userId={}", userId, ex);
        }
    }

    public void setOffline(String userId) {
        try {
            redis.delete(PREFIX + userId);
            kafkaTemplate.send("user.presence", userId + ":OFFLINE");
        } catch (Exception ex) {
            log.error("Failed to delete status for userId={}", userId, ex);
        }
    }

    public String getStatus(String userId) {
        try {
            String status = redis.opsForValue().get(PREFIX + userId);
            return status != null ? status : "OFFLINE";
        } catch (Exception ex) {
            log.error("Failed to read status for userId={}", userId, ex);
            return "OFFLINE";
        }
    }
}
