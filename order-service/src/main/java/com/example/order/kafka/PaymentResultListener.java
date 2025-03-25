package com.example.order.kafka;

import com.example.order.dto.PaymentResult;
import com.example.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentResultListener {
    
    private final OrderService orderService;
    
    @KafkaListener(topics = "payment.result", groupId = "order-group")
    public void handlePaymentResult(PaymentResult result) {
        orderService.updateOrderStatus(result.getOrderId(), result.getStatus(), result.getMessage());
    }
} 