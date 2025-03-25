package com.example.payment.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    private String orderId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
} 