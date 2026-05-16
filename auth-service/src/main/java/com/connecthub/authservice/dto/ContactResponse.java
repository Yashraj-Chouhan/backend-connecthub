package com.connecthub.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ContactResponse {
    private String contactId;
    private String ownerUserId;
    private String contactEmail;
    private String nickname;
    private String contactUserId;
    private String username;
    private String fullName;
    private String avatarUrl;
    private String bio;
    private String preferredLanguage;
    private String onlineStatus;
    private String lastSeenAt;
    private boolean registered;
}
