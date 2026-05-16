package com.connecthub.authservice.service;

import com.connecthub.authservice.dto.*;
import com.connecthub.authservice.entity.CreditTopupTransaction;
import com.connecthub.authservice.entity.PendingRegistration;
import com.connecthub.authservice.entity.User;
import com.connecthub.authservice.repository.CreditTopupTransactionRepository;
import com.connecthub.authservice.repository.PendingRegistrationRepository;
import com.connecthub.authservice.repository.UserRepository;
import com.connecthub.authservice.security.JwtUtil;
import com.connecthub.authservice.validation.ValidationSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * Central business layer for user onboarding, login, recovery, profile edits,
 * role management, presence flags, and translation credit accounting.
 */
@RequiredArgsConstructor
public class AuthService {

    private static final int DEFAULT_TRANSLATION_CREDITS = 50;
    private static final int MAX_USERNAME_LENGTH = 30;
    private static final int MAX_GOOGLE_USERNAME_ATTEMPTS = 100;
    private static final int OTP_BOUND = 999999;
    private static final Map<String, String> LANGUAGE_ALIASES = Map.ofEntries(
            Map.entry("english", "en"),
            Map.entry("spanish", "es"),
            Map.entry("french", "fr"),
            Map.entry("german", "de"),
            Map.entry("hindi", "hi"),
            Map.entry("\u0939\u093f\u0902\u0926\u0940", "hi"),
            Map.entry("japanese", "ja"),
            Map.entry("portuguese", "pt"),
            Map.entry("italian", "it"),
            Map.entry("kannada", "kn"),
            Map.entry("malayalam", "ml"),
            Map.entry("tamil", "ta"),
            Map.entry("telugu", "te"),
            Map.entry("marathi", "mr"),
            Map.entry("gujarati", "gu"),
            Map.entry("bengali", "bn"),
            Map.entry("punjabi", "pa")
    );

    private final UserRepository userRepository;
    private final CreditTopupTransactionRepository creditTopupTransactionRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final GoogleIdentityVerifier googleIdentityVerifier;
    private final UsernameBloomFilter usernameBloomFilter;
    private final Object usernameLock = new Object();
    private final SecureRandom random = new SecureRandom();

    // ──────────────────────────────────────────────────────────────────────────────
    // Legacy direct-register (kept for backward compatibility / internal use)
    // ──────────────────────────────────────────────────────────────────────────────
    public void register(RegisterRequest request) {
        requireText(request.getEmail(), "Please enter your email address.");
        requireText(request.getPhoneNumber(), "Please enter your mobile number.");
        requireText(request.getPassword(), "Please create a password.");
        requireText(request.getUsername(), "Please choose a username.");

        if (!ValidationSupport.isValidEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter a valid email address.");
        }
        if (!ValidationSupport.isValidPhoneNumber(request.getPhoneNumber())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter a valid mobile number.");
        }
        if (!ValidationSupport.isStrongPassword(request.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 8 characters long and include at least one letter and one number.");
        }

        String email = normalizeEmail(request.getEmail());
        String phoneNumber = normalizePhoneNumber(request.getPhoneNumber());
        String username = normalizeUsernameValue(request.getUsername());
        String normalizedUsername = normalizeUsernameKey(username);

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email address already exists.");
        }

        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this mobile number already exists.");
        }

        User user = User.builder()
                .email(email)
                .phoneNumber(phoneNumber)
                .password(passwordEncoder.encode(request.getPassword()))
                .username(username)
                .fullName(username)
                .preferredLanguage("en")
                .translationCreditsRemaining(DEFAULT_TRANSLATION_CREDITS)
                .onlineStatus("OFFLINE")
                .role("USER")
                .build();

        synchronized (usernameLock) {
            ensureUsernameAvailability(normalizedUsername);
            saveUser(user);
            usernameBloomFilter.add(username);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // OTP-based signup — Step 1: validate, store pending, send OTP
    // ──────────────────────────────────────────────────────────────────────────────
    public void initiateRegistration(InitiateRegistrationRequest request) {
        requireText(request.getEmail(), "Please enter your email address.");
        requireText(request.getPhoneNumber(), "Please enter your mobile number.");
        requireText(request.getPassword(), "Please create a password.");
        requireText(request.getUsername(), "Please choose a username.");

        if (!ValidationSupport.isValidEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter a valid email address.");
        }
        if (!ValidationSupport.isValidPhoneNumber(request.getPhoneNumber())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter a valid mobile number (include country code).");
        }
        if (!ValidationSupport.isStrongPassword(request.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 8 characters and include at least one letter and one number.");
        }

        String email      = normalizeEmail(request.getEmail());
        String phone      = normalizePhoneNumber(request.getPhoneNumber());
        String username   = normalizeUsernameValue(request.getUsername());
        String normKey    = normalizeUsernameKey(username);

        // Check real users table
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email address already exists.");
        }
        if (userRepository.existsByPhoneNumber(phone)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this mobile number already exists.");
        }
        // Check pending table (allow re-submission to resend OTP)
        pendingRegistrationRepository.findByEmail(email).ifPresent(pendingRegistrationRepository::delete);

        // Username uniqueness (bloom + DB check)
        if (isBlank(normKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please choose a username.");
        }
        if (usernameBloomFilter.mightContain(normKey) && userRepository.existsByUsernameIgnoreCase(normKey)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already taken.");
        }

        String otp = generateOtp();

        PendingRegistration pending = PendingRegistration.builder()
                .email(email)
                .phoneNumber(phone)
                .username(username)
                .encodedPassword(passwordEncoder.encode(request.getPassword()))
                .otp(otp)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        pendingRegistrationRepository.save(pending);

        emailService.sendSignupOtpEmail(email, username, otp);
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // OTP-based signup — Step 2: verify OTP, create User, delete pending record
    // ──────────────────────────────────────────────────────────────────────────────
    public void completeRegistration(CompleteRegistrationRequest request) {
        requireText(request.getEmail(), "Please enter your email address.");
        requireText(request.getOtp(), "Please enter the verification code.");

        if (!ValidationSupport.isValidOtp(request.getOtp())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter the 6-digit verification code.");
        }

        String email = normalizeEmail(request.getEmail());

        PendingRegistration pending = pendingRegistrationRepository
                .findByEmailAndOtp(email, request.getOtp().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "That verification code is incorrect. Please try again."));

        if (pending.getExpiresAt() == null || pending.getExpiresAt().isBefore(LocalDateTime.now())) {
            pendingRegistrationRepository.delete(pending);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "That verification code has expired. Please sign up again to get a new code.");
        }

        // Double-check uniqueness before creating the real account
        if (userRepository.existsByEmailIgnoreCase(email)) {
            pendingRegistrationRepository.delete(pending);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email address already exists.");
        }
        if (userRepository.existsByPhoneNumber(pending.getPhoneNumber())) {
            pendingRegistrationRepository.delete(pending);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this mobile number already exists.");
        }

        String username    = pending.getUsername();
        String normalizedUsername = normalizeUsernameKey(username);

        User user = User.builder()
                .email(email)
                .phoneNumber(pending.getPhoneNumber())
                .password(pending.getEncodedPassword())
                .username(username)
                .fullName(username)
                .preferredLanguage("en")
                .translationCreditsRemaining(DEFAULT_TRANSLATION_CREDITS)
                .onlineStatus("OFFLINE")
                .role("USER")
                .build();

        synchronized (usernameLock) {
            ensureUsernameAvailability(normalizedUsername);
            saveUser(user);
            usernameBloomFilter.add(username);
        }

        pendingRegistrationRepository.delete(pending);
    }

    /**
     * Authenticates the user by email or phone number and returns the JWT plus
     * the profile fields the frontend needs immediately after sign-in.
     */
    public AuthLoginResponse login(LoginRequest request) {
        String identifier = request.getIdentifier() == null ? null : request.getIdentifier().trim();

        requireEmailOrPhone(identifier);
        requireText(request.getPassword(), "Please enter your password.");

        User user = findUserByIdentifier(identifier)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "We couldn't sign you in. Check your email/mobile number and password and try again."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "We couldn't sign you in. Check your email/mobile number and password and try again.");
        }

        assertAccountIsActive(user);

        return buildLoginResponse(user);
    }

    public AuthLoginResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleUserProfile googleProfile = googleIdentityVerifier.verify(request.getIdToken());
        User user = resolveGoogleUser(googleProfile);
        assertAccountIsActive(user);
        return buildLoginResponse(user);
    }

    /**
     * Starts the password recovery flow by generating an OTP and emailing it to
     * the user's registered address.
     */
    public String forgotPassword(ForgotPasswordRequest request) {
        String identifier = request.getIdentifier() == null ? null : request.getIdentifier().trim();

        requireEmailOrPhone(identifier);

        User user = findUserByIdentifier(identifier)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "We couldn't find an account with those details."));

        // Generate 6-digit OTP
        String otp = generateOtp();
        user.setPasswordResetToken(otp);
        user.setPasswordResetTokenExpiry(LocalDateTime.now().plusMinutes(30));
        userRepository.save(user);

        // Send OTP via email (only works if identifier is an email, but fallback handles logging)
        emailService.sendOtpEmail(user.getEmail(), otp);

        return "A verification code has been sent to your registered email address.";
    }

    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request) {
        String identifier = request.getIdentifier() == null ? null : request.getIdentifier().trim();
        String otp = request.getOtp() == null ? null : request.getOtp().trim();

        requireEmailOrPhone(identifier);
        requireText(otp, "Please enter the verification code.");
        if (!ValidationSupport.isValidOtp(otp)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter the 6-digit verification code.");
        }

        User user = findUserByIdentifier(identifier)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "We couldn't find an account with those details."));

        if (!otp.equals(user.getPasswordResetToken())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That verification code is not correct.");
        }

        if (user.getPasswordResetTokenExpiry() == null || user.getPasswordResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "That verification code has expired. Please request a new one.");
        }

        // OTP is valid. Generate a UUID reset token for the final step.
        String resetToken = UUID.randomUUID().toString().replace("-", "");
        user.setPasswordResetToken(resetToken);
        user.setPasswordResetTokenExpiry(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        return new VerifyOtpResponse(resetToken, "OTP verified successfully");
    }

    public void resetPassword(ResetPasswordRequest request) {
        requireText(request.getToken(), "Please enter the reset code.");
        requireText(request.getNewPassword(), "Please create a new password.");
        if (!ValidationSupport.isStrongPassword(request.getNewPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 8 characters long and include at least one letter and one number.");
        }

        User user = userRepository.findByPasswordResetToken(request.getToken())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "That reset code is invalid or has already been used."));

        if (user.getPasswordResetTokenExpiry() == null || user.getPasswordResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "That reset code has expired. Please request a new one.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        userRepository.save(user);
    }

    public UserSummaryResponse getUserById(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "We couldn't find that user."));
        return toSummary(user);
    }

    public List<UserSummaryResponse> searchUsers(String query) {
        String trimmedQuery = query == null ? null : query.trim();

        if (isBlank(trimmedQuery)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter something to search for.");
        }

        return userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrPhoneNumberContainingIgnoreCase(
                        trimmedQuery, trimmedQuery, trimmedQuery)
                .stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    public UserSummaryResponse getUserByEmail(String email) {
        requireText(email, "Please enter an email address.");
        String normalizedEmail = normalizeEmail(email);
        if (!ValidationSupport.isValidEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter a valid email address.");
        }

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "We couldn't find that user."));
        return toSummary(user);
    }

    public UserSummaryResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "We couldn't find that user."));

        String requestedUsername = normalizeUsernameValue(request.getUsername());
        String currentUsername = normalizeUsernameValue(user.getUsername());
        String normalizedRequestedUsername = normalizeUsernameKey(requestedUsername);
        boolean usernameChanged = !isBlank(requestedUsername)
                && !Objects.equals(normalizeUsernameKey(currentUsername), normalizedRequestedUsername);

        if (usernameChanged) {
            synchronized (usernameLock) {
                ensureUsernameAvailability(normalizedRequestedUsername);
                user.setUsername(requestedUsername);
                applyProfileUpdates(user, request);
                saveUser(user);
                usernameBloomFilter.add(requestedUsername);
                return toSummary(user);
            }
        }

        applyProfileUpdates(user, request);
        saveUser(user);
        return toSummary(user);
    }

    public void changePassword(String userId, ChangePasswordRequest request) {
        requireText(request.getCurrentPassword(), "Please enter your current password.");
        requireText(request.getNewPassword(), "Please create a new password.");
        if (!ValidationSupport.isStrongPassword(request.getNewPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 8 characters long and include at least one letter and one number.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "We couldn't find that user."));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your current password is not correct.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    public void markUserOnline(String userId) {
        updatePresence(userId, "ONLINE", null);
    }

    public void markUserOffline(String userId) {
        updatePresence(userId, "OFFLINE", LocalDateTime.now());
    }

    public UserSummaryResponse updateStatus(String userId, String status) {
        String normalizedStatus = normalizePresenceStatus(status);
        LocalDateTime lastSeenAt = "OFFLINE".equals(normalizedStatus) ? LocalDateTime.now() : null;
        User user = updatePresence(userId, normalizedStatus, lastSeenAt);
        return toSummary(user);
    }

    /**
     * Deducts a single translation credit before message-service calls the
     * translation provider.
     */
    public UserSummaryResponse consumeTranslationCredit(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "We couldn't find that user."));

        int remaining = ensureTranslationCredits(user);
        if (remaining <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.PAYMENT_REQUIRED,
                    "Translation limit reached. Add more credits to continue translating messages."
            );
        }

        user.setTranslationCreditsRemaining(remaining - 1);
        userRepository.save(user);
        return toSummary(user);
    }

    /**
     * Adds purchased or refunded translation credits back onto the user's
     * account balance.
     */
    public UserSummaryResponse topUpTranslationCredits(String userId, int credits) {
        if (credits <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please add at least 1 credit.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "We couldn't find that user."));

        int remaining = ensureTranslationCredits(user);
        user.setTranslationCreditsRemaining(Math.addExact(remaining, credits));
        userRepository.save(user);
        return toSummary(user);
    }

    /**
     * Applies a payment-backed top-up exactly once for a given order so retries
     * from HTTP or Kafka cannot double-credit the same purchase.
     */
    @Transactional
    public UserSummaryResponse topUpTranslationCredits(
            String userId,
            int credits,
            String orderId,
            String paymentId,
            String source) {
        if (credits <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please add at least 1 credit.");
        }

        requireText(orderId, "Order ID is required.");
        requireText(paymentId, "Payment ID is required.");
        requireText(source, "Top-up source is required.");

        String normalizedOrderId = orderId.trim();
        String normalizedPaymentId = paymentId.trim();
        String normalizedSource = source.trim().toUpperCase(Locale.ROOT);

        CreditTopupTransaction existingTransaction = creditTopupTransactionRepository.findByOrderId(normalizedOrderId)
                .orElse(null);
        if (existingTransaction != null) {
            return resolveDuplicateTopup(existingTransaction, userId, credits, normalizedPaymentId);
        }

        try {
            creditTopupTransactionRepository.saveAndFlush(CreditTopupTransaction.builder()
                    .orderId(normalizedOrderId)
                    .paymentId(normalizedPaymentId)
                    .userId(userId)
                    .credits(credits)
                    .source(normalizedSource)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            CreditTopupTransaction concurrentTransaction = creditTopupTransactionRepository.findByOrderId(normalizedOrderId)
                    .orElseThrow(() -> ex);
            return resolveDuplicateTopup(concurrentTransaction, userId, credits, normalizedPaymentId);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "We couldn't find that user."));

        int remaining = ensureTranslationCredits(user);
        user.setTranslationCreditsRemaining(Math.addExact(remaining, credits));
        userRepository.save(user);
        return toSummary(user);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void requireText(String value, String message) {
        if (isBlank(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private void requireEmailOrPhone(String identifier) {
        requireText(identifier, "Please enter your email address or mobile number.");
        if (!ValidationSupport.isValidEmailOrPhone(identifier)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Please enter a valid email address or mobile number.");
        }
    }

    private void ensureUsernameAvailability(String normalizedUsername) {
        if (isBlank(normalizedUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please choose a username.");
        }

        if (usernameBloomFilter.mightContain(normalizedUsername)
                && userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already taken.");
        }
    }

    private void applyProfileUpdates(User user, UpdateProfileRequest request) {
        if (!isBlank(request.getFullName())) {
            user.setFullName(request.getFullName());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        if (request.getPreferredLanguage() != null) {
            user.setPreferredLanguage(normalizePreferredLanguage(request.getPreferredLanguage()));
        }
        if (!isBlank(request.getOnlineStatus())) {
            user.setOnlineStatus(request.getOnlineStatus().toUpperCase(Locale.ROOT));
        }
    }

    private void saveUser(User user) {
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            String message = resolveUniqueConstraintMessage(ex);
            if (message != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, message);
            }
            throw ex;
        }
    }

    private String resolveUniqueConstraintMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalizedMessage = message.toLowerCase(Locale.ROOT);
                if (normalizedMessage.contains("google_subject")
                        && (normalizedMessage.contains("duplicate")
                        || normalizedMessage.contains("unique"))) {
                    return "This Google account is already linked to another user.";
                }
                if (normalizedMessage.contains("uk_users_username")
                        || normalizedMessage.contains("users.username")
                        || (normalizedMessage.contains("username") && normalizedMessage.contains("duplicate"))
                        || (normalizedMessage.contains("username") && normalizedMessage.contains("unique"))) {
                    return "That username is already taken.";
                }
                if (normalizedMessage.contains("email")
                        && (normalizedMessage.contains("duplicate")
                        || normalizedMessage.contains("unique")
                        || normalizedMessage.contains("uk6dotkott2kjsp8vw4d0m25fb7"))) {
                    return "An account with this email address already exists.";
                }
                if (normalizedMessage.contains("phone_number")
                        && (normalizedMessage.contains("duplicate")
                        || normalizedMessage.contains("unique")
                        || normalizedMessage.contains("uk9q63snka3mdh91as4io72espi"))) {
                    return "An account with this mobile number already exists.";
                }
            }
            current = current.getCause();
        }
        return null;
    }

    private UserSummaryResponse resolveDuplicateTopup(
            CreditTopupTransaction existingTransaction,
            String userId,
            int credits,
            String paymentId) {
        boolean sameRequest = existingTransaction.getUserId().equals(userId)
                && existingTransaction.getCredits() == credits
                && existingTransaction.getPaymentId().equals(paymentId);
        if (!sameRequest) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This payment has already been applied to a different credit top-up request.");
        }

        User user = userRepository.findById(existingTransaction.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "We couldn't find that user."));
        return toSummary(user);
    }

    private String normalizeEmail(String email) {
        return ValidationSupport.normalizeEmail(email);
    }

    private String normalizePhoneNumber(String phoneNumber) {
        return ValidationSupport.normalizePhoneNumber(phoneNumber);
    }

    private String normalizeUsernameValue(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = username.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeUsernameKey(String username) {
        String normalized = normalizeUsernameValue(username);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizePreferredLanguage(String preferredLanguage) {
        if (preferredLanguage == null) {
            return null;
        }

        String normalized = preferredLanguage.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.equals("none")) {
            return null;
        }

        String alias = LANGUAGE_ALIASES.get(normalized);
        if (alias != null) {
            return alias;
        }

        int hyphenIndex = normalized.indexOf('-');
        if (hyphenIndex > 0) {
            String baseCode = normalized.substring(0, hyphenIndex);
            alias = LANGUAGE_ALIASES.get(baseCode);
            return alias != null ? alias : baseCode;
        }

        int underscoreIndex = normalized.indexOf('_');
        if (underscoreIndex > 0) {
            String baseCode = normalized.substring(0, underscoreIndex);
            alias = LANGUAGE_ALIASES.get(baseCode);
            return alias != null ? alias : baseCode;
        }

        return normalized;
    }

    private User resolveGoogleUser(GoogleUserProfile googleProfile) {
        return userRepository.findByGoogleSubject(googleProfile.subject())
                .map(existingUser -> syncGoogleProfile(existingUser, googleProfile))
                .orElseGet(() -> userRepository.findByEmailIgnoreCase(googleProfile.email())
                        .map(existingUser -> linkGoogleAccount(existingUser, googleProfile))
                        .orElseGet(() -> createGoogleUser(googleProfile)));
    }

    private User linkGoogleAccount(User user, GoogleUserProfile googleProfile) {
        if (StringUtils.hasText(user.getGoogleSubject())
                && !Objects.equals(user.getGoogleSubject(), googleProfile.subject())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This email address is already linked to another Google account.");
        }
        return syncGoogleProfile(user, googleProfile);
    }

    private User syncGoogleProfile(User user, GoogleUserProfile googleProfile) {
        if (StringUtils.hasText(user.getGoogleSubject())
                && !Objects.equals(user.getGoogleSubject(), googleProfile.subject())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This Google account is already linked to another user.");
        }

        boolean changed = false;

        if (!Objects.equals(user.getGoogleSubject(), googleProfile.subject())) {
            user.setGoogleSubject(googleProfile.subject());
            changed = true;
        }
        if (!StringUtils.hasText(user.getEmail())) {
            user.setEmail(googleProfile.email());
            changed = true;
        }
        if (!StringUtils.hasText(user.getFullName()) && StringUtils.hasText(googleProfile.fullName())) {
            user.setFullName(googleProfile.fullName());
            changed = true;
        }
        if (!StringUtils.hasText(user.getAvatarUrl()) && StringUtils.hasText(googleProfile.pictureUrl())) {
            user.setAvatarUrl(googleProfile.pictureUrl());
            changed = true;
        }
        if (!StringUtils.hasText(user.getPreferredLanguage())) {
            user.setPreferredLanguage("en");
            changed = true;
        }
        if (user.getTranslationCreditsRemaining() == null) {
            user.setTranslationCreditsRemaining(DEFAULT_TRANSLATION_CREDITS);
            changed = true;
        }
        if (!StringUtils.hasText(user.getOnlineStatus())) {
            user.setOnlineStatus("OFFLINE");
            changed = true;
        }
        if (!StringUtils.hasText(user.getRole())) {
            user.setRole("USER");
            changed = true;
        }

        return changed ? userRepository.save(user) : user;
    }

    private User createGoogleUser(GoogleUserProfile googleProfile) {
        synchronized (usernameLock) {
            String username = generateAvailableGoogleUsername(googleProfile);
            User user = User.builder()
                    .email(googleProfile.email())
                    .googleSubject(googleProfile.subject())
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .username(username)
                    .fullName(firstNonBlank(googleProfile.fullName(), username))
                    .avatarUrl(googleProfile.pictureUrl())
                    .preferredLanguage("en")
                    .translationCreditsRemaining(DEFAULT_TRANSLATION_CREDITS)
                    .onlineStatus("OFFLINE")
                    .role("USER")
                    .build();
            saveUser(user);
            usernameBloomFilter.add(username);
            return user;
        }
    }

    private String generateAvailableGoogleUsername(GoogleUserProfile googleProfile) {
        String baseUsername = sanitizeUsernameCandidate(
                firstNonBlank(extractEmailLocalPart(googleProfile.email()), googleProfile.fullName(), "googleuser"));
        if (isBlank(baseUsername)) {
            baseUsername = "googleuser";
        }

        String truncatedBase = truncateUsername(baseUsername);
        if (isUsernameAvailable(truncatedBase)) {
            return truncatedBase;
        }

        String subjectTail = googleSubjectTail(googleProfile.subject());
        for (int attempt = 1; attempt <= MAX_GOOGLE_USERNAME_ATTEMPTS; attempt++) {
            String suffix = StringUtils.hasText(subjectTail)
                    ? subjectTail + attempt
                    : String.valueOf(attempt);
            String candidate = appendUsernameSuffix(truncatedBase, suffix);
            if (isUsernameAvailable(candidate)) {
                return candidate;
            }
        }

        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "We couldn't create a username for this Google account. Please try again.");
    }

    private boolean isUsernameAvailable(String username) {
        String normalized = normalizeUsernameKey(username);
        return !usernameBloomFilter.mightContain(normalized)
                || !userRepository.existsByUsernameIgnoreCase(normalized);
    }

    private String sanitizeUsernameCandidate(String username) {
        String normalized = normalizeUsernameValue(username);
        if (normalized == null) {
            return null;
        }
        String sanitized = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "")
                .replaceAll("^[._-]+|[._-]+$", "");
        return sanitized.isBlank() ? null : sanitized;
    }

    private String truncateUsername(String username) {
        if (username == null || username.length() <= MAX_USERNAME_LENGTH) {
            return username;
        }
        return username.substring(0, MAX_USERNAME_LENGTH);
    }

    private String appendUsernameSuffix(String baseUsername, String suffix) {
        String safeSuffix = sanitizeUsernameCandidate(suffix);
        if (isBlank(safeSuffix)) {
            safeSuffix = "1";
        }
        if (safeSuffix.length() >= MAX_USERNAME_LENGTH) {
            return safeSuffix.substring(0, MAX_USERNAME_LENGTH);
        }

        int maxBaseLength = MAX_USERNAME_LENGTH - safeSuffix.length();
        String truncatedBase = baseUsername.length() > maxBaseLength
                ? baseUsername.substring(0, maxBaseLength)
                : baseUsername;
        return truncatedBase + safeSuffix;
    }

    private String extractEmailLocalPart(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return email;
        }
        return email.substring(0, atIndex);
    }

    private String googleSubjectTail(String subject) {
        if (!StringUtils.hasText(subject)) {
            return null;
        }
        String sanitized = subject.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        if (sanitized.isBlank()) {
            return null;
        }
        int startIndex = Math.max(0, sanitized.length() - 4);
        return sanitized.substring(startIndex);
    }

    private AuthLoginResponse buildLoginResponse(User user) {
        String token = jwtUtil.generateToken(user.getUserId());
        return new AuthLoginResponse(
                token,
                user.getUserId(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getUsername(),
                normalizePreferredLanguage(user.getPreferredLanguage()),
                ensureTranslationCredits(user),
                user.getRole(),
                user.getAvatarUrl()
        );
    }

    private void assertAccountIsActive(User user) {
        if (Boolean.TRUE.equals(user.getIsBlocked())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your account has been temporarily suspended. Please contact support.");
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private java.util.Optional<User> findUserByIdentifier(String identifier) {
        if (identifier == null) {
            return java.util.Optional.empty();
        }

        String trimmedIdentifier = identifier.trim();
        if (ValidationSupport.isValidEmail(trimmedIdentifier)) {
            return userRepository.findByEmailIgnoreCase(normalizeEmail(trimmedIdentifier));
        }

        if (ValidationSupport.isValidPhoneNumber(trimmedIdentifier)) {
            String normalizedPhone = normalizePhoneNumber(trimmedIdentifier);
            if (normalizedPhone != null) {
                java.util.Optional<User> phoneMatch = userRepository.findByPhoneNumber(normalizedPhone);
                if (phoneMatch.isPresent()) {
                    return phoneMatch;
                }

                if (!normalizedPhone.equals(trimmedIdentifier)) {
                    phoneMatch = userRepository.findByPhoneNumber(trimmedIdentifier);
                    if (phoneMatch.isPresent()) {
                        return phoneMatch;
                    }
                }
            }
        }

        return java.util.Optional.empty();
    }

    private UserSummaryResponse toSummary(User user) {
        int translationCreditsRemaining = ensureTranslationCredits(user);
        return new UserSummaryResponse(
                user.getUserId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getAvatarUrl(),
                user.getBio(),
                normalizePreferredLanguage(user.getPreferredLanguage()),
                translationCreditsRemaining,
                user.getOnlineStatus(),
                user.getLastSeenAt() == null ? null : user.getLastSeenAt().toString(),
                user.getRole(),
                Boolean.TRUE.equals(user.getIsBlocked())
        );
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Admin-only operations
    // ──────────────────────────────────────────────────────────────────────────────
    public List<UserSummaryResponse> getAllUsersForAdmin() {
        return userRepository.findAll()
                .stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    public UserSummaryResponse toggleUserBlockStatus(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        boolean currentlyBlocked = Boolean.TRUE.equals(user.getIsBlocked());
        user.setIsBlocked(!currentlyBlocked);
        userRepository.save(user);
        return toSummary(user);
    }

    public void deleteUserAsAdmin(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        userRepository.delete(user);
    }

    public UserSummaryResponse adminSetCredits(String userId, int credits) {
        if (credits < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credits cannot be negative.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        user.setTranslationCreditsRemaining(credits);
        userRepository.save(user);
        return toSummary(user);
    }

    private int ensureTranslationCredits(User user) {
        Integer remaining = user.getTranslationCreditsRemaining();
        if (remaining == null) {
            user.setTranslationCreditsRemaining(DEFAULT_TRANSLATION_CREDITS);
            userRepository.save(user);
            return DEFAULT_TRANSLATION_CREDITS;
        }
        return remaining;
    }

    private User updatePresence(String userId, String status, LocalDateTime lastSeenAt) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "We couldn't find that user."));
        user.setOnlineStatus(status);
        if ("ONLINE".equals(status)) {
            user.setLastSeenAt(null);
        } else if (lastSeenAt != null) {
            user.setLastSeenAt(lastSeenAt);
        }
        return userRepository.save(user);
    }

    private String normalizePresenceStatus(String status) {
        if (isBlank(status)) {
            return "OFFLINE";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    public UserSummaryResponse uploadAvatar(String userId, org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "File is required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "User not found"));

        try {
            java.nio.file.Path uploadDir = java.nio.file.Paths.get("uploads", "avatars").toAbsolutePath().normalize();
            java.nio.file.Files.createDirectories(uploadDir);

            // Clean up old avatar files for this user
            try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(uploadDir)) {
                files.filter(p -> p.getFileName().toString().startsWith(userId + "_"))
                     .forEach(p -> {
                         try {
                             java.nio.file.Files.deleteIfExists(p);
                         } catch (java.io.IOException ignored) {
                             // Best-effort cleanup for previous avatar files.
                         }
                     });
            }

            String originalFilename = file.getOriginalFilename();
            if (!StringUtils.hasText(originalFilename)) {
                originalFilename = "avatar.jpg";
            }
            String extension = "";
            int dotIndex = originalFilename.lastIndexOf('.');
            if (dotIndex > 0) {
                extension = originalFilename.substring(dotIndex);
            }

            String storedFilename = userId + "_" + UUID.randomUUID().toString() + extension;
            java.nio.file.Path targetPath = uploadDir.resolve(storedFilename);

            java.nio.file.Files.copy(file.getInputStream(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            String avatarUrl = "/auth/users/" + userId + "/avatar";
            user.setAvatarUrl(avatarUrl);
            userRepository.save(user);

            return toSummary(user);
        } catch (java.io.IOException e) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to upload avatar");
        }
    }

    public org.springframework.core.io.Resource getAvatarImage(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "User not found"));

        try {
            java.nio.file.Path uploadDir = java.nio.file.Paths.get("uploads", "avatars").toAbsolutePath().normalize();
            if (!java.nio.file.Files.exists(uploadDir)) {
                 throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Avatar directory missing");
            }
            
            // Search for the file starting with userId_
            java.util.Optional<java.nio.file.Path> avatarFile;
            try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(uploadDir)) {
                avatarFile = files.filter(p -> p.getFileName().toString().startsWith(userId + "_")).findFirst();
            }

            if (avatarFile.isEmpty()) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Avatar file missing");
            }

            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(avatarFile.get().toUri());
            if (!resource.exists()) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Avatar file unreadable");
            }

            return resource;
        } catch (java.io.IOException e) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Could not load avatar");
        }
    }

    private String generateOtp() {
        return String.format("%06d", random.nextInt(OTP_BOUND));
    }
}
