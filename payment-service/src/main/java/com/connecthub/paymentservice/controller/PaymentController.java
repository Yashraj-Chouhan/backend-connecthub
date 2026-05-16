package com.connecthub.paymentservice.controller;

import com.connecthub.paymentservice.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.connecthub.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
/**
 * Exposes payment configuration, order creation, verification, and payment
 * history endpoints for translation credit top-ups.
 */
@RequestMapping("/payments")
@Validated
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/config")
    public ResponseEntity<PaymentConfigResponse> config() {
        return ResponseEntity.ok(paymentService.getConfig());
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<List<PaymentHistoryResponse>> history(@PathVariable @NotBlank(message = "User ID is required") String userId) {
        return ResponseEntity.ok(paymentService.getHistory(userId));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<AuthUserSummaryResponse> user(@PathVariable @NotBlank(message = "User ID is required") String userId) {
        return ResponseEntity.ok(paymentService.getUserSummary(userId));
    }

    @PostMapping("/create-order")
    public ResponseEntity<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.ok(paymentService.createOrder(request));
    }

    @PostMapping("/verify")
    public ResponseEntity<PaymentVerificationResponse> verify(@Valid @RequestBody PaymentVerificationRequest request) {
        return ResponseEntity.ok(paymentService.verifyPayment(request));
    }
}
