package com.connecthub.paymentservice.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignatureUtilTest {

    @Test
    void generateSignatureIsDeterministicAndHexEncoded() {
        String signature = SignatureUtil.generateSignature("order-1|payment-1", "secret-123");

        assertThat(signature)
                .hasSize(64)
                .matches("^[0-9a-f]+$");
        assertThat(signature).isEqualTo(SignatureUtil.generateSignature("order-1|payment-1", "secret-123"));
    }

    @Test
    void verifySignatureMatchesExpectedPayloadAndRejectsNullInputs() {
        String signature = SignatureUtil.generateSignature("order-1|payment-1", "secret-123");

        assertThat(SignatureUtil.verifySignature("order-1", "payment-1", signature, "secret-123")).isTrue();
        assertThat(SignatureUtil.verifySignature("order-1", "payment-2", signature, "secret-123")).isFalse();
        assertThat(SignatureUtil.verifySignature(null, "payment-1", signature, "secret-123")).isFalse();
        assertThat(SignatureUtil.verifySignature("order-1", null, signature, "secret-123")).isFalse();
        assertThat(SignatureUtil.verifySignature("order-1", "payment-1", null, "secret-123")).isFalse();
        assertThat(SignatureUtil.verifySignature("order-1", "payment-1", signature, null)).isFalse();
    }
}
