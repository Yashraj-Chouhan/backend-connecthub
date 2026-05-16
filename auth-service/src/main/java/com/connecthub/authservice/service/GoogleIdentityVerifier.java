package com.connecthub.authservice.service;

public interface GoogleIdentityVerifier {

    GoogleUserProfile verify(String idToken);
}
