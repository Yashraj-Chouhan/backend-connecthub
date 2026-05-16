package com.connecthub.presenceservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration
@Slf4j
public class RedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.database:0}") int database,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.timeout:6s}") Duration timeout
    ) {
        RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration();
        standaloneConfig.setHostName(host);
        standaloneConfig.setPort(port);
        standaloneConfig.setDatabase(database);

        if (StringUtils.hasText(password)) {
            standaloneConfig.setPassword(password);
        }

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(timeout)
                .shutdownTimeout(Duration.ofSeconds(2))
                .build();

        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(standaloneConfig, clientConfig);
        connectionFactory.afterPropertiesSet();

        // Validate Redis reachability during startup so misconfiguration is visible immediately.
        try (var connection = connectionFactory.getConnection()) {
            connection.ping();
            log.info("Redis initialized at {}:{} (db={})", host, port, database);
        } catch (Exception ex) {
            log.warn("Redis not reachable at startup. Presence will degrade to OFFLINE defaults until Redis is available.", ex);
        }

        return connectionFactory;
    }

    @Bean
    public StringRedisTemplate redisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}