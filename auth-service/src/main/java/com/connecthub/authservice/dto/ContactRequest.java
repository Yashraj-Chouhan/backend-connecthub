package com.connecthub.authservice.dto;

import com.connecthub.authservice.validation.ValidEmail;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class ContactRequest {

    @NotBlank(message = "Please enter an email address.")
    @ValidEmail
    private String email;

    private String nickname;
}
