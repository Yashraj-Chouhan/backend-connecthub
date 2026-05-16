package com.connecthub.translationservice.service.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Order(2)
@Slf4j
public class LibreTranslateTranslationProvider implements TranslationProvider {

    private static final String PROVIDER = "libretranslate";

    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final String apiUrl;
    private final String apiKey;

    public LibreTranslateTranslationProvider(@Qualifier("translationRestTemplate") RestTemplate restTemplate,
                                            @Value("${translation.api.enabled:true}") boolean enabled,
                                            @Value("${translation.api.url:}") String apiUrl,
                                            @Value("${translation.api.key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.enabled = enabled;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }

    @Override
    public Optional<TranslationProviderResult> translate(TranslationJob job) {
        if (!enabled || !StringUtils.hasText(apiUrl)) {
            return Optional.empty();
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("q", job.originalText());
        body.put("source", job.sourceLanguage());
        body.put("target", job.targetLanguage());
        body.put("format", "text");
        if (StringUtils.hasText(apiKey)) {
            body.put("api_key", apiKey);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            Map<?, ?> responseBody = response.getBody();
            if (responseBody == null) {
                log.warn("LibreTranslate returned an empty body");
                return Optional.empty();
            }

            Object translatedTextValue = responseBody.get("translatedText");
            if (!(translatedTextValue instanceof String translatedText) || !StringUtils.hasText(translatedText)) {
                log.warn("LibreTranslate returned an empty translation");
                return Optional.empty();
            }

            return Optional.of(new TranslationProviderResult(
                    job.originalText(),
                    translatedText,
                    extractSourceLanguage(responseBody, job.sourceLanguage()),
                    job.targetLanguage(),
                    PROVIDER
            ));
        } catch (HttpStatusCodeException ex) {
            log.warn("LibreTranslate returned {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("LibreTranslate request failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private String extractSourceLanguage(Map<?, ?> responseBody, String fallbackSourceLang) {
        Object detectedLanguage = responseBody.get("detectedLanguage");
        if (detectedLanguage instanceof Map<?, ?> detectedLanguageMap) {
            Object language = detectedLanguageMap.get("language");
            if (language instanceof String languageText && StringUtils.hasText(languageText)) {
                return languageText;
            }
        }

        if (detectedLanguage instanceof String detectedLanguageText && StringUtils.hasText(detectedLanguageText)) {
            return detectedLanguageText;
        }

        return StringUtils.hasText(fallbackSourceLang) ? fallbackSourceLang : "auto";
    }
}
