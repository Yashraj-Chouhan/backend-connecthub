package com.connecthub.translationservice.service.provider;

import java.util.Optional;

public interface TranslationProvider {

    Optional<TranslationProviderResult> translate(TranslationJob job);
}
