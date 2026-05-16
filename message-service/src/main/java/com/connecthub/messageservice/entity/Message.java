package com.connecthub.messageservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sender;

    private String content;

    private String roomId;

    private LocalDateTime timestamp;

    private String messageType;

    private String attachmentName;

    private String attachmentPath;

    private String attachmentContentType;

    private Long attachmentSize;

    private Long replyToMessageId;

    private boolean deleted;

    private LocalDateTime editedAt;

    private LocalDateTime deletedAt;

    // Translation support
    private String detectedLanguage; // ISO code auto-detected when message was first translated, e.g. "hi"

    private String transcript;

    private String transcriptSourceLanguage;

    private LocalDateTime transcriptUpdatedAt;
}
