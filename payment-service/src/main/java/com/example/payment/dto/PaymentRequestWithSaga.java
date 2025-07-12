package com.example.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestWithSaga {
    private String orderId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String sagaId;
} 