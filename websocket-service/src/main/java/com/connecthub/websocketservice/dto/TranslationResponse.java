package com.connecthub.websocketservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
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
