package com.example.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResultWithSaga {
    private String orderId;
    private String status;
    private String message;
    private String sagaId;
} 