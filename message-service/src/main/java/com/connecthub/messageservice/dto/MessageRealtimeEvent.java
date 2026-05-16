package com.connecthub.messageservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRealtimeEvent {
    private Long messageId;
    private String sender;
    private String roomId;
    private String content;
    private String originalContent;
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
    private String detectedLanguage;
    private String transcript;
    private String transcriptSourceLanguage;
    private LocalDateTime transcriptUpdatedAt;
    private String eventType;
}
