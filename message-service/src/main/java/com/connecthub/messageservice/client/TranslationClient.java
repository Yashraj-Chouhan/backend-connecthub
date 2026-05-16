package com.connecthub.messageservice.client;

import com.connecthub.messageservice.dto.TranslationRequest;
import com.connecthub.messageservice.dto.TranslationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "translation-service")
public interface TranslationClient {

    @PostMapping("/translate")
    TranslationResponse translate(@RequestBody TranslationRequest request);
}
