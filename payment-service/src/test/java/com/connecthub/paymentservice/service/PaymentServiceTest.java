package com.connecthub.paymentservice.service;

import com.connecthub.paymentservice.dto.CreateOrderRequest;
import com.connecthub.paymentservice.dto.PaymentVerificationRequest;
import com.connecthub.paymentservice.dto.TopUpPlanResponse;
import com.connecthub.paymentservice.entity.Payment;
import com.connecthub.paymentservice.repository.PaymentRepository;
import com.connecthub.paymentservice.util.SignatureUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentPlanCatalog paymentPlanCatalog;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private PaymentReceiptEmailService paymentReceiptEmailService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository,
                restTemplate,
                paymentPlanCatalog,
                kafkaTemplate,
                paymentReceiptEmailService
        );

        ReflectionTestUtils.setField(paymentService, "razorpayKeyId", "rzp_test_123");
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", "secret_123");
        ReflectionTestUtils.setField(paymentService, "authServiceBaseUrl", "http://localhost:9002");
        ReflectionTestUtils.setField(paymentService, "authServiceTopupSecret", "topup-secret");
    }

    @Test
    void createOrderCreatesRazorpayOrderAndPersistsPayment() {
        TopUpPlanResponse plan = new TopUpPlanResponse(
                "boost",
                "Daily Boost",
                "Balanced credits for regular message translation.",
                150,
                new BigDecimal("249.00"),
                "Most popular",
                true
        );
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId("user-1");
        request.setPlanCode("boost");
        request.setCustomerName("Test User");
        request.setCustomerEmail("user@example.com");

        when(paymentPlanCatalog.requirePlan("boost")).thenReturn(plan);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(restTemplate.exchange(
                eq("https://api.razorpay.com/v1/orders"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of(
                "id", "order_test_123",
                "status", "created",
                "amount", 24900,
                "currency", "INR"
        )));

        paymentService.createOrder(request);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());

        Payment savedPayment = paymentCaptor.getValue();
        assertEquals("order_test_123", savedPayment.getOrderId());
        assertEquals("boost", savedPayment.getPlanCode());
        assertEquals(150, savedPayment.getCredits());
        assertEquals("CREATED", savedPayment.getStatus());
        assertEquals(24900L, savedPayment.getAmountInPaise());
    }

    @Test
    void verifyPaymentRejectsInvalidSignature() {
        Payment payment = Payment.builder()
                .orderId("order_test_123")
                .amount(249.00)
                .amountInPaise(24900L)
                .currency("INR")
                .planCode("boost")
                .planName("Daily Boost")
                .status("CREATED")
                .userId("user-1")
                .credits(150)
                .build();
        PaymentVerificationRequest request = new PaymentVerificationRequest();
        request.setOrderId("order_test_123");
        request.setPaymentId("pay_test_123");
        request.setSignature("invalid-signature");

        when(paymentRepository.findByOrderId("order_test_123")).thenReturn(Optional.of(payment));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.verifyPayment(request));

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("signature"));
        verify(kafkaTemplate, never()).send(any(String.class), any(String.class), any());
    }

    @Test
    void verifyPaymentCreditsUserViaAuthServiceBeforeKafkaFallback() {
        Payment payment = Payment.builder()
                .orderId("order_test_123")
                .amount(249.00)
                .amountInPaise(24900L)
                .currency("INR")
                .planCode("boost")
                .planName("Daily Boost")
                .status("CREATED")
                .userId("user-1")
                .credits(150)
                .build();
        PaymentVerificationRequest request = new PaymentVerificationRequest();
        request.setOrderId("order_test_123");
        request.setPaymentId("pay_test_123");
        request.setSignature(SignatureUtil.generateSignature("order_test_123|pay_test_123", "secret_123"));

        when(paymentRepository.findByOrderId("order_test_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(restTemplate.exchange(
                eq("https://api.razorpay.com/v1/payments/pay_test_123"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of(
                "id", "pay_test_123",
                "order_id", "order_test_123",
                "status", "captured",
                "captured", true,
                "amount", 24900,
                "currency", "INR"
        )));
        when(restTemplate.exchange(
                eq("http://localhost:9002/auth/users/{uid}/translation-credits/top-up?credits={credits}&orderId={orderId}&paymentId={paymentId}"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class),
                eq("user-1"),
                eq(150),
                eq("order_test_123"),
                eq("pay_test_123")
        )).thenReturn(ResponseEntity.ok("credited"));

        var response = paymentService.verifyPayment(request);

        assertTrue(response.creditsApplied());
        assertEquals("CREDITED", payment.getStatus());
        verify(kafkaTemplate, never()).send(any(String.class), any(String.class), any());
    }

    @Test
    void verifyPaymentFallsBackToKafkaWhenDirectCreditSyncFails() {
        Payment payment = Payment.builder()
                .orderId("order_test_123")
                .amount(249.00)
                .amountInPaise(24900L)
                .currency("INR")
                .planCode("boost")
                .planName("Daily Boost")
                .status("CREATED")
                .userId("user-1")
                .credits(150)
                .build();
        PaymentVerificationRequest request = new PaymentVerificationRequest();
        request.setOrderId("order_test_123");
        request.setPaymentId("pay_test_123");
        request.setSignature(SignatureUtil.generateSignature("order_test_123|pay_test_123", "secret_123"));

        when(paymentRepository.findByOrderId("order_test_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(restTemplate.exchange(
                eq("https://api.razorpay.com/v1/payments/pay_test_123"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of(
                "id", "pay_test_123",
                "order_id", "order_test_123",
                "status", "captured",
                "captured", true,
                "amount", 24900,
                "currency", "INR"
        )));
        when(restTemplate.exchange(
                eq("http://localhost:9002/auth/users/{uid}/translation-credits/top-up?credits={credits}&orderId={orderId}&paymentId={paymentId}"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class),
                eq("user-1"),
                eq(150),
                eq("order_test_123"),
                eq("pay_test_123")
        )).thenThrow(new RuntimeException("auth-service unavailable"));
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        var response = paymentService.verifyPayment(request);

        assertTrue(response.paymentVerified());
        assertTrue(!response.creditsApplied());
        assertEquals("PAID", payment.getStatus());
        verify(kafkaTemplate).send(any(String.class), eq("user-1"), any());
    }
}
