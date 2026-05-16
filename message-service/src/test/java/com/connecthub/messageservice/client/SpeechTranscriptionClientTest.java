package com.connecthub.messageservice.client;

import com.connecthub.messageservice.dto.TranscriptionResponse;
import com.connecthub.messageservice.entity.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeechTranscriptionClientTest {

    @TempDir
    Path tempDir;

    @Test
    void transcribeInfersWebmFilenameForBlobVoiceNotes() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        SpeechTranscriptionClient client = new SpeechTranscriptionClient(restTemplate, "http://localhost:9013");
        Path blobPath = tempDir.resolve("blob");
        Files.writeString(blobPath, "voice");

        Message message = Message.builder()
                .attachmentPath(blobPath.toString())
                .attachmentName("blob")
                .attachmentContentType("audio/webm")
                .build();

        when(restTemplate.exchange(
                eq("http://localhost:9013/transcribe"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(TranscriptionResponse.class)
        )).thenReturn(ResponseEntity.ok(TranscriptionResponse.builder()
                .success(true)
                .transcript("hello")
                .build()));

        client.transcribe(message, "auto");

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("http://localhost:9013/transcribe"),
                eq(HttpMethod.POST),
                requestCaptor.capture(),
                eq(TranscriptionResponse.class)
        );

        HttpEntity<?> request = requestCaptor.getValue();
        assertThat(request.getBody()).isInstanceOf(MultiValueMap.class);

        @SuppressWarnings("unchecked")
        MultiValueMap<String, Object> body = (MultiValueMap<String, Object>) request.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getFirst("file")).isInstanceOf(Resource.class);
        assertThat(((Resource) body.getFirst("file")).getFilename()).isEqualTo("voice-note.webm");
        assertThat(body.containsKey("sourceLang")).isFalse();
    }
}
