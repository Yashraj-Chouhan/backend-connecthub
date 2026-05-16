package com.connecthub.paymentservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentHistoryResponse(
        String orderId,
        String paymentId,
        String planCode,
        String planName,
        Integer credits,
        BigDecimal amount,
        String currency,
        String status,
        String userId,
        LocalDateTime createdAt,
        LocalDateTime verifiedAt,
        LocalDateTime creditedAt,
        String lastError
) {
}
