package com.connecthub.authservice.dto;

import com.connecthub.authservice.validation.ValidEmail;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
public class CompleteRegistrationRequest {

    @NotBlank(message = "Please enter your email address.")
    @ValidEmail
    private String email;

    @NotBlank(message = "Please enter the 6-digit OTP sent to your email.")
    @Pattern(regexp = "^\\d{6}$", message = "Please enter the 6-digit verification code.")
    private String otp;
}
