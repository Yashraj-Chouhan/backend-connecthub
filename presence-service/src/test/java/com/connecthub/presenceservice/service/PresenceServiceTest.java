package com.connecthub.presenceservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private PresenceService service;

    @BeforeEach
    void setUp() {
        service = new PresenceService(redisTemplate, kafkaTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void setOnlineWritesRedisAndPublishesKafkaEvent() {
        service.setOnline("user-1");

        verify(valueOperations).set("user:status:user-1", "ONLINE");
        verify(kafkaTemplate).send("user.presence", "user-1:ONLINE");
    }

    @Test
    void setOfflineDeletesRedisKeyAndPublishesKafkaEvent() {
        service.setOffline("user-1");

        verify(redisTemplate).delete("user:status:user-1");
        verify(kafkaTemplate).send("user.presence", "user-1:OFFLINE");
    }

    @Test
    void getStatusReturnsOfflineWhenNoRedisValueExists() {
        when(valueOperations.get("user:status:user-1")).thenReturn(null);

        assertThat(service.getStatus("user-1")).isEqualTo("OFFLINE");
    }

    @Test
    void getStatusReturnsStoredRedisValue() {
        when(valueOperations.get("user:status:user-1")).thenReturn("ONLINE");

        assertThat(service.getStatus("user-1")).isEqualTo("ONLINE");
    }

    @Test
    void setOnlineSwallowsInfrastructureFailures() {
        doThrow(new RuntimeException("redis down")).when(valueOperations).set("user:status:user-1", "ONLINE");

        service.setOnline("user-1");

        verify(valueOperations).set("user:status:user-1", "ONLINE");
        verifyNoMoreInteractions(kafkaTemplate);
    }

    @Test
    void getStatusFallsBackToOfflineWhenRedisFails() {
        when(valueOperations.get("user:status:user-1")).thenThrow(new RuntimeException("redis down"));

        assertThat(service.getStatus("user-1")).isEqualTo("OFFLINE");
    }
}
