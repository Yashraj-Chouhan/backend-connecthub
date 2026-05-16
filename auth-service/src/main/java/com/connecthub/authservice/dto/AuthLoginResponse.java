package com.connecthub.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthLoginResponse {
    private String token;
    private String userId;
    private String email;
    private String phoneNumber;
    private String username;
    private String preferredLanguage;
    private Integer translationCreditsRemaining;
    private String role;
    private String avatarUrl;
}
