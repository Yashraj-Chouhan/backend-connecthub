package com.connecthub.authservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_username", columnNames = "username")
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String userId;

    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String phoneNumber;

    @Column(unique = true)
    private String googleSubject;

    private String password;

    private String username;

    private String fullName;

    private String avatarUrl;

    @Column(length = 1000)
    private String bio;

    private String preferredLanguage;

    /**
     * Remaining one-off translation credits for the current account.
     * The backend treats this as the simple paywall balance.
     */
    private Integer translationCreditsRemaining;

    private String role;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private Boolean isBlocked = false;

    private String onlineStatus;

    private LocalDateTime lastSeenAt;

    @Column(name = "password_reset_token", unique = true)
    private String passwordResetToken;

    private LocalDateTime passwordResetTokenExpiry;
}
