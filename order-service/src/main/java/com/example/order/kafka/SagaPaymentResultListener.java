package com.example.order.kafka;

import com.example.order.dto.PaymentResultWithSaga;
import com.example.order.dto.PaymentResult;
import com.example.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@RequiredArgsConstructor
public class SagaPaymentResultListener {
    
    private final OrderService orderService;
    private static final Logger log = LoggerFactory.getLogger(SagaPaymentResultListener.class);
    
    @KafkaListener(
        topics = "payment.result", 
        groupId = "order-saga-group",
        containerFactory = "sagaResultKafkaListenerContainerFactory"
    )
    public void handleSagaPaymentResult(PaymentResultWithSaga result) {
        try {
            log.info("Saga 결제 결과 수신: orderId={}, status={}, sagaId={}", 
                    result.getOrderId(), result.getStatus(), result.getSagaId());
            
            // PaymentResult 객체 생성
            PaymentResult paymentResult = new PaymentResult(
                result.getOrderId(), 
                result.getStatus(), 
                result.getMessage()
            );
            
            // 결제 결과 처리
            orderService.handlePaymentResult(paymentResult, result.getSagaId());
        } catch (Exception e) {
            log.error("Saga 결제 결과 처리 중 오류: error={}", e.getMessage(), e);
        }
    }
} 