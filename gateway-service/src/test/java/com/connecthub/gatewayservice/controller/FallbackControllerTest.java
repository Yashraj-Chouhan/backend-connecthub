package com.connecthub.gatewayservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackControllerTest {

    private final FallbackController controller = new FallbackController();

    @Test
    void everyExplicitFallbackMethodReturnsTheSameServiceUnavailablePayload() {
        assertFallbackResponse(controller.fallbackGet().block());
        assertFallbackResponse(controller.fallbackPost().block());
        assertFallbackResponse(controller.fallbackPut().block());
        assertFallbackResponse(controller.fallbackPatch().block());
        assertFallbackResponse(controller.fallbackDelete().block());
    }

    private void assertFallbackResponse(ResponseEntity<Map<String, Object>> response) {
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody())
                .containsEntry("status", HttpStatus.SERVICE_UNAVAILABLE.value())
                .containsEntry("error", "Service Unavailable")
                .containsEntry("message", "The requested service is currently unavailable. Please try again later.");
    }
}
