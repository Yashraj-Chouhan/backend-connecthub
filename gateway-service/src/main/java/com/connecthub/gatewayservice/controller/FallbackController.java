package com.connecthub.gatewayservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> fallbackGet() {
        return serviceUnavailable();
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> fallbackPost() {
        return serviceUnavailable();
    }

    @PutMapping
    public Mono<ResponseEntity<Map<String, Object>>> fallbackPut() {
        return serviceUnavailable();
    }

    @PatchMapping
    public Mono<ResponseEntity<Map<String, Object>>> fallbackPatch() {
        return serviceUnavailable();
    }

    @DeleteMapping
    public Mono<ResponseEntity<Map<String, Object>>> fallbackDelete() {
        return serviceUnavailable();
    }

    private Mono<ResponseEntity<Map<String, Object>>> serviceUnavailable() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("error", "Service Unavailable");
        response.put("message", "The requested service is currently unavailable. Please try again later.");

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
    }
}
