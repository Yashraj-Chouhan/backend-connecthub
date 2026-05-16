package com.connecthub.authservice.dto;

import com.connecthub.authservice.validation.ValidEmailOrPhone;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
public class VerifyOtpRequest {

    @NotBlank(message = "Please enter your email address or mobile number.")
    @ValidEmailOrPhone
    private String identifier; // email or phone number

    @NotBlank(message = "Please enter the verification code.")
    @Pattern(regexp = "^\\d{6}$", message = "Please enter the 6-digit verification code.")
    private String otp;
}
