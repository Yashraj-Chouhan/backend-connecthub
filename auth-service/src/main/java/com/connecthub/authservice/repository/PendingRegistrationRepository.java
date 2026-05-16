package com.connecthub.authservice.repository;

import com.connecthub.authservice.entity.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, String> {

    Optional<PendingRegistration> findByEmail(String email);

    Optional<PendingRegistration> findByPhoneNumber(String phoneNumber);

    Optional<PendingRegistration> findByEmailAndOtp(String email, String otp);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    /** Clean up expired pending registrations (called periodically) */
    void deleteByExpiresAtBefore(LocalDateTime now);
}
