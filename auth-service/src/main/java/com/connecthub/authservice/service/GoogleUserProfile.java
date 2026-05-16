package com.connecthub.authservice.service;

public record GoogleUserProfile(
        String subject,
        String email,
        String fullName,
        String pictureUrl
) {
}
