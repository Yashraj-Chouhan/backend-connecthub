package com.connecthub.authservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleTokenVerifierClientTest {

    @Test
    void verifyAcceptsVerifiedTokenForAllowedClientId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleTokenVerifierClient verifier = new GoogleTokenVerifierClient(
                builder,
                "client-1.apps.googleusercontent.com",
                "https://oauth2.googleapis.com/tokeninfo");

        server.expect(requestTo("https://oauth2.googleapis.com/tokeninfo?id_token=google-id-token"))
                .andRespond(withSuccess("""
                        {
                          "aud": "client-1.apps.googleusercontent.com",
                          "sub": "google-subject-1",
                          "email": "user@example.com",
                          "email_verified": "true",
                          "name": "Google User",
                          "picture": "https://example.com/avatar.jpg"
                        }
                        """, MediaType.APPLICATION_JSON));

        GoogleUserProfile profile = verifier.verify("google-id-token");

        assertEquals("google-subject-1", profile.subject());
        assertEquals("user@example.com", profile.email());
        assertEquals("Google User", profile.fullName());
        assertEquals("https://example.com/avatar.jpg", profile.pictureUrl());
    }

    @Test
    void verifyRejectsUnverifiedEmail() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleTokenVerifierClient verifier = new GoogleTokenVerifierClient(
                builder,
                "client-1.apps.googleusercontent.com",
                "https://oauth2.googleapis.com/tokeninfo");

        server.expect(requestTo("https://oauth2.googleapis.com/tokeninfo?id_token=google-id-token"))
                .andRespond(withSuccess("""
                        {
                          "aud": "client-1.apps.googleusercontent.com",
                          "sub": "google-subject-1",
                          "email": "user@example.com",
                          "email_verified": "false",
                          "name": "Google User"
                        }
                        """, MediaType.APPLICATION_JSON));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> verifier.verify("google-id-token"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void verifyRejectsUnexpectedAudience() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleTokenVerifierClient verifier = new GoogleTokenVerifierClient(
                builder,
                "client-1.apps.googleusercontent.com",
                "https://oauth2.googleapis.com/tokeninfo");

        server.expect(requestTo("https://oauth2.googleapis.com/tokeninfo?id_token=google-id-token"))
                .andRespond(withSuccess("""
                        {
                          "aud": "client-2.apps.googleusercontent.com",
                          "sub": "google-subject-1",
                          "email": "user@example.com",
                          "email_verified": "true",
                          "name": "Google User"
                        }
                        """, MediaType.APPLICATION_JSON));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> verifier.verify("google-id-token"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void verifyFailsClosedWhenClientIdsAreNotConfigured() {
        GoogleTokenVerifierClient verifier = new GoogleTokenVerifierClient(
                RestClient.builder(),
                "",
                "https://oauth2.googleapis.com/tokeninfo");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> verifier.verify("google-id-token"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }
}
