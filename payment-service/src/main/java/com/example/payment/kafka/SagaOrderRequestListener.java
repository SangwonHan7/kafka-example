package com.example.payment.kafka;

import com.example.payment.service.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.payment.dto.PaymentRequestWithSaga;
import com.example.payment.dto.OrderRequest;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@RequiredArgsConstructor
public class SagaOrderRequestListener {
    
    private final PaymentService paymentService;
    private static final Logger log = LoggerFactory.getLogger(SagaOrderRequestListener.class);
    
    /**
     * Saga ID가 포함된 결제 요청 처리
     */
    @KafkaListener(topics = "payment.request", groupId = "payment-saga-group", containerFactory = "sagaKafkaListenerContainerFactory")
    public void handleSagaOrderRequest(PaymentRequestWithSaga paymentRequest) {
        try {
            log.info("Saga 결제 요청 수신: orderId={}, sagaId={}", 
                    paymentRequest.getOrderId(), paymentRequest.getSagaId());
            
            // OrderRequest 객체 생성
            OrderRequest orderRequest = new OrderRequest(
                paymentRequest.getOrderId(),
                paymentRequest.getAmount(),
                paymentRequest.getCurrency(),
                paymentRequest.getPaymentMethod()
            );
            
            // Saga ID와 함께 결제 처리
            paymentService.processPaymentWithSaga(orderRequest, paymentRequest.getSagaId());
            
        } catch (Exception e) {
            log.error("Saga 결제 요청 처리 중 오류: error={}", e.getMessage(), e);
        }
    }
} 