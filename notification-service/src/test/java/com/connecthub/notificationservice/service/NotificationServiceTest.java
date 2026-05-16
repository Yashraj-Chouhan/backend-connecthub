package com.connecthub.notificationservice.service;

import com.connecthub.notificationservice.dto.NotificationRequest;
import com.connecthub.notificationservice.entity.Notification;
import com.connecthub.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository);
    }

    @Test
    void createNotificationInitializesUnreadTimestampedRecord() {
        NotificationRequest request = new NotificationRequest();
        request.setUserId("user-1");
        request.setMessage("A new message arrived");

        when(repository.save(org.mockito.ArgumentMatchers.any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Notification created = service.createNotification(request);

        assertThat(created.getUserId()).isEqualTo("user-1");
        assertThat(created.getMessage()).isEqualTo("A new message arrived");
        assertThat(created.isRead()).isFalse();
        assertThat(created.getTimestamp()).isNotNull();
    }

    @Test
    void unreadQueriesDelegateToRepository() {
        Notification unread = new Notification(1L, "user-1", "hello", false, null);
        when(repository.findByUserIdAndReadFalse("user-1")).thenReturn(List.of(unread));
        when(repository.countByUserIdAndReadFalse("user-1")).thenReturn(3L);

        assertThat(service.getUnread("user-1")).containsExactly(unread);
        assertThat(service.countUnread("user-1")).isEqualTo(3L);
    }

    @Test
    void markAllReadUpdatesEveryUnreadNotification() {
        Notification first = new Notification(1L, "user-1", "hello", false, null);
        Notification second = new Notification(2L, "user-1", "bye", false, null);
        when(repository.findByUserIdAndReadFalse("user-1")).thenReturn(List.of(first, second));

        String result = service.markAllRead("user-1");

        assertThat(result).isEqualTo("Marked all as read");
        assertThat(first.isRead()).isTrue();
        assertThat(second.isRead()).isTrue();
        verify(repository, times(2)).save(org.mockito.ArgumentMatchers.any(Notification.class));
    }

    @Test
    void markAsReadUpdatesSingleNotification() {
        Notification notification = new Notification(7L, "user-1", "hello", false, null);
        when(repository.findById(7L)).thenReturn(Optional.of(notification));

        String result = service.markAsRead(7L);

        assertThat(result).isEqualTo("Marked as read");
        assertThat(notification.isRead()).isTrue();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(7L);
    }
}
