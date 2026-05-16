package com.connecthub.messageservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryResponse {
    private String userId;
    private String username;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String avatarUrl;
    private String bio;
    private String preferredLanguage;
    private Integer translationCreditsRemaining;
    private String onlineStatus;
    private String lastSeenAt;
    private String role;
}
