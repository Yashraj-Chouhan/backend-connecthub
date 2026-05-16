package com.connecthub.paymentservice.dto;

public record PaymentVerificationResponse(
        boolean paymentVerified,
        boolean creditsApplied,
        String message,
        String orderId,
        String paymentId,
        String paymentStatus,
        String planCode,
        String planName,
        int creditsAdded,
        Integer remainingCredits,
        AuthUserSummaryResponse updatedUser,
        String lastError
) {
}
