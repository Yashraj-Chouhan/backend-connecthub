package com.connecthub.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @NotBlank(message = "Display name cannot be empty")
    @Size(max = 50, message = "Display name is too long")
    private String fullName;

    @NotBlank(message = "Username cannot be empty")
    @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
    private String username;

    private String avatarUrl;

    @Size(max = 250, message = "Bio cannot exceed 250 characters")
    private String bio;

    private String preferredLanguage;
    private String onlineStatus;
}
