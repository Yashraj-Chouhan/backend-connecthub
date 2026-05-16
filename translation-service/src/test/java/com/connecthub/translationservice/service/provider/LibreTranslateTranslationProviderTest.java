package com.connecthub.translationservice.service.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LibreTranslateTranslationProviderTest {

    @Mock
    private RestTemplate restTemplate;

    private LibreTranslateTranslationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new LibreTranslateTranslationProvider(
                restTemplate,
                true,
                "https://example.test/translate",
                "libre-key"
        );
    }

    @Test
    void translatesAndExtractsDetectedLanguage() {
        when(restTemplate.exchange(
                eq("https://example.test/translate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of(
                "translatedText", "Hola",
                "detectedLanguage", Map.of("language", "en")
        )));

        Optional<TranslationProviderResult> result = provider.translate(
                new TranslationJob("hello", "en", "es", "English")
        );

        assertThat(result).isPresent();
        assertThat(result.get().correctedText()).isEqualTo("hello");
        assertThat(result.get().translatedText()).isEqualTo("Hola");
        assertThat(result.get().sourceLanguage()).isEqualTo("en");
        assertThat(result.get().provider()).isEqualTo("libretranslate");

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://example.test/translate"),
                eq(HttpMethod.POST),
                requestCaptor.capture(),
                eq(Map.class)
        );

        Map<?, ?> body = (Map<?, ?>) requestCaptor.getValue().getBody();
        assertThat(body.get("source")).isEqualTo("en");
        assertThat(body.get("target")).isEqualTo("es");
        assertThat(body.get("api_key")).isEqualTo("libre-key");
    }

    @Test
    void returnsEmptyWhenProviderReturnsBlankTranslation() {
        when(restTemplate.exchange(
                eq("https://example.test/translate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of("translatedText", "   ")));

        Optional<TranslationProviderResult> result = provider.translate(
                new TranslationJob("hello", "en", "es", "English")
        );

        assertThat(result).isEmpty();
    }

    @Test
    void usesStringDetectedLanguageWhenProviderReturnsIt() {
        when(restTemplate.exchange(
                eq("https://example.test/translate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of(
                "translatedText", "Hola",
                "detectedLanguage", "en"
        )));

        Optional<TranslationProviderResult> result = provider.translate(
                new TranslationJob("hello", "auto", "es", "English")
        );

        assertThat(result).isPresent();
        assertThat(result.get().sourceLanguage()).isEqualTo("en");
    }

    @Test
    void fallsBackToAutoWhenDetectedLanguageIsMissing() {
        when(restTemplate.exchange(
                eq("https://example.test/translate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of("translatedText", "Hola")));

        Optional<TranslationProviderResult> result = provider.translate(
                new TranslationJob("hello", "", "es", "English")
        );

        assertThat(result).isPresent();
        assertThat(result.get().sourceLanguage()).isEqualTo("auto");
    }

    @Test
    void returnsEmptyWhenProviderReturnsNoBody() {
        when(restTemplate.exchange(
                eq("https://example.test/translate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(null));

        Optional<TranslationProviderResult> result = provider.translate(
                new TranslationJob("hello", "en", "es", "English")
        );

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenProviderIsDisabled() {
        LibreTranslateTranslationProvider disabledProvider = new LibreTranslateTranslationProvider(
                restTemplate,
                false,
                "https://example.test/translate",
                "libre-key"
        );

        Optional<TranslationProviderResult> result = disabledProvider.translate(
                new TranslationJob("hello", "en", "es", "English")
        );

        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }
}
