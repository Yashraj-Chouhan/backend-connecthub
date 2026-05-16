package com.connecthub.messageservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranslationRequest {
    @NotBlank(message = "Text is required")
    @Size(max = 4000, message = "Text cannot exceed 4000 characters")
    private String text;

    @NotBlank(message = "Target language is required")
    @Pattern(regexp = "^[a-zA-Z_-]{2,10}$", message = "Target language must be a valid ISO language code")
    private String targetLang;

    private String sourceLang = "auto";
}
