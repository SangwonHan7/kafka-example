package com.example.payment.kafka;

import com.example.payment.dto.PaymentCancelRequest;
import com.example.payment.service.PaymentService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentCancelListener {
    
    private final PaymentService paymentService;
    private static final Logger log = LoggerFactory.getLogger(PaymentCancelListener.class);
    
    /**
     * 결제 취소 요청 처리 (Map 형태로 수신)
     */
    @KafkaListener(topics = "payment.cancel", groupId = "payment-cancel-group")
    public void handlePaymentCancelRequest(Map<String, Object> cancelRequestMap) {
        try {
            // Map에서 필요한 정보 추출
            String orderId = (String) cancelRequestMap.get("orderId");
            String sagaId = (String) cancelRequestMap.get("sagaId");
            String reason = (String) cancelRequestMap.get("reason");
            
            log.info("결제 취소 요청 수신 (Map): orderId={}, sagaId={}, reason={}", 
                    orderId, sagaId, reason);
            
            // PaymentCancelRequest 객체 생성
            PaymentCancelRequest cancelRequest = new PaymentCancelRequest(
                orderId,
                sagaId,
                reason != null ? reason : "Order compensation required"
            );

            paymentService.cancelPayment(cancelRequest);
            
        } catch (Exception e) {
            log.error("결제 취소 요청 처리 중 오류: error={}", e.getMessage(), e);
        }
    }
} 