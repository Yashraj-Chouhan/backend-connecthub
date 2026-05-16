package com.connecthub.messageservice.client;

import com.connecthub.messageservice.dto.UserSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "auth-service")
public interface AuthClient {

    String INTERNAL_TOPUP_HEADER = "X-ConnectHub-Topup-Secret";

    @PostMapping("/auth/users/{userId}/translation-credits/consume")
    UserSummaryResponse consumeTranslationCredit(@PathVariable("userId") String userId);

    @PostMapping("/auth/users/{userId}/translation-credits/top-up")
    UserSummaryResponse topUpTranslationCredits(@PathVariable("userId") String userId,
                                                @RequestHeader(INTERNAL_TOPUP_HEADER) String topupSecret,
                                                @RequestParam("credits") int credits);
}
