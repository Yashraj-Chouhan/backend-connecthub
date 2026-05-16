package com.connecthub.paymentservice.dto;

import java.math.BigDecimal;

public record CreateOrderResponse(
        String orderId,
        String provider,
        String keyId,
        String approvalUrl,
        String currency,
        BigDecimal amount,
        long amountInPaise,
        int credits,
        String planCode,
        String planName,
        String receipt,
        String userId,
        String message
) {
}
