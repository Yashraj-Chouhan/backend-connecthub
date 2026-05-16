package com.connecthub.presenceservice.controller;

import com.connecthub.presenceservice.service.PresenceService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
/**
 * Exposes the presence endpoints used to mark users online/offline and retrieve
 * the latest known presence status.
 */
@RequestMapping("/presence")
@Validated
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService service;

    @PostMapping("/online/{userId}")
    public String online(@PathVariable @NotBlank(message = "User ID is required") String userId) {
        service.setOnline(userId);
        return "User is online";
    }

    @PostMapping("/offline/{userId}")
    public String offline(@PathVariable @NotBlank(message = "User ID is required") String userId) {
        service.setOffline(userId);
        return "User is offline";
    }

    @GetMapping("/{userId}")
    public String status(@PathVariable @NotBlank(message = "User ID is required") String userId) {
        return service.getStatus(userId);
    }
}
