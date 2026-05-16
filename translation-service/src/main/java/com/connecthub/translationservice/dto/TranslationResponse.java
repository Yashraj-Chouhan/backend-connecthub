package com.connecthub.translationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranslationResponse {
    private String originalText;
    private String correctedText;
    private String translatedText;
    private String sourceLanguage;
    private String targetLanguage;
    private String provider;
    private boolean success;
    private String error;
}
