package com.connecthub.translationservice.service.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiTranslationProviderTest {

    @Mock
    private RestTemplate restTemplate;

    private GeminiTranslationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new GeminiTranslationProvider(
                restTemplate,
                new ObjectMapper(),
                true,
                "https://generativelanguage.googleapis.com/v1beta/models",
                "gemini-2.5-flash",
                "test-gemini-key"
        );
    }

    @Test
    void returnsStructuredTranslationAndTypoCorrection() {
        when(restTemplate.exchange(
                eq("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of(
                                        "text", "{\"correctedText\":\"Hello\",\"translatedText\":\"Hola\",\"sourceLanguage\":\"en\"}"
                                ))
                        )
                ))
        )));

        Optional<TranslationProviderResult> result = provider.translate(
                new TranslationJob("Helo", "en", "es", "English")
        );

        assertThat(result).isPresent();
        assertThat(result.get().correctedText()).isEqualTo("Hello");
        assertThat(result.get().translatedText()).isEqualTo("Hola");
        assertThat(result.get().sourceLanguage()).isEqualTo("en");
        assertThat(result.get().provider()).isEqualTo("gemini");

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"),
                eq(HttpMethod.POST),
                requestCaptor.capture(),
                eq(Map.class)
        );

        assertThat(requestCaptor.getValue().getHeaders().getFirst("x-goog-api-key")).isEqualTo("test-gemini-key");
        Map<?, ?> body = (Map<?, ?>) requestCaptor.getValue().getBody();
        assertThat(body.get("generationConfig")).asString().contains("responseFormat");
        assertThat(body.get("generationConfig")).asString().contains("application/json");
    }

    @Test
    void returnsEmptyWhenApiKeyIsMissing() {
        GeminiTranslationProvider missingKeyProvider = new GeminiTranslationProvider(
                restTemplate,
                new ObjectMapper(),
                true,
                "https://generativelanguage.googleapis.com/v1beta/models",
                "gemini-2.5-flash",
                ""
        );

        Optional<TranslationProviderResult> result = missingKeyProvider.translate(
                new TranslationJob("hello", "en", "hi", "English")
        );

        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void returnsEmptyWhenGeminiPayloadIsMissingTranslatedText() {
        when(restTemplate.exchange(
                eq("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of(
                                        "text", "{\"correctedText\":\"Hello\",\"translatedText\":\"\",\"sourceLanguage\":\"en\"}"
                                ))
                        )
                ))
        )));

        Optional<TranslationProviderResult> result = provider.translate(
                new TranslationJob("Helo", "en", "es", "English")
        );

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenGeminiReturnsNoCandidateText() {
        when(restTemplate.exchange(
                eq("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of("candidates", List.of(Map.of()))));

        Optional<TranslationProviderResult> result = provider.translate(
                new TranslationJob("Helo", "en", "es", "English")
        );

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenGeminiReturnsInvalidJson() {
        when(restTemplate.exchange(
                eq("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of("text", "{not-json}"))
                        )
                ))
        )));

        Optional<TranslationProviderResult> result = provider.translate(
                new TranslationJob("Helo", "en", "es", "English")
        );

        assertThat(result).isEmpty();
    }

    @Test
    void extractsJsonWhenGeminiWrapsStructuredOutputInMarkdownFences() {
        when(restTemplate.exchange(
                eq("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of(
                                        "text", """
                                                ```json
                                                {"correctedText":"Hello","translatedText":"Hola","sourceLanguage":"en"}
                                                ```
                                                """
                                ))
                        )
                ))
        )));

        Optional<TranslationProviderResult> result = provider.translate(
                new TranslationJob("Helo", "en", "es", "English")
        );

        assertThat(result).isPresent();
        assertThat(result.get().translatedText()).isEqualTo("Hola");
    }

    @Test
    void retriesWhenEnglishTranslationStillContainsSourceScript() {
        when(restTemplate.exchange(
                eq("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(
                ResponseEntity.ok(Map.of(
                        "candidates", List.of(Map.of(
                                "content", Map.of(
                                        "parts", List.of(Map.of(
                                                "text", "{\"correctedText\":\"शाम में क्या कर रहे हो\",\"translatedText\":\"शाम में WHAT कर रहे YES\",\"sourceLanguage\":\"hi\"}"
                                        ))
                                )
                        ))
                )),
                ResponseEntity.ok(Map.of(
                        "candidates", List.of(Map.of(
                                "content", Map.of(
                                        "parts", List.of(Map.of(
                                                "text", "{\"correctedText\":\"शाम में क्या कर रहे हो\",\"translatedText\":\"What are you doing in the evening?\",\"sourceLanguage\":\"hi\"}"
                                        ))
                                )
                        ))
                ))
        );

        Optional<TranslationProviderResult> result = provider.translate(
                new TranslationJob("शाम में क्या कर रहे हो", "auto", "en", "auto")
        );

        assertThat(result).isPresent();
        assertThat(result.get().translatedText()).isEqualTo("What are you doing in the evening?");
        verify(restTemplate, org.mockito.Mockito.times(2)).exchange(
                eq("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        );
    }

    @Test
    void resolvesTemplatedGeminiEndpointsBeforeCallingTheApi() {
        GeminiTranslationProvider templatedProvider = new GeminiTranslationProvider(
                restTemplate,
                new ObjectMapper(),
                true,
                "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
                "gemini-2.5-flash",
                "test-gemini-key"
        );

        when(restTemplate.exchange(
                eq("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of(
                                        "text", "{\"correctedText\":\"Hello\",\"translatedText\":\"Hola\",\"sourceLanguage\":\"en\"}"
                                ))
                        )
                ))
        )));

        Optional<TranslationProviderResult> result = templatedProvider.translate(
                new TranslationJob("Helo", "en", "es", "English")
        );

        assertThat(result).isPresent();
        assertThat(result.get().translatedText()).isEqualTo("Hola");
    }
}
