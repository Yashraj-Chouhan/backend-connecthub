package com.connecthub.authservice.service;

import com.connecthub.authservice.validation.ValidationSupport;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GoogleTokenVerifierClient implements GoogleIdentityVerifier {

    private final RestClient restClient;
    private final Set<String> allowedClientIds;
    private final String tokenInfoUrl;

    public GoogleTokenVerifierClient(
            RestClient.Builder restClientBuilder,
            @Value("${google.auth.allowed-client-ids:}") String allowedClientIdsValue,
            @Value("${google.auth.token-info-url:https://oauth2.googleapis.com/tokeninfo}") String tokenInfoUrl) {
        this.restClient = restClientBuilder.build();
        this.allowedClientIds = parseAllowedClientIds(allowedClientIdsValue);
        this.tokenInfoUrl = tokenInfoUrl;
    }

    @Override
    public GoogleUserProfile verify(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google ID token is required.");
        }
        if (allowedClientIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google sign-in is not configured.");
        }

        GoogleTokenInfoResponse tokenInfo = fetchTokenInfo(idToken.trim());

        if (!StringUtils.hasText(tokenInfo.sub())
                || !ValidationSupport.isValidEmail(tokenInfo.email())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Google sign-in failed. Please choose a different Google account and try again.");
        }
        if (!Boolean.parseBoolean(tokenInfo.emailVerified())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Your Google account email must be verified before you can sign in.");
        }
        if (!allowedClientIds.contains(tokenInfo.aud())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "This Google sign-in token is not meant for this app.");
        }

        return new GoogleUserProfile(
                tokenInfo.sub().trim(),
                ValidationSupport.normalizeEmail(tokenInfo.email()),
                normalizeOptionalText(tokenInfo.name()),
                normalizeOptionalText(tokenInfo.picture())
        );
    }

    private GoogleTokenInfoResponse fetchTokenInfo(String idToken) {
        URI uri = UriComponentsBuilder.fromUriString(tokenInfoUrl)
                .queryParam("id_token", idToken)
                .build(true)
                .toUri();

        try {
            GoogleTokenInfoResponse response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(GoogleTokenInfoResponse.class);
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Google sign-in failed. Please try again.");
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Google sign-in failed. Please try again.", ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Google sign-in is temporarily unavailable. Please try again.", ex);
        }
    }

    private Set<String> parseAllowedClientIds(String allowedClientIdsValue) {
        if (!StringUtils.hasText(allowedClientIdsValue)) {
            return Set.of();
        }
        return Arrays.stream(allowedClientIdsValue.split(","))
                .map(this::normalizeOptionalText)
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String normalizeOptionalText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record GoogleTokenInfoResponse(
            String aud,
            String sub,
            String email,
            @JsonProperty("email_verified") String emailVerified,
            String name,
            String picture
    ) {
    }
}
