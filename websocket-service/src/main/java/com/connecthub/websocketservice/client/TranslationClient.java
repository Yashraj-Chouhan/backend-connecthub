package com.connecthub.websocketservice.client;

import com.connecthub.websocketservice.dto.TranslationRequest;
import com.connecthub.websocketservice.dto.TranslationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "translation-service")
public interface TranslationClient {

    @PostMapping("/translate")
    TranslationResponse translate(@RequestBody TranslationRequest request);
}
