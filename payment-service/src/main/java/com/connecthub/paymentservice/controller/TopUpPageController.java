package com.connecthub.paymentservice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
/**
 * Forwards friendly payment entry URLs to the packaged top-up page.
 */
public class TopUpPageController {

    @GetMapping({"/", "/payments", "/payments/", "/payments/topup"})
    public String topUpPage() {
        return "forward:/payments/index.html";
    }
}
