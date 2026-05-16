package com.connecthub.authservice.dto;

import com.connecthub.authservice.validation.StrongPassword;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class ResetPasswordRequest {

    @NotBlank(message = "Please enter the reset code.")
    private String token;

    @NotBlank(message = "Please create a new password.")
    @StrongPassword
    private String newPassword;
}
