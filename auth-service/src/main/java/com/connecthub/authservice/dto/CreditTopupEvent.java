package com.connecthub.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreditTopupEvent {
    private String userId;
    private Integer credits;
    private String orderId;
    private String paymentId;
}
