package com.connecthub.authservice.controller;

import com.connecthub.authservice.dto.*;
import com.connecthub.authservice.service.AuthService;
import com.connecthub.authservice.validation.ValidEmail;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Map;

@RestController
/**
 * Exposes the public and authenticated HTTP endpoints for account lifecycle,
 * profile management, and translation credit operations.
 */
@RequestMapping("/auth")
@Validated
@RequiredArgsConstructor
public class AuthController {

    private static final String INTERNAL_TOPUP_HEADER = "X-ConnectHub-Topup-Secret";

    private final AuthService authService;

    @Value("${app.internal-topup-secret:connecthub-topup-secret-change-me}")
    private String internalTopupSecret;

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Account created successfully! Please sign in."));
    }

    /**
     * Step 1 of OTP-gated signup: validate details and send a 6-digit OTP to the email.
     */
    @PostMapping("/register/initiate")
    public ResponseEntity<Map<String, String>> initiateSignup(@Valid @RequestBody InitiateRegistrationRequest request) {
        authService.initiateRegistration(request);
        return ResponseEntity.accepted()
                .body(Map.of("message", "A verification code has been sent to " + request.getEmail() + ". It expires in 10 minutes."));
    }

    /**
     * Step 2 of OTP-gated signup: confirm OTP and create the actual user account.
     */
    @PostMapping("/register/complete")
    public ResponseEntity<Map<String, String>> completeSignup(@Valid @RequestBody CompleteRegistrationRequest request) {
        authService.completeRegistration(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Account created successfully! You can now sign in."));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthLoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthLoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login/google")
    public ResponseEntity<AuthLoginResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        AuthLoginResponse response = authService.loginWithGoogle(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String msg = authService.forgotPassword(request);
        return ResponseEntity.ok(Map.of("message", msg));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<VerifyOtpResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        VerifyOtpResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserSummaryResponse> getUserById(@PathVariable String userId) {
        return ResponseEntity.ok(authService.getUserById(userId));
    }

    @GetMapping("/users/by-email")
    public ResponseEntity<UserSummaryResponse> getUserByEmail(@RequestParam @NotBlank(message = "Please enter an email address.")
                                                                      @ValidEmail
                                                                      String email) {
        return ResponseEntity.ok(authService.getUserByEmail(email));
    }

    @GetMapping("/users/search")
    public ResponseEntity<List<UserSummaryResponse>> searchUsers(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(authService.searchUsers(query));
    }

    @PutMapping("/users/{userId}/profile")
    public ResponseEntity<UserSummaryResponse> updateProfile(@PathVariable String userId,
                                                             @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(authService.updateProfile(userId, request));
    }

    @PostMapping(value = "/users/{userId}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserSummaryResponse> uploadAvatar(@PathVariable String userId,
                                                            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(authService.uploadAvatar(userId, file));
    }

    @GetMapping("/users/{userId}/avatar")
    public ResponseEntity<Resource> getAvatarImage(@PathVariable String userId) {
        Resource resource = authService.getAvatarImage(userId);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"avatar.jpg\"")
                .body(resource);
    }

    @PutMapping("/users/{userId}/password")
    public ResponseEntity<Map<String, String>> changePassword(@PathVariable String userId,
                                                              @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userId, request);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    @PutMapping("/users/{userId}/status")
    public ResponseEntity<UserSummaryResponse> updateStatus(@PathVariable String userId,
                                                            @RequestParam String status) {
        return ResponseEntity.ok(authService.updateStatus(userId, status));
    }

    @PostMapping("/users/{userId}/translation-credits/consume")
    public ResponseEntity<UserSummaryResponse> consumeTranslationCredit(@PathVariable String userId) {
        return ResponseEntity.ok(authService.consumeTranslationCredit(userId));
    }

    @PostMapping("/users/{userId}/translation-credits/top-up")
    public ResponseEntity<UserSummaryResponse> topUpTranslationCredits(@PathVariable String userId,
                                                                       @RequestHeader(value = INTERNAL_TOPUP_HEADER, required = false)
                                                                       String topupSecret,
                                                                       @RequestParam(required = false) String orderId,
                                                                       @RequestParam(required = false) String paymentId,
                                                                       @RequestParam @Min(value = 1, message = "Please add at least 1 credit.")
                                                                       int credits) {
        verifyInternalTopUpSecret(topupSecret);
        if (StringUtils.hasText(orderId) || StringUtils.hasText(paymentId)) {
            return ResponseEntity.ok(authService.topUpTranslationCredits(
                    userId,
                    credits,
                    orderId,
                    paymentId,
                    "PAYMENT_SERVICE_HTTP"));
        }
        return ResponseEntity.ok(authService.topUpTranslationCredits(userId, credits));
    }

    private void verifyInternalTopUpSecret(String providedSecret) {
        if (!StringUtils.hasText(internalTopupSecret)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Top-up secret is not configured");
        }

        if (!StringUtils.hasText(providedSecret) || !internalTopupSecret.equals(providedSecret.trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
        }
    }
}
