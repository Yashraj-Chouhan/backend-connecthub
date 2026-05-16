package com.connecthub.websocketservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Starts the real-time delivery service that handles WebSocket traffic and
 * Kafka-backed fan-out of saved chat messages.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.connecthub.websocketservice.client")
@ConfigurationPropertiesScan
public class WebsocketServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebsocketServiceApplication.class, args);
    }
}
