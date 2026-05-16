package com.connecthub.translationservice.service.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(1)
@Slf4j
public class GeminiTranslationProvider implements TranslationProvider {

    private static final String PROVIDER = "gemini";
    private static final String GENERATE_CONTENT_SUFFIX = ":generateContent";
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{.*}", Pattern.DOTALL);
    private static final Map<String, String> LANGUAGE_NAMES = Map.ofEntries(
            Map.entry("en", "English"),
            Map.entry("es", "Spanish"),
            Map.entry("fr", "French"),
            Map.entry("de", "German"),
            Map.entry("hi", "Hindi"),
            Map.entry("ja", "Japanese"),
            Map.entry("pt", "Portuguese"),
            Map.entry("it", "Italian"),
            Map.entry("kn", "Kannada"),
            Map.entry("ml", "Malayalam"),
            Map.entry("ta", "Tamil"),
            Map.entry("te", "Telugu"),
            Map.entry("mr", "Marathi"),
            Map.entry("gu", "Gujarati"),
            Map.entry("bn", "Bengali"),
            Map.entry("pa", "Punjabi")
    );
    private static final String SYSTEM_INSTRUCTION = """
            You are an expert multilingual translation assistant for chat messages and live captions.
            Correct obvious spelling mistakes and typos in the source text before translating it.
            Inputs can be informal, code-mixed, transliterated, or contain multiple scripts.
            Infer the intended full meaning first, then translate the complete message naturally.
            Never do word-by-word substitution and never leave source-language words untranslated unless they are proper names, brands, URLs, or intentionally quoted text.
            Preserve the original meaning, tone, links, names, emojis, and punctuation.
            If the target language is the same as the source language, return the typo-corrected text in that language.
            Return only JSON that matches the provided schema.
            """;
    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "correctedText", Map.of(
                            "type", "string",
                            "description", "Source text after correcting obvious typos."
                    ),
                    "translatedText", Map.of(
                            "type", "string",
                            "description", "Translated text in the requested target language."
                    ),
                    "sourceLanguage", Map.of(
                            "type", "string",
                            "description", "Detected or applied source language code."
                    )
            ),
            "required", List.of("correctedText", "translatedText", "sourceLanguage")
    );

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String apiUrl;
    private final String model;
    private final String apiKey;

    public GeminiTranslationProvider(@Qualifier("translationRestTemplate") RestTemplate restTemplate,
                                     ObjectMapper objectMapper,
                                     @Value("${translation.gemini.enabled:true}") boolean enabled,
                                     @Value("${translation.gemini.url:https://generativelanguage.googleapis.com/v1beta/models}") String apiUrl,
                                     @Value("${translation.gemini.model:gemini-2.5-flash}") String model,
                                     @Value("${translation.gemini.key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.apiUrl = apiUrl;
        this.model = model;
        this.apiKey = apiKey;
    }

    @Override
    public Optional<TranslationProviderResult> translate(TranslationJob job) {
        if (!enabled || !StringUtils.hasText(apiKey) || !StringUtils.hasText(apiUrl) || !StringUtils.hasText(model)) {
            return Optional.empty();
        }

        Optional<TranslationProviderResult> initialAttempt = requestTranslation(job, buildPrompt(job, false));
        if (initialAttempt.isPresent() && !looksLowQuality(job, initialAttempt.get())) {
            return initialAttempt;
        }

        if (initialAttempt.isPresent()) {
            log.warn("Gemini produced a low-quality translation for target {}. Retrying with a stricter prompt.", job.targetLanguage());
        }

        Optional<TranslationProviderResult> strictAttempt = requestTranslation(job, buildPrompt(job, true));
        if (strictAttempt.isPresent() && !looksLowQuality(job, strictAttempt.get())) {
            return strictAttempt;
        }

        if (strictAttempt.isPresent()) {
            log.warn("Gemini strict retry still produced a low-quality translation for target {}", job.targetLanguage());
        }

        return Optional.empty();
    }

    private Optional<TranslationProviderResult> requestTranslation(TranslationJob job, String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("system_instruction", Map.of(
                "parts", List.of(Map.of("text", SYSTEM_INSTRUCTION))
        ));
        requestBody.put("contents", List.of(Map.of(
                "parts", List.of(Map.of("text", prompt))
        )));
        requestBody.put("generationConfig", Map.of(
                "responseFormat", Map.of(
                        "text", Map.of(
                                "mimeType", "application/json",
                                "schema", RESPONSE_SCHEMA
                        )
                )
        ));

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    resolveEndpoint(),
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    Map.class
            );

            Map<?, ?> responseBody = response.getBody();
            if (responseBody == null) {
                log.warn("Gemini returned an empty body");
                return Optional.empty();
            }

            String jsonPayload = extractCandidateText(responseBody);
            if (!StringUtils.hasText(jsonPayload)) {
                log.warn("Gemini returned no candidate text");
                return Optional.empty();
            }

            Map<String, Object> parsed = parseStructuredPayload(jsonPayload);
            String correctedText = valueAsString(parsed.get("correctedText"));
            String translatedText = valueAsString(parsed.get("translatedText"));
            String sourceLanguage = valueAsString(parsed.get("sourceLanguage"));

            if (!StringUtils.hasText(translatedText)) {
                log.warn("Gemini returned an empty translated text payload");
                return Optional.empty();
            }

            return Optional.of(new TranslationProviderResult(
                    StringUtils.hasText(correctedText) ? correctedText : job.originalText(),
                    translatedText,
                    StringUtils.hasText(sourceLanguage) ? sourceLanguage : job.sourceLanguage(),
                    job.targetLanguage(),
                    PROVIDER
            ));
        } catch (HttpStatusCodeException ex) {
            log.warn("Gemini returned {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("Gemini request failed: {}", ex.getMessage());
            return Optional.empty();
        } catch (IOException ex) {
            log.warn("Gemini returned invalid JSON: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private String buildPrompt(TranslationJob job, boolean strictRetry) {
        String targetLanguageCode = normalizeLanguageCode(job.targetLanguage());
        String targetLanguageName = resolveLanguageName(targetLanguageCode);
        String strictInstructions = strictRetry
                ? """
                  additional_requirements:
                  - The translatedText must be fully natural in the target language.
                  - Do not leave source-language words or source script in translatedText unless they are proper names or URLs.
                  - If the target language is English, translatedText must be fluent English only.
                  - Use the normal native script for the target language whenever applicable.
                  """
                : "";
        return """
                Translate the following chat message or live caption.
                source_language_hint: %s
                target_language_code: %s
                target_language_name: %s
                requirements:
                - Correct obvious typos in the source text before translating it.
                - If the text is code-mixed or transliterated, normalize the intended meaning before translating.
                - Translate the full utterance naturally, not word-by-word.
                - Keep the original meaning, tone, numbers, links, and names.
                - Return sourceLanguage as a lowercase ISO 639-1 language code when possible.
                %s
                text:
                %s
                """.formatted(job.sourceLanguageHint(), targetLanguageCode, targetLanguageName, strictInstructions, job.originalText());
    }

    private String resolveEndpoint() {
        String normalizedUrl = apiUrl.trim();
        if (normalizedUrl.endsWith("/")) {
            normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 1);
        }
        if (normalizedUrl.contains("{model}")) {
            normalizedUrl = normalizedUrl.replace("{model}", model);
        }
        if (normalizedUrl.endsWith(GENERATE_CONTENT_SUFFIX)) {
            return normalizedUrl;
        }
        return normalizedUrl + "/" + model + GENERATE_CONTENT_SUFFIX;
    }

    private String extractCandidateText(Map<?, ?> responseBody) {
        Object candidates = responseBody.get("candidates");
        if (!(candidates instanceof List<?> candidateList)) {
            return null;
        }

        for (Object candidate : candidateList) {
            if (!(candidate instanceof Map<?, ?> candidateMap)) {
                continue;
            }
            Object content = candidateMap.get("content");
            if (!(content instanceof Map<?, ?> contentMap)) {
                continue;
            }
            Object parts = contentMap.get("parts");
            if (!(parts instanceof List<?> partList)) {
                continue;
            }

            StringBuilder builder = new StringBuilder();
            for (Object part : partList) {
                if (part instanceof Map<?, ?> partMap) {
                    Object text = partMap.get("text");
                    if (text instanceof String textValue && StringUtils.hasText(textValue)) {
                        if (builder.length() > 0) {
                            builder.append('\n');
                        }
                        builder.append(textValue);
                    }
                }
            }

            if (builder.length() > 0) {
                return builder.toString();
            }
        }

        return null;
    }

    private Map<String, Object> parseStructuredPayload(String jsonPayload) throws IOException {
        try {
            return objectMapper.readValue(jsonPayload, new TypeReference<>() {
            });
        } catch (IOException directParseFailure) {
            String extractedJson = extractJsonObject(jsonPayload);
            if (!StringUtils.hasText(extractedJson) || Objects.equals(extractedJson, jsonPayload)) {
                throw directParseFailure;
            }
            return objectMapper.readValue(extractedJson, new TypeReference<>() {
            });
        }
    }

    private String extractJsonObject(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }

        Matcher matcher = JSON_OBJECT_PATTERN.matcher(payload);
        return matcher.find() ? matcher.group() : null;
    }

    private boolean looksLowQuality(TranslationJob job, TranslationProviderResult result) {
        String translatedText = result.translatedText();
        if (!StringUtils.hasText(translatedText)) {
            return true;
        }

        String normalizedTarget = normalizeLanguageCode(job.targetLanguage());
        String normalizedSource = normalizeLanguageCode(
                StringUtils.hasText(result.sourceLanguage()) ? result.sourceLanguage() : job.sourceLanguage()
        );

        if ("en".equals(normalizedTarget) && containsNonLatinLetters(translatedText)) {
            return true;
        }

        if (!Objects.equals(normalizedTarget, normalizedSource)
                && normalizeForComparison(job.originalText()).equals(normalizeForComparison(translatedText))) {
            return true;
        }

        return false;
    }

    private boolean containsNonLatinLetters(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }

        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);

            if (!Character.isLetter(codePoint)) {
                continue;
            }

            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script != Character.UnicodeScript.LATIN
                    && script != Character.UnicodeScript.COMMON
                    && script != Character.UnicodeScript.INHERITED) {
                return true;
            }
        }

        return false;
    }

    private String normalizeLanguageCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }

        String normalized = code.trim().toLowerCase();
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

    private String resolveLanguageName(String code) {
        if (!StringUtils.hasText(code)) {
            return "Unknown";
        }
        return LANGUAGE_NAMES.getOrDefault(code, code);
    }

    private String normalizeForComparison(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }

        return text.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private String valueAsString(Object value) {
        return value instanceof String text ? text : null;
    }
}
