package com.connecthub.paymentservice.dto;

public record AuthUserSummaryResponse(
        String userId,
        String username,
        String fullName,
        String email,
        String phoneNumber,
        String avatarUrl,
        String bio,
        String preferredLanguage,
        Integer translationCreditsRemaining,
        String onlineStatus,
        String lastSeenAt,
        String role
) {
}
