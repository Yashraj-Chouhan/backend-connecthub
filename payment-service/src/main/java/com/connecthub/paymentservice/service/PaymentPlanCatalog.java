package com.connecthub.paymentservice.service;

import com.connecthub.paymentservice.dto.PaymentConfigResponse;
import com.connecthub.paymentservice.dto.TopUpPlanResponse;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
/**
 * Central definition of the purchasable translation-credit bundles and the
 * rules used to build a custom top-up plan.
 */
public class PaymentPlanCatalog {

    private static final List<TopUpPlanResponse> PLANS = List.of(
            new TopUpPlanResponse(
                    "spark",
                    "Starter Spark",
                    "A quick top-up for casual chatters.",
                    50,
                    new BigDecimal("99.00"),
                    "Great for trying it out",
                    false
            ),
            new TopUpPlanResponse(
                    "boost",
                    "Daily Boost",
                    "Balanced credits for regular message translation.",
                    150,
                    new BigDecimal("249.00"),
                    "Most popular",
                    true
            ),
            new TopUpPlanResponse(
                    "power",
                    "Power Pack",
                    "A stronger bundle for active users and busy rooms.",
                    400,
                    new BigDecimal("599.00"),
                    "Best value",
                    false
            ),
            new TopUpPlanResponse(
                    "elite",
                    "Elite Wallet",
                    "Large top-up for heavy usage and teams.",
                    1000,
                    new BigDecimal("1199.00"),
                    "Big saver",
                    false
            )
    );

    @Value("${app.topup.custom-credit-rate:2.50}")
    private BigDecimal customCreditRate;

    @Value("${app.topup.min-credits:25}")
    @Getter
    private int minCredits;

    @Value("${app.topup.max-credits:5000}")
    @Getter
    private int maxCredits;

    @Value("${app.topup.merchant-name:ConnectHub Credits}")
    private String merchantName;

    @Value("${app.topup.support-message:Choose a bundle or pick your own credits. Razorpay handles the checkout and the payment service verifies the signature before syncing credits to your ConnectHub account.}")
    private String supportMessage;

    public List<TopUpPlanResponse> getPlans() {
        return PLANS;
    }

    public PaymentConfigResponse buildConfig(String provider, String keyId) {
        return new PaymentConfigResponse(
                merchantName,
                "INR",
                provider,
                keyId,
                customCreditRate.setScale(2, RoundingMode.HALF_UP),
                minCredits,
                maxCredits,
                supportMessage,
                PLANS
        );
    }

    public TopUpPlanResponse requirePlan(String code) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("Plan code is required");
        }

        return findPlan(code).orElseThrow(() ->
                new IllegalArgumentException("Unknown top-up plan: " + code));
    }

    public Optional<TopUpPlanResponse> findPlan(String code) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }

        String normalizedCode = code.trim().toLowerCase(Locale.ROOT);
        return PLANS.stream()
                .filter(plan -> plan.code().equalsIgnoreCase(normalizedCode))
                .findFirst();
    }

    public Optional<TopUpPlanResponse> findByCredits(int credits) {
        return PLANS.stream()
                .filter(plan -> plan.credits() == credits)
                .findFirst();
    }

    public Optional<TopUpPlanResponse> findByAmount(BigDecimal amount) {
        if (amount == null) {
            return Optional.empty();
        }

        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        return PLANS.stream()
                .filter(plan -> plan.amount().setScale(2, RoundingMode.HALF_UP).compareTo(normalized) == 0)
                .findFirst();
    }

    public TopUpPlanResponse buildCustomPlan(int credits) {
        if (credits < minCredits || credits > maxCredits) {
            throw new IllegalArgumentException("Credits must be between " + minCredits + " and " + maxCredits);
        }

        BigDecimal amount = customCreditRate
                .multiply(BigDecimal.valueOf(credits))
                .setScale(2, RoundingMode.HALF_UP);

        return new TopUpPlanResponse(
                "custom",
                "Custom Top-up",
                "Pick the exact number of credits you want.",
                credits,
                amount,
                "Flexible",
                false
        );
    }

    public BigDecimal getCustomCreditRate() {
        return customCreditRate.setScale(2, RoundingMode.HALF_UP);
    }
}
