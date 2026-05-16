package com.connecthub.messageservice.controller;

import com.connecthub.messageservice.client.SpeechTranscriptionClient;
import com.connecthub.messageservice.dto.MessageRequest;
import com.connecthub.messageservice.dto.EditMessageRequest;
import com.connecthub.messageservice.dto.MessageTranslationResponse;
import com.connecthub.messageservice.dto.ReactionRequest;
import com.connecthub.messageservice.dto.TranscriptionResponse;
import com.connecthub.messageservice.dto.TranslationRequest;
import com.connecthub.messageservice.dto.TranslationResponse;
import com.connecthub.messageservice.dto.UserSummaryResponse;
import com.connecthub.messageservice.entity.Message;
import com.connecthub.messageservice.entity.MessageReaction;
import com.connecthub.messageservice.client.AuthClient;
import com.connecthub.messageservice.client.TranslationClient;
import com.connecthub.messageservice.service.MessageService;
import com.connecthub.messageservice.service.TranscriptEventPublisher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import feign.FeignException;

@RestController
/**
 * Handles message CRUD, attachments, reactions, search, and on-demand message
 * translation for room conversations.
 */
@RequestMapping("/messages")
@Validated
@RequiredArgsConstructor
public class MessageController {

    private static final Map<String, String> LANGUAGE_ALIASES = Map.ofEntries(
            Map.entry("english", "en"),
            Map.entry("spanish", "es"),
            Map.entry("french", "fr"),
            Map.entry("german", "de"),
            Map.entry("hindi", "hi"),
            Map.entry("\u0939\u093f\u0902\u0926\u0940", "hi"),
            Map.entry("japanese", "ja"),
            Map.entry("portuguese", "pt"),
            Map.entry("italian", "it"),
            Map.entry("kannada", "kn"),
            Map.entry("malayalam", "ml"),
            Map.entry("tamil", "ta"),
            Map.entry("telugu", "te"),
            Map.entry("marathi", "mr"),
            Map.entry("gujarati", "gu"),
            Map.entry("bengali", "bn"),
            Map.entry("punjabi", "pa")
    );

    private final MessageService service;
    private final TranslationClient translationClient;
    private final AuthClient authClient;
    private final SpeechTranscriptionClient speechTranscriptionClient;
    private final TranscriptEventPublisher transcriptEventPublisher;

    @Value("${app.auth-service-topup-secret:connecthub-topup-secret-change-me}")
    private String authServiceTopupSecret;

    @PostMapping
    public Message save(@Valid @RequestBody MessageRequest request) {
        return service.saveMessage(request);
    }

    @GetMapping("/{roomId}")
    public List<Message> getMessages(@PathVariable @NotBlank(message = "Room ID is required") String roomId) {
        return service.getMessages(roomId);
    }

    @GetMapping("/{roomId}/history")
    public Page<Message> getHistory(@PathVariable @NotBlank(message = "Room ID is required") String roomId,
                                    @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must be 0 or greater") int page,
                                    @RequestParam(defaultValue = "20") @Min(value = 1, message = "Size must be at least 1") int size) {
        return service.getMessageHistory(roomId, page, size);
    }

    @GetMapping("/{roomId}/search")
    public Page<Message> search(@PathVariable @NotBlank(message = "Room ID is required") String roomId,
                                @RequestParam @NotBlank(message = "Query is required") String query,
                                @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must be 0 or greater") int page,
                                @RequestParam(defaultValue = "20") @Min(value = 1, message = "Size must be at least 1") int size) {
        return service.searchMessages(roomId, query, page, size);
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Message uploadAttachment(@RequestParam @NotBlank(message = "Sender is required") String sender,
                                    @RequestParam @NotBlank(message = "Room ID is required") String roomId,
                                    @RequestParam(required = false) String messageType,
                                    @RequestParam(required = false) String content,
                                    @RequestParam(required = false) String transcript,
                                    @RequestParam(required = false) String transcriptSourceLanguage,
                                    @RequestPart("file") MultipartFile file) {
        return service.saveAttachmentMessage(
                sender,
                roomId,
                content,
                messageType,
                transcript,
                transcriptSourceLanguage,
                file
        );
    }

    @GetMapping("/{roomId}/attachments/{messageId}")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable @NotBlank(message = "Room ID is required") String roomId,
                                                       @PathVariable Long messageId) {
        Message message = service.getAttachmentMetadata(messageId, roomId);
        Resource resource = service.getAttachment(messageId, roomId);
        String encodedFilename = URLEncoder.encode(message.getAttachmentName(), StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(message.getAttachmentContentType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(message.getAttachmentContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFilename + "\"")
                .body(resource);
    }

    @GetMapping("/{roomId}/attachments/{messageId}/metadata")
    public Message getAttachmentMetadata(@PathVariable @NotBlank(message = "Room ID is required") String roomId,
                                         @PathVariable Long messageId) {
        return service.getAttachmentMetadata(messageId, roomId);
    }

    @PutMapping("/{roomId}/{messageId}")
    public Message editMessage(@PathVariable @NotBlank(message = "Room ID is required") String roomId,
                               @PathVariable Long messageId,
                               @Valid @RequestBody EditMessageRequest request) {
        return service.editMessage(messageId, roomId, request);
    }

    @DeleteMapping("/{roomId}/{messageId}")
    public Message deleteMessage(@PathVariable @NotBlank(message = "Room ID is required") String roomId,
                                 @PathVariable Long messageId) {
        return service.deleteMessage(messageId, roomId);
    }

    @PostMapping("/{roomId}/{messageId}/reactions")
    public MessageReaction react(@PathVariable @NotBlank(message = "Room ID is required") String roomId,
                                 @PathVariable Long messageId,
                                 @Valid @RequestBody ReactionRequest request) {
        return service.addReaction(messageId, roomId, request);
    }

    /**
     * Translate a message's content into the requester's preferred language.
     * GET /messages/{roomId}/{messageId}/translate?targetLang=hi
     */
    @GetMapping("/{roomId}/{messageId}/translate")
    public ResponseEntity<MessageTranslationResponse> translateMessage(
            @PathVariable @NotBlank(message = "Room ID is required") String roomId,
            @PathVariable Long messageId,
            @RequestParam(required = false) String targetLang,
            @RequestParam @NotBlank(message = "User ID is required") String userId) {

        Message message = service.getMessage(messageId, roomId);
        String resolvedTargetLang = null;

        if (message == null) {
            return ResponseEntity.ok(MessageTranslationResponse.builder()
                    .messageId(messageId)
                    .messageType(null)
                    .success(false)
                    .error("Message not found")
                    .build());
        }

        if (!service.isTranslatable(message)) {
            return ResponseEntity.ok(MessageTranslationResponse.builder()
                    .messageId(messageId)
                    .messageType(message.getMessageType())
                    .success(false)
                    .error("Deleted messages cannot be translated")
                    .build());
        }

        boolean creditConsumed = false;
        UserSummaryResponse quota = null;

        try {
            quota = authClient.consumeTranslationCredit(userId);
            creditConsumed = true;
            resolvedTargetLang = resolveTargetLanguage(targetLang, quota);
            TranslationContext translationContext = resolveTranslationContext(message, roomId);
            if (!translationContext.success()) {
                UserSummaryResponse refundedQuota = refundTranslationCredit(userId);
                return ResponseEntity.ok(MessageTranslationResponse.builder()
                        .messageId(messageId)
                        .messageType(message.getMessageType())
                        .originalContent(translationContext.originalContent())
                        .targetLanguage(resolvedTargetLang)
                        .translationCreditsRemaining(refundedQuota == null
                                ? quota.getTranslationCreditsRemaining()
                                : refundedQuota.getTranslationCreditsRemaining())
                        .upgradeRequired(false)
                        .success(false)
                        .error(translationContext.error())
                        .build());
            }

            TranslationResponse translationResult = translationClient.translate(
                    new TranslationRequest(
                            translationContext.originalContent(),
                            resolvedTargetLang,
                            translationContext.sourceLanguage()
                    ));

            if (!translationResult.isSuccess()) {
                UserSummaryResponse refundedQuota = refundTranslationCredit(userId);
                return ResponseEntity.ok(MessageTranslationResponse.builder()
                        .messageId(messageId)
                        .messageType(message.getMessageType())
                        .originalContent(translationContext.originalContent())
                        .targetLanguage(resolvedTargetLang)
                        .translationCreditsRemaining(refundedQuota == null
                                ? quota.getTranslationCreditsRemaining()
                                : refundedQuota.getTranslationCreditsRemaining())
                        .upgradeRequired(false)
                        .success(false)
                        .error(translationResult.getError() == null
                                ? "Translation failed"
                                : translationResult.getError())
                        .build());
            }

            return ResponseEntity.ok(MessageTranslationResponse.builder()
                    .messageId(messageId)
                    .messageType(message.getMessageType())
                    .originalContent(translationContext.originalContent())
                    .translatedContent(translationResult.getTranslatedText())
                    .detectedSourceLanguage(firstNonBlank(
                            translationResult.getSourceLanguage(),
                            translationContext.sourceLanguage()
                    ))
                    .targetLanguage(translationResult.getTargetLanguage())
                    .translationCreditsRemaining(quota.getTranslationCreditsRemaining())
                    .upgradeRequired(false)
                    .success(true)
                    .build());
        } catch (FeignException feignException) {
            if (creditConsumed) {
                quota = refundTranslationCredit(userId);
            }
            if (feignException.status() == 402 || feignException.status() == 429) {
                return ResponseEntity.ok(MessageTranslationResponse.builder()
                        .messageId(messageId)
                        .messageType(message.getMessageType())
                        .originalContent(message.getTranscript() == null ? message.getContent() : message.getTranscript())
                        .targetLanguage(firstNonBlank(resolvedTargetLang, resolveTargetLanguage(targetLang, quota)))
                        .translationCreditsRemaining(0)
                        .upgradeRequired(true)
                        .success(false)
                        .error("Translation limit reached. Add more credits to continue.")
                        .build());
            }

            String error = feignException.status() >= 500
                    ? "Translation service unavailable"
                    : "Translation request was rejected";

            return ResponseEntity.ok(MessageTranslationResponse.builder()
                    .messageId(messageId)
                    .messageType(message.getMessageType())
                    .originalContent(message.getTranscript() == null ? message.getContent() : message.getTranscript())
                    .targetLanguage(firstNonBlank(resolvedTargetLang, resolveTargetLanguage(targetLang, quota)))
                    .translationCreditsRemaining(quota == null ? null : quota.getTranslationCreditsRemaining())
                    .upgradeRequired(false)
                    .success(false)
                    .error(error)
                    .build());
        } catch (Exception e) {
            if (creditConsumed) {
                quota = refundTranslationCredit(userId);
            }
            return ResponseEntity.ok(MessageTranslationResponse.builder()
                    .messageId(messageId)
                    .messageType(message.getMessageType())
                    .originalContent(message.getTranscript() == null ? message.getContent() : message.getTranscript())
                    .translationCreditsRemaining(quota == null ? null : quota.getTranslationCreditsRemaining())
                    .upgradeRequired(false)
                    .success(false)
                    .error("Translation service unavailable: " + e.getMessage())
                    .build());
        }
    }

    private UserSummaryResponse refundTranslationCredit(String userId) {
        try {
            return authClient.topUpTranslationCredits(userId, authServiceTopupSecret, 1);
        } catch (Exception ignored) {
            // If the refund fails, the user can top up manually later.
            return null;
        }
    }

    private String resolveTargetLanguage(String requestedTargetLang, UserSummaryResponse quota) {
        if (StringUtils.hasText(requestedTargetLang)) {
            return normalizeTargetLanguage(requestedTargetLang.trim());
        }

        if (quota != null && StringUtils.hasText(quota.getPreferredLanguage())) {
            return normalizeTargetLanguage(quota.getPreferredLanguage().trim());
        }

        return "hi";
    }

    private String normalizeTargetLanguage(String language) {
        if (!StringUtils.hasText(language)) {
            return "hi";
        }

        String normalized = language.trim().toLowerCase(Locale.ROOT);
        String alias = LANGUAGE_ALIASES.get(normalized);
        if (alias != null) {
            return alias;
        }

        int hyphenIndex = normalized.indexOf('-');
        if (hyphenIndex > 0) {
            String baseCode = normalized.substring(0, hyphenIndex);
            alias = LANGUAGE_ALIASES.get(baseCode);
            return alias != null ? alias : baseCode;
        }

        int underscoreIndex = normalized.indexOf('_');
        if (underscoreIndex > 0) {
            String baseCode = normalized.substring(0, underscoreIndex);
            alias = LANGUAGE_ALIASES.get(baseCode);
            return alias != null ? alias : baseCode;
        }

        return normalized;
    }

    private TranslationContext resolveTranslationContext(Message message, String roomId) {
        if (service.isVoiceNote(message)) {
            String existingTranscript = trimToNull(message.getTranscript());
            if (existingTranscript != null) {
                return TranslationContext.success(existingTranscript, "auto");
            }

            TranscriptionResponse transcriptionResponse = speechTranscriptionClient.transcribe(
                    message,
                    firstNonBlank(message.getTranscriptSourceLanguage(), "auto")
            );
            if (transcriptionResponse == null || !transcriptionResponse.isSuccess()) {
                return TranslationContext.failure(
                        trimToNull(message.getContent()),
                        transcriptionResponse == null || transcriptionResponse.getError() == null
                                ? "Voice note transcription failed"
                                : transcriptionResponse.getError()
                );
            }

            Message updatedMessage = service.updateTranscript(
                    message.getId(),
                    roomId,
                    transcriptionResponse.getTranscript(),
                    transcriptionResponse.getSourceLanguage()
            );
            transcriptEventPublisher.publish(updatedMessage);

            return TranslationContext.success(
                    updatedMessage.getTranscript(),
                    "auto"
            );
        }

        String content = trimToNull(message.getContent());
        if (content == null) {
            return TranslationContext.failure(null, "Message not found or has no translatable content");
        }

        return TranslationContext.success(content, firstNonBlank(message.getDetectedLanguage(), "auto"));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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

    private record TranslationContext(String originalContent, String sourceLanguage, String error, boolean success) {

        private static TranslationContext success(String originalContent, String sourceLanguage) {
            return new TranslationContext(originalContent, sourceLanguage, null, true);
        }

        private static TranslationContext failure(String originalContent, String error) {
            return new TranslationContext(originalContent, null, error, false);
        }
    }
}
