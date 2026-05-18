package com.connecthub.authservice.controller;

import com.connecthub.authservice.config.SecurityConfig;
import com.connecthub.authservice.dto.AuthLoginResponse;
import com.connecthub.authservice.dto.ChangePasswordRequest;
import com.connecthub.authservice.dto.CompleteRegistrationRequest;
import com.connecthub.authservice.dto.ForgotPasswordRequest;
import com.connecthub.authservice.dto.GoogleLoginRequest;
import com.connecthub.authservice.dto.InitiateRegistrationRequest;
import com.connecthub.authservice.dto.LoginRequest;
import com.connecthub.authservice.dto.RegisterRequest;
import com.connecthub.authservice.dto.ResetPasswordRequest;
import com.connecthub.authservice.dto.UpdateProfileRequest;
import com.connecthub.authservice.dto.UserSummaryResponse;
import com.connecthub.authservice.dto.VerifyOtpRequest;
import com.connecthub.authservice.dto.VerifyOtpResponse;
import com.connecthub.authservice.exception.GlobalExceptionHandler;
import com.connecthub.authservice.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthController authController;

    @MockBean
    private AuthService authService;

    @Test
    void registerReturnsCreatedMessage() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Test User",
                                  "email": "user@example.com",
                                  "phoneNumber": "9876543210",
                                  "username": "tester",
                                  "password": "Password123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Account created successfully! Please sign in."));

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void initiateSignupReturnsAcceptedMessage() throws Exception {
        mockMvc.perform(post("/auth/register/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Test User",
                                  "email": "user@example.com",
                                  "phoneNumber": "9876543210",
                                  "username": "tester",
                                  "password": "Password123"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("A verification code has been sent to user@example.com. It expires in 10 minutes."));

        verify(authService).initiateRegistration(any(InitiateRegistrationRequest.class));
    }

    @Test
    void completeSignupReturnsCreatedMessage() throws Exception {
        mockMvc.perform(post("/auth/register/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "otp": "123456"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Account created successfully! You can now sign in."));

        verify(authService).completeRegistration(any(CompleteRegistrationRequest.class));
    }

    @Test
    void loginReturnsTokenPayload() throws Exception {
        AuthLoginResponse response = new AuthLoginResponse(
                "jwt-token",
                "user-1",
                "user@example.com",
                "9876543210",
                "tester",
                "english",
                10,
                "USER",
                null
        );
        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "user@example.com",
                                  "password": "Password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.userId").value("user-1"));
    }

    @Test
    void googleLoginReturnsTokenPayload() throws Exception {
        AuthLoginResponse response = new AuthLoginResponse(
                "google-jwt-token",
                "user-2",
                "google@example.com",
                null,
                "googleuser",
                "en",
                50,
                "USER",
                null
        );
        when(authService.loginWithGoogle(any(GoogleLoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/auth/login/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": "google-id-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("google-jwt-token"))
                .andExpect(jsonPath("$.userId").value("user-2"));
    }

    @Test
    void passwordFlowsReturnExpectedMessages() throws Exception {
        when(authService.forgotPassword(any(ForgotPasswordRequest.class))).thenReturn("OTP sent");
        when(authService.verifyOtp(any(VerifyOtpRequest.class))).thenReturn(new VerifyOtpResponse("reset-token", "verified"));

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "user@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("OTP sent"));

        mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "user@example.com",
                                  "otp": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken").value("reset-token"));

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "reset-token",
                                  "newPassword": "Password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated successfully"));

        verify(authService).resetPassword(any(ResetPasswordRequest.class));
    }

    @Test
    void userEndpointsDelegateToService() throws Exception {
        UserSummaryResponse response = new UserSummaryResponse(
                "user-1",
                "tester",
                "Test User",
                "user@example.com",
                "9876543210",
                null,
                null,
                "english",
                10,
                "ONLINE",
                null,
                "USER",
                false
        );
        when(authService.getUserById("user-1")).thenReturn(response);
        when(authService.getUserByEmail("user@example.com")).thenReturn(response);
        when(authService.searchUsers("te")).thenReturn(List.of(response));
        when(authService.updateProfile(eq("user-1"), any(UpdateProfileRequest.class))).thenReturn(response);
        when(authService.updateStatus("user-1", "ONLINE")).thenReturn(response);
        when(authService.consumeTranslationCredit("user-1")).thenReturn(response);
        when(authService.topUpTranslationCredits("user-1", 5)).thenReturn(response);
        when(authService.topUpTranslationCredits("user-1", 5, "order-1", "pay-1", "PAYMENT_SERVICE_HTTP"))
                .thenReturn(response);

        mockMvc.perform(get("/auth/users/user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-1"));

        mockMvc.perform(get("/auth/users/by-email").param("email", "user@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"));

        mockMvc.perform(get("/auth/users/search").param("query", "te"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("tester"));

        mockMvc.perform(put("/auth/users/user-1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Updated User",
                                  "email": "user@example.com",
                                  "phoneNumber": "9876543210",
                                  "username": "tester",
                                  "preferredLanguage": "english"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Test User"));

        mockMvc.perform(put("/auth/users/user-1/status").param("status", "ONLINE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onlineStatus").value("ONLINE"));

        mockMvc.perform(post("/auth/users/user-1/translation-credits/consume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translationCreditsRemaining").value(10));

        ReflectionTestUtils.setField(authController, "internalTopupSecret", "test-secret");

        mockMvc.perform(post("/auth/users/user-1/translation-credits/top-up")
                        .header("X-ConnectHub-Topup-Secret", "test-secret")
                        .param("credits", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-1"));

        mockMvc.perform(post("/auth/users/user-1/translation-credits/top-up")
                        .header("X-ConnectHub-Topup-Secret", "test-secret")
                        .param("credits", "5")
                        .param("orderId", "order-1")
                        .param("paymentId", "pay-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-1"));
    }

    @Test
    void avatarAndPasswordEndpointsReturnExpectedResponses() throws Exception {
        UserSummaryResponse response = new UserSummaryResponse(
                "user-1",
                "tester",
                "Test User",
                "user@example.com",
                null,
                null,
                null,
                "english",
                10,
                "ONLINE",
                null,
                "USER",
                false
        );
        when(authService.uploadAvatar(eq("user-1"), any())).thenReturn(response);
        when(authService.getAvatarImage("user-1")).thenReturn(new ByteArrayResource("img".getBytes()));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "img".getBytes()
        );

        mockMvc.perform(multipart("/auth/users/user-1/avatar").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-1"));

        mockMvc.perform(get("/auth/users/user-1/avatar"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=\"avatar.jpg\""))
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));

        mockMvc.perform(put("/auth/users/user-1/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "OldPassword123",
                                  "newPassword": "NewPassword123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));

        verify(authService).changePassword(eq("user-1"), any(ChangePasswordRequest.class));
    }
}
