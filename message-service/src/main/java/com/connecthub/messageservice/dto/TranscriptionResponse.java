package com.connecthub.messageservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionResponse {
    private String transcript;
    private String sourceLanguage;
    private String provider;
    private boolean success;
    private String error;
}
