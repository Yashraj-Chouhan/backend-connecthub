package com.connecthub.authservice.service;

import com.connecthub.authservice.dto.RegisterRequest;
import com.connecthub.authservice.dto.LoginRequest;
import com.connecthub.authservice.dto.UpdateProfileRequest;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceUsernameTest {

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
    void registerAddsUsernameToBloomFilterAndPersistsUser() {
        // The old register method in AuthService is likely removed or refactored to initiateRegistration
        // If it's still there we can keep it for backwards compatibility tests or we just change the call
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPhoneNumber("9999999999");
        request.setPassword("secret123");
        request.setUsername("NewUser");

        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("9999999999")).thenReturn(false);
        when(usernameBloomFilter.mightContain("newuser")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request);

        verify(userRepository).save(any(User.class));
        verify(usernameBloomFilter).add("NewUser");
        verify(userRepository, never()).existsByUsernameIgnoreCase("newuser");
    }

    @Test
    void registerRejectsDuplicateUsernameWhenBloomAndDatabaseAgree() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new2@example.com");
        request.setPhoneNumber("9999999998");
        request.setPassword("secret123");
        request.setUsername("TakenUser");

        when(userRepository.existsByEmailIgnoreCase("new2@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("9999999998")).thenReturn(false);
        when(usernameBloomFilter.mightContain("takenuser")).thenReturn(true);
        when(userRepository.existsByUsernameIgnoreCase("takenuser")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> authService.register(request));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerAllowsBloomFalsePositiveWhenDatabaseIsClear() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new3@example.com");
        request.setPhoneNumber("9999999997");
        request.setPassword("secret123");
        request.setUsername("FalsePositive");

        when(userRepository.existsByEmailIgnoreCase("new3@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("9999999997")).thenReturn(false);
        when(usernameBloomFilter.mightContain("falsepositive")).thenReturn(true);
        when(userRepository.existsByUsernameIgnoreCase("falsepositive")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request);

        verify(userRepository).existsByUsernameIgnoreCase("falsepositive");
        verify(userRepository).save(any(User.class));
        verify(usernameBloomFilter).add("FalsePositive");
    }

    @Test
    void updateProfileRejectsExistingUsername() {
        User user = User.builder()
                .userId("user-1")
                .username("currentuser")
                .fullName("Current User")
                .email("current@example.com")
                .phoneNumber("1111111111")
                .translationCreditsRemaining(50)
                .onlineStatus("OFFLINE")
                .role("USER")
                .build();

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUsername("ExistingUser");

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(usernameBloomFilter.mightContain("existinguser")).thenReturn(true);
        when(userRepository.existsByUsernameIgnoreCase("existinguser")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.updateProfile("user-1", request));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateProfileNormalizesHindiPreferenceToLanguageCode() {
        User user = User.builder()
                .userId("user-2")
                .username("receiver")
                .fullName("Receiver")
                .email("receiver@example.com")
                .phoneNumber("2222222222")
                .preferredLanguage("en")
                .translationCreditsRemaining(50)
                .onlineStatus("OFFLINE")
                .role("USER")
                .build();

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setPreferredLanguage("Hindi");

        when(userRepository.findById("user-2")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.updateProfile("user-2", request);

        assertEquals("hi", response.getPreferredLanguage());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void loginNormalizesJapanesePreferenceToLanguageCode() {
        User user = User.builder()
                .userId("user-3")
                .username("receiverja")
                .fullName("Receiver JA")
                .email("receiver.ja@example.com")
                .phoneNumber("3333333333")
                .password("hashed-password")
                .preferredLanguage("Japanese")
                .translationCreditsRemaining(50)
                .onlineStatus("OFFLINE")
                .role("USER")
                .build();

        LoginRequest request = new LoginRequest();
        request.setIdentifier("receiver.ja@example.com");
        request.setPassword("secret123");

        when(userRepository.findByEmailIgnoreCase("receiver.ja@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hashed-password")).thenReturn(true);
        when(jwtUtil.generateToken("user-3")).thenReturn("token-123");

        var response = authService.login(request);

        assertEquals("ja", response.getPreferredLanguage());
    }
}
