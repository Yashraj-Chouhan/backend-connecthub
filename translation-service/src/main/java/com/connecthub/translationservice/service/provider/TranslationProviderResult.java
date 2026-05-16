package com.connecthub.translationservice.service.provider;

public record TranslationProviderResult(String correctedText,
                                        String translatedText,
                                        String sourceLanguage,
                                        String targetLanguage,
                                        String provider) {
}
