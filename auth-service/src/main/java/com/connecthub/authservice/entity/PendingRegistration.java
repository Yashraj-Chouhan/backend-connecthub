package com.connecthub.authservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stores a pending (unverified) registration until the user confirms with OTP.
 * The record is deleted once the user either confirms or the OTP expires.
 */
@Entity
@Table(name = "pending_registrations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String phoneNumber;

    private String username;

    /** BCrypt-encoded password — stored here before the real User is created */
    private String encodedPassword;

    /** 6-digit OTP sent to the user's email */
    private String otp;

    private LocalDateTime expiresAt;
}
