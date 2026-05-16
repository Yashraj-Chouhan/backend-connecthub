package com.connecthub.authservice.service;

import com.connecthub.authservice.dto.*;
import com.connecthub.authservice.entity.CreditTopupTransaction;
import com.connecthub.authservice.entity.User;
import com.connecthub.authservice.repository.CreditTopupTransactionRepository;
import com.connecthub.authservice.repository.PendingRegistrationRepository;
import com.connecthub.authservice.repository.UserRepository;
import com.connecthub.authservice.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CreditTopupTransactionRepository creditTopupTransactionRepository;

    @Mock
    private PendingRegistrationRepository pendingRegistrationRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private GoogleIdentityVerifier googleIdentityVerifier;

    @Mock
    private UsernameBloomFilter usernameBloomFilter;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                creditTopupTransactionRepository,
                pendingRegistrationRepository,
                jwtUtil,
                passwordEncoder,
                emailService,
                googleIdentityVerifier,
                usernameBloomFilter);
    }

    @Test
    void login_ValidCredentials_ReturnsResponse() {
        LoginRequest request = new LoginRequest();
        request.setIdentifier("test@example.com");
        request.setPassword("password123");

        User user = User.builder()
                .userId("user-1")
                .email("test@example.com")
                .username("testuser")
                .preferredLanguage("en")
                .translationCreditsRemaining(50)
                .role("USER")
                .password("hashed")
                .build();

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken("user-1")).thenReturn("token");

        AuthLoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("token", response.getToken());
        assertEquals("user-1", response.getUserId());
    }

    @Test
    void login_InvalidPassword_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.setIdentifier("test@example.com");
        request.setPassword("wrong");

        User user = User.builder().password("hashed").build();

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.login(request));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void loginWithGoogle_LinksExistingEmailAndReturnsResponse() {
        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setIdToken("google-token");

        User user = User.builder()
                .userId("user-1")
                .email("test@example.com")
                .username("testuser")
                .preferredLanguage("en")
                .translationCreditsRemaining(50)
                .role("USER")
                .onlineStatus("OFFLINE")
                .build();

        when(googleIdentityVerifier.verify("google-token")).thenReturn(
                new GoogleUserProfile("google-subject-1", "test@example.com", "Test User", "https://example.com/avatar.jpg"));
        when(userRepository.findByGoogleSubject("google-subject-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtil.generateToken("user-1")).thenReturn("google-token-jwt");

        AuthLoginResponse response = authService.loginWithGoogle(request);

        assertEquals("google-token-jwt", response.getToken());
        assertEquals("google-subject-1", user.getGoogleSubject());
        assertEquals("Test User", user.getFullName());
        assertEquals("https://example.com/avatar.jpg", user.getAvatarUrl());
    }

    @Test
    void loginWithGoogle_CreatesNewUserWhenNoMatchExists() {
        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setIdToken("google-token");

        when(googleIdentityVerifier.verify("google-token")).thenReturn(
                new GoogleUserProfile("google-subject-2", "new.user@example.com", "New User", null));
        when(userRepository.findByGoogleSubject("google-subject-2")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("new.user@example.com")).thenReturn(Optional.empty());
        when(usernameBloomFilter.mightContain("new.user")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-random-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setUserId("user-2");
            return savedUser;
        });
        when(jwtUtil.generateToken("user-2")).thenReturn("google-token-jwt");

        AuthLoginResponse response = authService.loginWithGoogle(request);

        assertEquals("google-token-jwt", response.getToken());
        assertEquals("user-2", response.getUserId());
        assertEquals("new.user@example.com", response.getEmail());
        assertEquals("new.user", response.getUsername());
        verify(usernameBloomFilter).add("new.user");
    }

    @Test
    void loginWithGoogle_RejectsBlockedUser() {
        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setIdToken("google-token");

        User user = User.builder()
                .userId("user-3")
                .email("blocked@example.com")
                .username("blockeduser")
                .googleSubject("google-blocked")
                .fullName("Blocked User")
                .preferredLanguage("en")
                .translationCreditsRemaining(50)
                .role("USER")
                .onlineStatus("OFFLINE")
                .isBlocked(true)
                .build();

        when(googleIdentityVerifier.verify("google-token")).thenReturn(
                new GoogleUserProfile("google-blocked", "blocked@example.com", "Blocked User", null));
        when(userRepository.findByGoogleSubject("google-blocked")).thenReturn(Optional.of(user));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.loginWithGoogle(request));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void forgotPassword_ValidUser_SendsOtp() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setIdentifier("test@example.com");

        User user = User.builder().email("test@example.com").build();

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        String result = authService.forgotPassword(request);

        assertEquals("A verification code has been sent to your registered email address.", result);
        verify(emailService).sendOtpEmail(eq("test@example.com"), anyString());
    }

    @Test
    void getUserById_ValidId_ReturnsSummary() {
        String userId = "user-1";
        User user = User.builder()
                .userId(userId)
                .username("testuser")
                .fullName("Test User")
                .email("test@example.com")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserSummaryResponse response = authService.getUserById(userId);

        assertNotNull(response);
        assertEquals(userId, response.getUserId());
    }

    @Test
    void getUserById_InvalidId_ThrowsException() {
        String userId = "invalid";

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.getUserById(userId));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void updateStatus_OfflineSetsLastSeenAt() {
        User user = User.builder()
                .userId("user-1")
                .onlineStatus("ONLINE")
                .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime beforeUpdate = LocalDateTime.now().minusSeconds(1);
        UserSummaryResponse response = authService.updateStatus("user-1", "offline");
        LocalDateTime afterUpdate = LocalDateTime.now().plusSeconds(1);

        assertEquals("OFFLINE", user.getOnlineStatus());
        assertNotNull(user.getLastSeenAt());
        assertFalse(user.getLastSeenAt().isBefore(beforeUpdate));
        assertFalse(user.getLastSeenAt().isAfter(afterUpdate));
        assertEquals(user.getLastSeenAt().toString(), response.getLastSeenAt());
    }

    @Test
    void updateStatus_OnlineClearsLastSeenAt() {
        User user = User.builder()
                .userId("user-1")
                .onlineStatus("OFFLINE")
                .lastSeenAt(LocalDateTime.now().minusHours(2))
                .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserSummaryResponse response = authService.updateStatus("user-1", "ONLINE");

        assertEquals("ONLINE", user.getOnlineStatus());
        assertNull(user.getLastSeenAt());
        assertNull(response.getLastSeenAt());
    }

    @Test
    void topUpTranslationCredits_WithOrderId_AddsCreditsAndStoresTransaction() {
        User user = User.builder()
                .userId("user-1")
                .translationCreditsRemaining(50)
                .build();

        when(creditTopupTransactionRepository.findByOrderId("order-1")).thenReturn(Optional.empty());
        when(creditTopupTransactionRepository.saveAndFlush(any(CreditTopupTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserSummaryResponse response = authService.topUpTranslationCredits(
                "user-1",
                25,
                "order-1",
                "pay-1",
                "PAYMENT_SERVICE_HTTP");

        assertEquals(75, response.getTranslationCreditsRemaining());
        verify(creditTopupTransactionRepository).saveAndFlush(any(CreditTopupTransaction.class));
        verify(userRepository).save(user);
    }

    @Test
    void topUpTranslationCredits_DuplicateOrderId_ReturnsExistingBalanceWithoutSavingAgain() {
        User user = User.builder()
                .userId("user-1")
                .translationCreditsRemaining(75)
                .build();
        CreditTopupTransaction existingTransaction = CreditTopupTransaction.builder()
                .orderId("order-1")
                .paymentId("pay-1")
                .userId("user-1")
                .credits(25)
                .source("KAFKA")
                .build();

        when(creditTopupTransactionRepository.findByOrderId("order-1")).thenReturn(Optional.of(existingTransaction));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UserSummaryResponse response = authService.topUpTranslationCredits(
                "user-1",
                25,
                "order-1",
                "pay-1",
                "PAYMENT_SERVICE_HTTP");

        assertEquals(75, response.getTranslationCreditsRemaining());
        verify(creditTopupTransactionRepository, never()).saveAndFlush(any(CreditTopupTransaction.class));
        verify(userRepository, never()).save(user);
    }
}
