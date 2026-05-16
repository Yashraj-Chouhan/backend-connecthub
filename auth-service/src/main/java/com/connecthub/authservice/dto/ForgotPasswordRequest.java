package com.connecthub.authservice.dto;

import com.connecthub.authservice.validation.ValidEmailOrPhone;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class ForgotPasswordRequest {

    @NotBlank(message = "Please enter your email address or mobile number.")
    @ValidEmailOrPhone
    private String identifier;
}
