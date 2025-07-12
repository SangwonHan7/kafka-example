package com.example.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResultWithSaga extends PaymentResult {
    private String sagaId;
    
    public PaymentResultWithSaga(String orderId, String status, String message, String sagaId) {
        super(orderId, status, message);
        this.sagaId = sagaId;
    }
} 