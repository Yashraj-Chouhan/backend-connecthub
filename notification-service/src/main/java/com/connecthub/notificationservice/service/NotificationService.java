package com.connecthub.notificationservice.service;

import com.connecthub.notificationservice.dto.NotificationRequest;
import com.connecthub.notificationservice.entity.Notification;
import com.connecthub.notificationservice.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
/**
 * Stores notification rows and exposes convenience operations for unread state.
 */
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    public Notification createNotification(NotificationRequest request) {
        Notification notification = new Notification();
        notification.setUserId(request.getUserId());
        notification.setMessage(request.getMessage());
        notification.setRead(false);
        notification.setTimestamp(LocalDateTime.now());

        return repository.save(notification);
    }

    public List<Notification> getUnread(String userId) {
        return repository.findByUserIdAndReadFalse(userId);
    }

    public long countUnread(String userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    public String markAllRead(String userId) {
        repository.findByUserIdAndReadFalse(userId).forEach(notification -> {
            notification.setRead(true);
            repository.save(notification);
        });
        return "Marked all as read";
    }

    public String markAsRead(Long id) {
        Notification notification = repository.findById(id).orElseThrow();
        notification.setRead(true);
        repository.save(notification);
        return "Marked as read";
    }
}
