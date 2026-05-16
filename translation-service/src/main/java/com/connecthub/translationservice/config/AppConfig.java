package com.connecthub.translationservice.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    @Qualifier("translationRestTemplate")
    public RestTemplate translationRestTemplate(@Value("${translation.api.timeout:5000}") int timeoutMs) {
        return buildRestTemplate(timeoutMs);
    }

    @Bean
    @Qualifier("speechRestTemplate")
    public RestTemplate speechRestTemplate(@Value("${speech.api.timeout:30000}") int timeoutMs) {
        return buildRestTemplate(timeoutMs);
    }

    private RestTemplate buildRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);

        return new RestTemplate(factory);
    }
}
