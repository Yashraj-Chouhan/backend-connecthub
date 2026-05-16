package com.connecthub.paymentservice.service;

import com.connecthub.paymentservice.entity.Payment;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReceiptEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private PaymentReceiptPdfService paymentReceiptPdfService;

    private PaymentReceiptEmailService service;

    @BeforeEach
    void setUp() {
        service = new PaymentReceiptEmailService(mailSender, paymentReceiptPdfService);
    }

    @Test
    void sendReceiptSkipsWhenEmailOrSenderMissing() {
        Payment payment = Payment.builder().orderId("order-1").build();
        ReflectionTestUtils.setField(service, "fromEmail", "");

        service.sendReceipt(payment);

        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void sendReceiptBuildsAndSendsMimeMessage() {
        Payment payment = Payment.builder()
                .orderId("order-1")
                .receipt("rcpt-1")
                .customerName("Test User")
                .customerEmail("user@example.com")
                .paymentId("pay-1")
                .credits(150)
                .currency("INR")
                .amount(249.0)
                .build();
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@connecthub.local");
        when(paymentReceiptPdfService.generateReceiptPdf(payment)).thenReturn("pdf".getBytes());
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service.sendReceipt(payment);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendReceiptWrapsUnexpectedFailures() {
        Payment payment = Payment.builder()
                .orderId("order-1")
                .customerEmail("user@example.com")
                .build();
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@connecthub.local");
        when(paymentReceiptPdfService.generateReceiptPdf(payment)).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service.sendReceipt(payment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to send payment receipt email");
    }
}
