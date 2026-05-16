package com.connecthub.paymentservice.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTest {

    @Test
    void lifecycleCallbacksPopulateDefaults() {
        Payment payment = Payment.builder().build();

        payment.onCreate();

        assertThat(payment.getCreatedAt()).isNotNull();
        assertThat(payment.getUpdatedAt()).isNotNull();
        assertThat(payment.getStatus()).isEqualTo("CREATED");
        assertThat(payment.getCurrency()).isEqualTo("INR");

        payment.onUpdate();

        assertThat(payment.getUpdatedAt()).isNotNull();
    }
}
