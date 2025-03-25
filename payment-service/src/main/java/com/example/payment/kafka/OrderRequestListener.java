package com.example.payment.kafka;

import com.example.payment.dto.OrderRequest;
import com.example.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderRequestListener {
    
    private final PaymentService paymentService;
    
    @KafkaListener(topics = "payment.request", groupId = "payment-group")
    public void handleOrderRequest(OrderRequest orderRequest) {
        paymentService.processPayment(orderRequest);
    }
} 