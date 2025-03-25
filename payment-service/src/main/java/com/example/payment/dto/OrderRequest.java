package com.example.payment.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
    private String orderId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
} 