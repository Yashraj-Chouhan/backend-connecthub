package com.connecthub.authservice.controller;

import com.connecthub.authservice.config.SecurityConfig;
import com.connecthub.authservice.exception.GlobalExceptionHandler;
import com.connecthub.authservice.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Test
    void registerReturnsFriendlyValidationErrors() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "bad-email",
                                  "phoneNumber": "12345",
                                  "password": "123",
                                  "username": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please correct the highlighted fields and try again."))
                .andExpect(jsonPath("$.errors.email").value("Please enter a valid email address."))
                .andExpect(jsonPath("$.errors.phoneNumber").value("Please enter a valid mobile number."))
                .andExpect(jsonPath("$.errors.password").value("Password must be at least 8 characters long and include at least one letter and one number."))
                .andExpect(jsonPath("$.errors.username").value("Please choose a username."));
    }

    @Test
    void googleLoginRequiresIdToken() throws Exception {
        mockMvc.perform(post("/auth/login/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Google ID token is required."))
                .andExpect(jsonPath("$.errors.idToken").value("Google ID token is required."));
    }
}
