package com.connecthub.paymentservice.dto;

import java.math.BigDecimal;

public record TopUpPlanResponse(
        String code,
        String title,
        String description,
        int credits,
        BigDecimal amount,
        String badge,
        boolean recommended
) {
}
