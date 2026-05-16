package com.connecthub.paymentservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentPlanCatalogTest {

    private PaymentPlanCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new PaymentPlanCatalog();
        ReflectionTestUtils.setField(catalog, "customCreditRate", new BigDecimal("2.50"));
        ReflectionTestUtils.setField(catalog, "minCredits", 25);
        ReflectionTestUtils.setField(catalog, "maxCredits", 5000);
        ReflectionTestUtils.setField(catalog, "merchantName", "ConnectHub Credits");
        ReflectionTestUtils.setField(catalog, "supportMessage", "support");
    }

    @Test
    void buildConfigAndLookupMethodsReturnExpectedValues() {
        assertThat(catalog.getPlans()).hasSize(4);
        assertThat(catalog.buildConfig("razorpay", "rzp_test").merchantName()).isEqualTo("ConnectHub Credits");
        assertThat(catalog.requirePlan("BOOST").code()).isEqualTo("boost");
        assertThat(catalog.findPlan(" power ")).isPresent();
        assertThat(catalog.findByCredits(150)).isPresent();
        assertThat(catalog.findByAmount(new BigDecimal("249.00"))).isPresent();
        assertThat(catalog.getCustomCreditRate()).isEqualByComparingTo("2.50");
    }

    @Test
    void customPlanValidationAndAmountsWork() {
        assertThat(catalog.buildCustomPlan(40).amount()).isEqualByComparingTo("100.00");
        assertThatThrownBy(() -> catalog.requirePlan(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plan code is required");
        assertThatThrownBy(() -> catalog.buildCustomPlan(10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 25 and 5000");
        assertThat(catalog.findByAmount(null)).isEmpty();
        assertThat(catalog.findPlan(null)).isEmpty();
    }
}
