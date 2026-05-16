package com.connecthub.translationservice.controller;

import com.connecthub.translationservice.dto.TranscriptionResponse;
import com.connecthub.translationservice.dto.TranslationRequest;
import com.connecthub.translationservice.dto.TranslationResponse;
import com.connecthub.translationservice.service.SpeechTranscriptionService;
import com.connecthub.translationservice.service.TranslationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslationControllerTest {

    @Mock
    private TranslationService translationService;

    @Mock
    private SpeechTranscriptionService speechTranscriptionService;

    @Test
    void translateDelegatesToTranslationService() {
        TranslationController controller = new TranslationController(translationService, speechTranscriptionService);
        TranslationRequest request = new TranslationRequest("Hello", "es", "en");
        TranslationResponse response = TranslationResponse.builder()
                .translatedText("Hola")
                .success(true)
                .provider("gemini")
                .build();
        when(translationService.translate(request)).thenReturn(response);

        TranslationResponse actual = controller.translate(request);

        assertThat(actual).isSameAs(response);
        verify(translationService).translate(request);
    }

    @Test
    void transcribeDelegatesToSpeechTranscriptionService() {
        TranslationController controller = new TranslationController(translationService, speechTranscriptionService);
        MockMultipartFile file = new MockMultipartFile("file", "voice-note.webm", "audio/webm", "audio".getBytes());
        TranscriptionResponse response = TranscriptionResponse.builder()
                .transcript("hello")
                .sourceLanguage("en")
                .provider("groq-speech-to-text")
                .success(true)
                .build();
        when(speechTranscriptionService.transcribe(file, "en")).thenReturn(response);

        TranscriptionResponse actual = controller.transcribe(file, "en");

        assertThat(actual).isSameAs(response);
        verify(speechTranscriptionService).transcribe(file, "en");
    }
}
