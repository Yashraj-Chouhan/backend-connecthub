package com.connecthub.translationservice.service;

import com.connecthub.translationservice.dto.TranslationRequest;
import com.connecthub.translationservice.dto.TranslationResponse;
import com.connecthub.translationservice.service.provider.TranslationJob;
import com.connecthub.translationservice.service.provider.TranslationProvider;
import com.connecthub.translationservice.service.provider.TranslationProviderResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslationServiceTest {

    @Mock
    private TranslationProvider geminiProvider;

    @Mock
    private TranslationProvider libreTranslateProvider;

    private TranslationService service;

    @BeforeEach
    void setUp() {
        service = new TranslationService(List.of(geminiProvider, libreTranslateProvider));
    }

    @Test
    void usesGeminiFirstWhenProviderSucceedsAndReturnsCorrectedText() {
        when(geminiProvider.translate(any())).thenReturn(Optional.of(new TranslationProviderResult(
                "Hello",
                "Hola",
                "en",
                "es",
                "gemini"
        )));

        TranslationResponse response = service.translate(new TranslationRequest("Helo", "Spanish", "English"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCorrectedText()).isEqualTo("Hello");
        assertThat(response.getTranslatedText()).isEqualTo("Hola");
        assertThat(response.getSourceLanguage()).isEqualTo("en");
        assertThat(response.getTargetLanguage()).isEqualTo("es");
        assertThat(response.getProvider()).isEqualTo("gemini");

        ArgumentCaptor<TranslationJob> jobCaptor = ArgumentCaptor.forClass(TranslationJob.class);
        verify(geminiProvider).translate(jobCaptor.capture());
        assertThat(jobCaptor.getValue().sourceLanguage()).isEqualTo("en");
        assertThat(jobCaptor.getValue().targetLanguage()).isEqualTo("es");
        assertThat(jobCaptor.getValue().sourceLanguageHint()).isEqualTo("English");
        verifyNoInteractions(libreTranslateProvider);
    }

    @Test
    void fallsBackToLibreTranslateWhenGeminiCannotTranslate() {
        when(geminiProvider.translate(any())).thenReturn(Optional.empty());
        when(libreTranslateProvider.translate(any())).thenReturn(Optional.of(new TranslationProviderResult(
                "hello",
                "नमस्ते",
                "en",
                "hi",
                "libretranslate"
        )));

        TranslationResponse response = service.translate(new TranslationRequest("hello", "Hindi", "auto"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCorrectedText()).isEqualTo("hello");
        assertThat(response.getTranslatedText()).isEqualTo("नमस्ते");
        assertThat(response.getTargetLanguage()).isEqualTo("hi");
        assertThat(response.getProvider()).isEqualTo("libretranslate");
        verify(geminiProvider).translate(any());
        verify(libreTranslateProvider).translate(any());
    }

    @Test
    void usesGeminiForSameLanguageRequestsSoTypoCorrectionStillWorks() {
        when(geminiProvider.translate(any())).thenReturn(Optional.of(new TranslationProviderResult(
                "Hello world",
                "Hello world",
                "en",
                "en",
                "gemini"
        )));

        TranslationResponse response = service.translate(new TranslationRequest("Helo world", "English", "English"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCorrectedText()).isEqualTo("Hello world");
        assertThat(response.getTranslatedText()).isEqualTo("Hello world");
        assertThat(response.getTargetLanguage()).isEqualTo("en");
        assertThat(response.getProvider()).isEqualTo("gemini");
        verifyNoInteractions(libreTranslateProvider);
    }

    @Test
    void fallsBackToHindiDevanagariWhenRemoteProvidersAreUnavailable() {
        when(geminiProvider.translate(any())).thenReturn(Optional.empty());
        when(libreTranslateProvider.translate(any())).thenReturn(Optional.empty());

        TranslationResponse response = service.translate(new TranslationRequest("hello", "Hindi", "auto"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCorrectedText()).isEqualTo("hello");
        assertThat(response.getTranslatedText()).isEqualTo("नमस्ते");
        assertThat(response.getTargetLanguage()).isEqualTo("hi");
        assertThat(response.getProvider()).isEqualTo("offline-fallback");
    }

    @Test
    void fallsBackToJapaneseWhenRemoteProvidersAreUnavailable() {
        when(geminiProvider.translate(any())).thenReturn(Optional.empty());
        when(libreTranslateProvider.translate(any())).thenReturn(Optional.empty());

        TranslationResponse response = service.translate(new TranslationRequest("hello", "Japanese", "auto"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTranslatedText()).isEqualTo("こんにちは");
        assertThat(response.getTargetLanguage()).isEqualTo("ja");
        assertThat(response.getProvider()).isEqualTo("offline-fallback");
    }

    @Test
    void fallsBackToKannadaScriptWhenRemoteProvidersAreUnavailable() {
        when(geminiProvider.translate(any())).thenReturn(Optional.empty());
        when(libreTranslateProvider.translate(any())).thenReturn(Optional.empty());

        TranslationResponse response = service.translate(new TranslationRequest("hello", "Kannada", "auto"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTranslatedText()).isEqualTo("ನಮಸ್ಕಾರ");
        assertThat(response.getTargetLanguage()).isEqualTo("kn");
        assertThat(response.getProvider()).isEqualTo("offline-fallback");
    }

    @Test
    void translatesCommonEnglishPhrasesIntoKannadaWhenRemoteProvidersAreUnavailable() {
        when(geminiProvider.translate(any())).thenReturn(Optional.empty());
        when(libreTranslateProvider.translate(any())).thenReturn(Optional.empty());

        Map<String, String> examples = Map.of(
                "hello", "ನಮಸ್ಕಾರ",
                "thank you", "ಧನ್ಯವಾದಗಳು",
                "good morning", "ಶುಭೋದಯ",
                "how are you", "ನೀವು ಹೇಗಿದ್ದೀರಾ",
                "see you", "ಮತ್ತೆ ಸಿಗೋಣ"
        );

        for (Map.Entry<String, String> example : examples.entrySet()) {
            TranslationResponse response = service.translate(new TranslationRequest(example.getKey(), "Kannada", "auto"));

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getTranslatedText()).isEqualTo(example.getValue());
            assertThat(response.getTargetLanguage()).isEqualTo("kn");
            assertThat(response.getProvider()).isEqualTo("offline-fallback");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "Kannada,kn",
            "Malayalam,ml",
            "Tamil,ta",
            "Telugu,te",
            "Marathi,mr",
            "Bengali,bn",
            "Punjabi,pa"
    })
    void fallsBackToEveryAdvertisedLanguageWhenRemoteProvidersAreUnavailable(String requestedLanguage, String expectedCode) {
        when(geminiProvider.translate(any())).thenReturn(Optional.empty());
        when(libreTranslateProvider.translate(any())).thenReturn(Optional.empty());

        TranslationResponse response = service.translate(new TranslationRequest("hello", requestedLanguage, "auto"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTranslatedText()).isNotBlank().isNotEqualTo("hello");
        assertThat(response.getTargetLanguage()).isEqualTo(expectedCode);
        assertThat(response.getProvider()).isEqualTo("offline-fallback");
    }

    @Test
    void returnsValidationFailureWhenTextOrTargetLanguageIsMissing() {
        TranslationResponse response = service.translate(new TranslationRequest("hello", " ", "auto"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).contains("required");
        assertThat(response.getProvider()).isEqualTo("translation-service");
        verifyNoInteractions(geminiProvider, libreTranslateProvider);
    }

    @Test
    void offlineFallbackCanTranslateBackToEnglish() {
        when(geminiProvider.translate(any())).thenReturn(Optional.empty());
        when(libreTranslateProvider.translate(any())).thenReturn(Optional.empty());

        TranslationResponse response = service.translate(new TranslationRequest("こんにちは", "English", "auto"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTranslatedText()).isNotBlank().isNotEqualTo("こんにちは");
        assertThat(response.getProvider()).isEqualTo("offline-fallback");
        assertThat(response.getTargetLanguage()).isEqualTo("en");
    }

    @Test
    void rejectsBrokenOfflineFallbackForHindiSentenceTranslatedToEnglish() {
        when(geminiProvider.translate(any())).thenReturn(Optional.empty());
        when(libreTranslateProvider.translate(any())).thenReturn(Optional.empty());

        TranslationResponse response = service.translate(new TranslationRequest("शाम में क्या कर रहे हो", "English", "auto"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).contains("temporarily unavailable");
        assertThat(response.getProvider()).isEqualTo("translation-service");
    }

    @Test
    void skipsLowQualityLibreTranslateOutputForIndicTargets() {
        when(geminiProvider.translate(any())).thenReturn(Optional.empty());
        when(libreTranslateProvider.translate(any())).thenReturn(Optional.of(new TranslationProviderResult(
                "hello",
                "namaste",
                "en",
                "hi",
                "libretranslate"
        )));

        TranslationResponse response = service.translate(new TranslationRequest("hello", "Hindi", "English"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getProvider()).isEqualTo("offline-fallback");
        assertThat(response.getTranslatedText()).isEqualTo("नमस्ते");
    }

    @ParameterizedTest
    @CsvSource({
            "Hindi, hi, नमस्ते",
            "Marathi, mr, नमस्कार",
            "Gujarati, gu, નમસ્તે",
            "Bengali, bn, নমস্কার",
            "Punjabi, pa, ਸਤ ਸ੍ਰੀ ਅਕਾਲ",
            "Kannada, kn, ನಮಸ್ಕಾರ",
            "Malayalam, ml, നമസ്കാരം",
            "Tamil, ta, வணக்கம்",
            "Telugu, te, నమస్కారం",
            "Japanese, ja, こんにちは"
    })
    void acceptsProviderTranslationsThatUseTheExpectedScript(String requestedLanguage,
                                                             String expectedCode,
                                                             String translatedText) {
        when(geminiProvider.translate(any())).thenReturn(Optional.of(new TranslationProviderResult(
                "hello",
                translatedText,
                "en",
                expectedCode,
                "gemini"
        )));

        TranslationResponse response = service.translate(new TranslationRequest("hello", requestedLanguage, "English"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTranslatedText()).isEqualTo(translatedText);
        assertThat(response.getTargetLanguage()).isEqualTo(expectedCode);
        assertThat(response.getProvider()).isEqualTo("gemini");
        verifyNoInteractions(libreTranslateProvider);
    }
}
