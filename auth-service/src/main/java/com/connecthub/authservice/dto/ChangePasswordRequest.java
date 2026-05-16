package com.connecthub.authservice.dto;

import com.connecthub.authservice.validation.StrongPassword;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class ChangePasswordRequest {

    @NotBlank(message = "Please enter your current password.")
    private String currentPassword;

    @NotBlank(message = "Please create a new password.")
    @StrongPassword
    private String newPassword;
}
