package com.connecthub.translationservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI translationServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Translation Service API")
                        .description("ConnectHub translation API for chat content localization")
                        .version("v0.0.1"));
    }
}
