package com.connecthub.translationservice.service;

import com.connecthub.translationservice.dto.TranscriptionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpeechTranscriptionServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private SpeechTranscriptionService service;

    @BeforeEach
    void setUp() {
        service = new SpeechTranscriptionService(
                restTemplate,
                "groq-speech-to-text",
                "https://example.test/audio/transcriptions",
                "test-key",
                "whisper-large-v3-turbo"
        );
    }

    @Test
    void transcribesSupportedVoiceNoteAndReturnsTranscript() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "voice-note.webm",
                "audio/webm",
                "fake-audio".getBytes()
        );

        when(restTemplate.exchange(
                eq("https://example.test/audio/transcriptions"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of(
                "text", "hello from the voice note",
                "language", "en"
        )));

        TranscriptionResponse response = service.transcribe(file, "auto");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTranscript()).isEqualTo("hello from the voice note");
        assertThat(response.getSourceLanguage()).isEqualTo("en");
        assertThat(response.getProvider()).isEqualTo("groq-speech-to-text");

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://example.test/audio/transcriptions"),
                eq(HttpMethod.POST),
                requestCaptor.capture(),
                eq(Map.class)
        );

        HttpEntity<?> request = requestCaptor.getValue();
        assertThat(request.getHeaders().getContentType()).isNotNull();
        assertThat(request.getHeaders().getContentType().toString()).contains("multipart/form-data");
        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer test-key");
    }

    @Test
    void rejectsUnsupportedFileExtensionWithoutCallingProvider() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "voice-note.txt",
                "text/plain",
                "not-audio".getBytes()
        );

        TranscriptionResponse response = service.transcribe(file, "auto");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).contains("Unsupported audio format");
        verifyNoInteractions(restTemplate);
    }

    @Test
    void returnsFailureWhenProviderIsNotConfigured() {
        SpeechTranscriptionService missingKeyService = new SpeechTranscriptionService(
                restTemplate,
                "groq-speech-to-text",
                "https://example.test/audio/transcriptions",
                "",
                "whisper-large-v3-turbo"
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "voice-note.webm",
                "audio/webm",
                "fake-audio".getBytes()
        );

        TranscriptionResponse response = missingKeyService.transcribe(file, "auto");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).isEqualTo("Speech transcription provider is not configured");
        verifyNoInteractions(restTemplate);
    }

    @Test
    void keepsSpeechLanguageAutoDetectedEvenWhenAClientHintIsProvided() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "voice-note.webm",
                "audio/webm",
                "fake-audio".getBytes()
        );

        when(restTemplate.exchange(
                eq("https://example.test/audio/transcriptions"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of("text", "hello from the voice note")));

        TranscriptionResponse response = service.transcribe(file, "en-US");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getSourceLanguage()).isEqualTo("auto");

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://example.test/audio/transcriptions"),
                eq(HttpMethod.POST),
                requestCaptor.capture(),
                eq(Map.class)
        );

        MultiValueMap<String, Object> body = (MultiValueMap<String, Object>) requestCaptor.getValue().getBody();
        assertThat(body).isNotNull();
        assertThat(body.containsKey("language")).isFalse();
    }

    @Test
    void returnsFailureWhenProviderReturnsAnEmptyTranscript() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "voice-note.webm",
                "audio/webm",
                "fake-audio".getBytes()
        );

        when(restTemplate.exchange(
                eq("https://example.test/audio/transcriptions"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of("text", "   ")));

        TranscriptionResponse response = service.transcribe(file, "auto");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).isEqualTo("Speech transcription provider returned an empty transcript");
    }

    @Test
    void returnsFailureWhenProviderIsUnavailable() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "voice-note.webm",
                "audio/webm",
                "fake-audio".getBytes()
        );

        when(restTemplate.exchange(
                eq("https://example.test/audio/transcriptions"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenThrow(new RestClientException("down"));

        TranscriptionResponse response = service.transcribe(file, "auto");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).isEqualTo("Speech transcription provider is unavailable");
    }

    @Test
    void returnsFailureWhenUploadedVoiceNoteCannotBeRead() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("voice-note.webm");
        when(file.getBytes()).thenThrow(new IOException("boom"));

        TranscriptionResponse response = service.transcribe(file, "auto");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).isEqualTo("Could not read uploaded voice note");
    }
}
