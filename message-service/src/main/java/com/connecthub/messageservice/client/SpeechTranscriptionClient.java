package com.connecthub.messageservice.client;

import com.connecthub.messageservice.dto.TranscriptionResponse;
import com.connecthub.messageservice.entity.Message;
import com.connecthub.messageservice.util.VoiceNoteFilenameSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class SpeechTranscriptionClient {

    private final RestTemplate restTemplate;
    private final String translationServiceUrl;

    public SpeechTranscriptionClient(RestTemplate restTemplate,
                                     @Value("${app.translation-service-url:http://localhost:9013}") String translationServiceUrl) {
        this.restTemplate = restTemplate;
        this.translationServiceUrl = trimTrailingSlash(translationServiceUrl);
    }

    public TranscriptionResponse transcribe(Message message, String sourceLang) {
        if (message == null || !StringUtils.hasText(message.getAttachmentPath())) {
            return failure("Voice note attachment is missing");
        }

        Path attachmentPath = Paths.get(message.getAttachmentPath()).normalize();
        if (!Files.exists(attachmentPath)) {
            return failure("Voice note file is missing");
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedFileSystemResource(attachmentPath, resolveTranscriptionFilename(message, attachmentPath)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        try {
            ResponseEntity<TranscriptionResponse> response = restTemplate.exchange(
                    translationServiceUrl + "/transcribe",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    TranscriptionResponse.class
            );
            return response.getBody() == null ? failure("Speech transcription failed") : response.getBody();
        } catch (RestClientException ex) {
            return failure("Speech transcription service is unavailable");
        }
    }

    private TranscriptionResponse failure(String error) {
        return TranscriptionResponse.builder()
                .success(false)
                .error(error)
                .build();
    }

    private String trimTrailingSlash(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "http://localhost:9013";
        }

        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String resolveTranscriptionFilename(Message message, Path attachmentPath) {
        String preferredFilename = message == null ? null : message.getAttachmentName();
        if (!StringUtils.hasText(preferredFilename) && attachmentPath != null && attachmentPath.getFileName() != null) {
            preferredFilename = attachmentPath.getFileName().toString();
        }

        return VoiceNoteFilenameSupport.normalizeVoiceNoteFilename(
                preferredFilename,
                message == null ? null : message.getAttachmentContentType()
        );
    }

    private static final class NamedFileSystemResource extends FileSystemResource {

        private final String filename;

        private NamedFileSystemResource(Path path, String filename) {
            super(path);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
