package com.connecthub.websocketservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.websocket")
public class WebSocketProperties {

    private String endpoint = "/ws";
    private String applicationDestinationPrefix = "/app";
    private List<String> brokerPrefixes = new ArrayList<>(List.of("/topic"));
    private List<String> allowedOriginPatterns = new ArrayList<>(List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "https://localhost:*",
            "https://127.0.0.1:*"
    ));
    private Duration heartbeatIncoming = Duration.ofSeconds(10);
    private Duration heartbeatOutgoing = Duration.ofSeconds(10);
    private int schedulerPoolSize = 1;
    private String roomTopicPrefix = "/topic/room";
    private String userTopicPrefix = "/topic/user";
    private int messageSizeLimitBytes = 262_144;
    private int sendBufferSizeLimitBytes = 524_288;
    private int sendTimeLimitMillis = 20_000;
    private int maxTextMessageBufferSizeBytes = 262_144;
    private int maxBinaryMessageBufferSizeBytes = 262_144;
}
