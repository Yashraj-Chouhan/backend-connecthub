package com.connecthub.gatewayservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(GatewayCorsProperties.class)
/**
 * Registers the gateway-wide CORS policy used by browser clients in local and
 * preview environments.
 */
public class GatewayConfig {

    private final GatewayCorsProperties corsProperties;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        List<String> allowedOriginPatterns = corsProperties.getAllowedOriginPatterns().stream()
                .filter(pattern -> pattern != null && !pattern.isBlank())
                .toList();

        corsConfig.setAllowedOriginPatterns(allowedOriginPatterns);
        corsConfig.setMaxAge(corsProperties.getMaxAge());
        corsConfig.setAllowedMethods(corsProperties.getAllowedMethods());
        corsConfig.setAllowedHeaders(corsProperties.getAllowedHeaders());
        corsConfig.setAllowCredentials(corsProperties.isAllowCredentials());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        return new CorsWebFilter(source);
    }
}
