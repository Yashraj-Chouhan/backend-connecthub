package com.connecthub.translationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Starts the translation service that wraps provider translation calls and the
 * local fallback translation behavior.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class TranslationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TranslationServiceApplication.class, args);
    }

}
