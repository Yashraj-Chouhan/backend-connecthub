package com.connecthub.authservice.dto;

import com.connecthub.authservice.validation.StrongPassword;
import com.connecthub.authservice.validation.ValidEmail;
import com.connecthub.authservice.validation.ValidPhoneNumber;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class InitiateRegistrationRequest {

    @NotBlank(message = "Please enter your email address.")
    @ValidEmail
    private String email;

    @NotBlank(message = "Please enter your mobile number.")
    @ValidPhoneNumber
    private String phoneNumber;

    @NotBlank(message = "Please create a password.")
    @StrongPassword
    private String password;

    @NotBlank(message = "Please choose a username.")
    private String username;
}
