package com.connecthub.translationservice.service;

import com.connecthub.translationservice.dto.TranscriptionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class SpeechTranscriptionService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "flac", "mp3", "mp4", "mpeg", "mpga", "m4a", "ogg", "wav", "webm"
    );

    private final RestTemplate speechRestTemplate;
    private final String provider;
    private final String apiUrl;
    private final String apiKey;
    private final String model;

    public SpeechTranscriptionService(@Qualifier("speechRestTemplate") RestTemplate speechRestTemplate,
                                      @Value("${speech.api.provider:groq-speech-to-text}") String provider,
                                      @Value("${speech.api.url}") String apiUrl,
                                      @Value("${speech.api.key:}") String apiKey,
                                      @Value("${speech.api.model:whisper-large-v3-turbo}") String model) {
        this.speechRestTemplate = speechRestTemplate;
        this.provider = provider;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    public TranscriptionResponse transcribe(MultipartFile file, String sourceLang) {
        if (file == null || file.isEmpty()) {
            return failure("Audio file is required");
        }

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "voice-note.webm" : file.getOriginalFilename()
        );
        if (!hasSupportedExtension(originalFilename)) {
            return failure("Unsupported audio format. Supported formats: flac, mp3, mp4, mpeg, mpga, m4a, ogg, wav, webm");
        }

        if (!StringUtils.hasText(apiKey)) {
            return failure("Speech transcription provider is not configured");
        }

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new NamedByteArrayResource(file.getBytes(), originalFilename));
            body.add("model", model);
            body.add("response_format", "json");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ResponseEntity<Map> response = speechRestTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            Map<?, ?> responseBody = response.getBody();
            String transcript = valueAsString(responseBody == null ? null : responseBody.get("text"));
            if (!StringUtils.hasText(transcript)) {
                return failure("Speech transcription provider returned an empty transcript");
            }

            return TranscriptionResponse.builder()
                    .transcript(transcript.trim())
                    .sourceLanguage(firstNonBlank(
                            normalizeLanguageCode(valueAsString(responseBody == null ? null : responseBody.get("language"))),
                            "auto"
                    ))
                    .provider(provider)
                    .success(true)
                    .build();
        } catch (HttpStatusCodeException ex) {
            log.warn("Speech transcription API returned {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return failure("Speech transcription request was rejected");
        } catch (RestClientException ex) {
            log.error("Speech transcription API request failed: {}", ex.getMessage());
            return failure("Speech transcription provider is unavailable");
        } catch (IOException ex) {
            log.error("Could not read uploaded voice note for transcription", ex);
            return failure("Could not read uploaded voice note");
        }
    }

    private boolean hasSupportedExtension(String filename) {
        int extensionIndex = filename.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == filename.length() - 1) {
            return false;
        }

        String extension = filename.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.contains(extension);
    }

    private String normalizeLanguageCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }

        String normalized = code.trim().toLowerCase(Locale.ROOT);
        if ("auto".equals(normalized)) {
            return "auto";
        }

        int hyphenIndex = normalized.indexOf('-');
        if (hyphenIndex > 0) {
            return normalized.substring(0, hyphenIndex);
        }

        int underscoreIndex = normalized.indexOf('_');
        if (underscoreIndex > 0) {
            return normalized.substring(0, underscoreIndex);
        }

        return normalized;
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }

        return null;
    }

    private TranscriptionResponse failure(String error) {
        return TranscriptionResponse.builder()
                .provider(provider)
                .success(false)
                .error(error)
                .build();
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
