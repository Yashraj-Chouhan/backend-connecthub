package com.connecthub.websocketservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {

    @JsonAlias("id")
    private Long messageId;

    @JsonAlias("senderId")
    private String sender;

    private String roomId;
    private String content;

    @JsonAlias("originalText")
    private String originalContent;

    @JsonAlias("translatedText")
    private String translatedContent;

    private LocalDateTime timestamp;
    private String messageType;
    private String attachmentName;
    private String attachmentPath;
    private String attachmentContentType;
    private Long attachmentSize;
    private Long replyToMessageId;
    private Boolean deleted;
    private LocalDateTime editedAt;
    private LocalDateTime deletedAt;

    @JsonAlias("sourceLanguage")
    private String detectedLanguage;

    private String transcript;
    private String transcriptSourceLanguage;
    private LocalDateTime transcriptUpdatedAt;
    private String targetLanguage;

    private Long translationCreditsRemaining;
    private Boolean translationLimitReached;
    private String recipientId;
    private String eventType;
    private String emoji;
    private List<String> reactions;
    private String callId;
    private String callMediaType;
    private String signalType;
    private String sdp;
    private String candidate;
    private String candidateMid;
    private Integer candidateMLineIndex;
    private String callStatus;
}
