package com.connecthub.messageservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageTranslationResponse {
    private Long messageId;
    private String messageType;
    private String originalContent;
    private String translatedContent;
    private String detectedSourceLanguage;
    private String targetLanguage;
    private Integer translationCreditsRemaining;
    private boolean upgradeRequired;
    private boolean success;
    private String error;
}
