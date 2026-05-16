package com.connecthub.translationservice.service.provider;

/**
 * Normalized translation request shared by the remote providers.
 */
public record TranslationJob(String originalText,
                             String sourceLanguage,
                             String targetLanguage,
                             String sourceLanguageHint) {
}
