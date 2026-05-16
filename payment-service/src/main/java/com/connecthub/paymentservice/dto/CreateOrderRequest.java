package com.connecthub.paymentservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateOrderRequest {
    @NotBlank(message = "User ID is required")
    private String userId;

    @Size(max = 64, message = "Plan code cannot exceed 64 characters")
    private String planCode;

    @Min(value = 1, message = "Credits must be at least 1")
    private Integer credits;

    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private Double amount;

    @Size(max = 100, message = "Customer name cannot exceed 100 characters")
    private String customerName;

    @Email(message = "Invalid email format")
    private String customerEmail;
}
