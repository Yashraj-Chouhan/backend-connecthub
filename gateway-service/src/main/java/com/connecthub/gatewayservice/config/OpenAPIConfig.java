package com.connecthub.gatewayservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI gatewayServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Gateway Service API")
                        .description("ConnectHub API gateway with JWT request filtering")
                        .version("v0.0.1"));
    }
}
