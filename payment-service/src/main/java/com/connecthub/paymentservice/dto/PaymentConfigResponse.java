package com.connecthub.paymentservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record PaymentConfigResponse(
        String merchantName,
        String currency,
        String provider,
        String keyId,
        BigDecimal customCreditRate,
        int minCredits,
        int maxCredits,
        String supportMessage,
        List<TopUpPlanResponse> plans
) {
}
