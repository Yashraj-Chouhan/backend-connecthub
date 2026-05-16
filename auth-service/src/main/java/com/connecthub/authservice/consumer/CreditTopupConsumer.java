package com.connecthub.authservice.consumer;

import com.connecthub.authservice.dto.CreditTopupEvent;
import com.connecthub.authservice.service.AuthService;
import com.connecthub.authservice.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
/**
 * Listens for successful payment events and applies purchased translation
 * credits to the matching user account.
 */
@RequiredArgsConstructor
@Slf4j
public class CreditTopupConsumer {

    private final AuthService authService;

    @KafkaListener(topics = "credit-topup-topic", groupId = "auth-group")
    public void consume(CreditTopupEvent event) {
        log.info("Received credit top-up event for user {}: {} credits", event.getUserId(), event.getCredits());
        try {
            authService.topUpTranslationCredits(
                    event.getUserId(),
                    event.getCredits(),
                    event.getOrderId(),
                    event.getPaymentId(),
                    "KAFKA");
            log.info("Successfully updated credits for user {}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to update credits for user {}: {}", event.getUserId(), e.getMessage());
        }
    }
}
