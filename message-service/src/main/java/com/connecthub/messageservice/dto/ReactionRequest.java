package com.connecthub.messageservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReactionRequest {
    @NotBlank(message = "User ID is required")
    private String userId;

    @NotBlank(message = "Reaction emoji is required")
    @Size(max = 16, message = "Reaction emoji is too long")
    private String emoji;
}
