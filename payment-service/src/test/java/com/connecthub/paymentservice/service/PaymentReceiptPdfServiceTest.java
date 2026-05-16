package com.connecthub.paymentservice.service;

import com.connecthub.paymentservice.entity.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentReceiptPdfServiceTest {

    private PaymentReceiptPdfService service;

    @BeforeEach
    void setUp() {
        service = new PaymentReceiptPdfService();
        ReflectionTestUtils.setField(service, "companyName", "ConnectHub");
        ReflectionTestUtils.setField(service, "supportEmail", "support@connecthub.local");
    }

    @Test
    void generateReceiptPdfProducesDocumentBytes() {
        Payment payment = Payment.builder()
                .receipt("rcpt-1")
                .orderId("order-1")
                .paymentId("pay-1")
                .customerName("Test User")
                .customerEmail("user@example.com")
                .planName("Boost")
                .credits(150)
                .amount(249.0)
                .currency("INR")
                .status("PAID")
                .verifiedAt(LocalDateTime.now())
                .build();

        byte[] pdf = service.generateReceiptPdf(payment);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    }
}
