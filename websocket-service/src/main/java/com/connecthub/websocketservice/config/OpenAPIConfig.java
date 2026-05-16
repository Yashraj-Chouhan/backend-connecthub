package com.connecthub.websocketservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI websocketServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("WebSocket Service API")
                        .description("ConnectHub real-time messaging and delivery event service")
                        .version("v0.0.1"));
    }
}
