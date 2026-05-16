package com.connecthub.websocketservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocketMessageBroker
/**
 * Registers the STOMP endpoint, broker prefixes, and heartbeat scheduler used
 * by the real-time chat experience.
 */
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketProperties properties;

    @Bean
    public ThreadPoolTaskScheduler webSocketTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.getSchedulerPoolSize());
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(properties.getMaxTextMessageBufferSizeBytes());
        container.setMaxBinaryMessageBufferSize(properties.getMaxBinaryMessageBufferSizeBytes());
        return container;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.setApplicationDestinationPrefixes(properties.getApplicationDestinationPrefix());
        config.enableSimpleBroker(properties.getBrokerPrefixes().toArray(String[]::new))
                .setHeartbeatValue(new long[] {
                        properties.getHeartbeatOutgoing().toMillis(),
                        properties.getHeartbeatIncoming().toMillis()
                })
                .setTaskScheduler(webSocketTaskScheduler());
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(properties.getMessageSizeLimitBytes());
        registry.setSendBufferSizeLimit(properties.getSendBufferSizeLimitBytes());
        registry.setSendTimeLimit(properties.getSendTimeLimitMillis());
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(properties.getEndpoint())
                .setAllowedOriginPatterns(properties.getAllowedOriginPatterns().toArray(String[]::new))
                .withSockJS();
    }
}
