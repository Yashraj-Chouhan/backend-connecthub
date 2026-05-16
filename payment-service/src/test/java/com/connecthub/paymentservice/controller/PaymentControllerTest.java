package com.connecthub.paymentservice.controller;

import com.connecthub.paymentservice.dto.AuthUserSummaryResponse;
import com.connecthub.paymentservice.dto.CreateOrderRequest;
import com.connecthub.paymentservice.dto.CreateOrderResponse;
import com.connecthub.paymentservice.dto.PaymentConfigResponse;
import com.connecthub.paymentservice.dto.PaymentHistoryResponse;
import com.connecthub.paymentservice.dto.PaymentVerificationRequest;
import com.connecthub.paymentservice.dto.PaymentVerificationResponse;
import com.connecthub.paymentservice.dto.TopUpPlanResponse;
import com.connecthub.paymentservice.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @Test
    void endpointsDelegateToService() {
        PaymentController controller = new PaymentController(paymentService);
        PaymentConfigResponse config = new PaymentConfigResponse(
                "ConnectHub",
                "INR",
                "razorpay",
                "rzp_test",
                new BigDecimal("2.50"),
                25,
                5000,
                "support",
                List.of(new TopUpPlanResponse("boost", "Boost", "desc", 100, new BigDecimal("99.00"), "tag", true))
        );
        PaymentHistoryResponse history = new PaymentHistoryResponse(
                "order-1",
                "pay-1",
                "boost",
                "Boost",
                100,
                new BigDecimal("99.00"),
                "INR",
                "PAID",
                "user-1",
                LocalDateTime.now(),
                null,
                null,
                null
        );
        AuthUserSummaryResponse user = new AuthUserSummaryResponse(
                "user-1", "tester", "Test User", "user@example.com", null, null, null, "english", 10, "ONLINE", null, "USER"
        );
        CreateOrderResponse order = new CreateOrderResponse(
                "order-1", "razorpay", "rzp_test", null, "INR", new BigDecimal("99.00"), 9900L, 100, "boost", "Boost", "receipt-1", "user-1", "created"
        );
        PaymentVerificationResponse verification = new PaymentVerificationResponse(
                true, true, "Payment verified", "order-1", "pay-1", "PAID", "boost", "Boost", 100, 110, user, null
        );

        when(paymentService.getConfig()).thenReturn(config);
        when(paymentService.getHistory("user-1")).thenReturn(List.of(history));
        when(paymentService.getUserSummary("user-1")).thenReturn(user);
        when(paymentService.createOrder(any(CreateOrderRequest.class))).thenReturn(order);
        when(paymentService.verifyPayment(any(PaymentVerificationRequest.class))).thenReturn(verification);

        ResponseEntity<PaymentConfigResponse> configResponse = controller.config();
        ResponseEntity<List<PaymentHistoryResponse>> historyResponse = controller.history("user-1");
        ResponseEntity<AuthUserSummaryResponse> userResponse = controller.user("user-1");
        ResponseEntity<CreateOrderResponse> orderResponse = controller.createOrder(new CreateOrderRequest());
        ResponseEntity<PaymentVerificationResponse> verifyResponse = controller.verify(new PaymentVerificationRequest());

        assertThat(configResponse.getBody()).isEqualTo(config);
        assertThat(historyResponse.getBody()).containsExactly(history);
        assertThat(userResponse.getBody()).isEqualTo(user);
        assertThat(orderResponse.getBody()).isEqualTo(order);
        assertThat(verifyResponse.getBody()).isEqualTo(verification);

        verify(paymentService).getConfig();
        verify(paymentService).getHistory("user-1");
        verify(paymentService).getUserSummary("user-1");
        verify(paymentService).createOrder(any(CreateOrderRequest.class));
        verify(paymentService).verifyPayment(any(PaymentVerificationRequest.class));
    }
}
