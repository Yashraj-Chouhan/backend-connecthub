package com.connecthub.notificationservice.controller;

import com.connecthub.notificationservice.dto.NotificationRequest;
import com.connecthub.notificationservice.entity.Notification;
import com.connecthub.notificationservice.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
/**
 * Exposes APIs for creating, reading, and clearing unread notifications.
 */
@RequestMapping("/notifications")
@Validated
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping
    public Notification create(@Valid @RequestBody NotificationRequest request) {
        return service.createNotification(request);
    }

    @GetMapping("/{userId}")
    public List<Notification> getUnread(@PathVariable @NotBlank(message = "User ID is required") String userId) {
        return service.getUnread(userId);
    }

    @GetMapping("/{userId}/count")
    public long unreadCount(@PathVariable @NotBlank(message = "User ID is required") String userId) {
        return service.countUnread(userId);
    }

    @PutMapping("/{id}/read")
    public String markRead(@PathVariable Long id) {
        return service.markAsRead(id);
    }

    @PutMapping("/{userId}/read-all")
    public String markAllRead(@PathVariable @NotBlank(message = "User ID is required") String userId) {
        return service.markAllRead(userId);
    }
}
