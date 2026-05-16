package com.connecthub.messageservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EditMessageRequest {
    @NotBlank(message = "Content is required")
    private String content;
}
