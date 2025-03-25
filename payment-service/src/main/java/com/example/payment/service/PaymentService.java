package com.example.payment.service;

import com.example.payment.domain.Payment;
import com.example.payment.dto.OrderRequest;
import com.example.payment.dto.PaymentResult;
import com.example.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {
    
    private final KafkaTemplate<String, PaymentResult> kafkaTemplate;
    private final PaymentRepository paymentRepository;
    private final String PAYMENT_RESULT_TOPIC = "payment.result";
    
    public PaymentResult processPayment(OrderRequest orderRequest) {
        try {
            // 결제 정보 저장
            Payment payment = new Payment();
            payment.setOrderId(orderRequest.getOrderId());
            payment.setAmount(orderRequest.getAmount());
            payment.setCurrency(orderRequest.getCurrency());
            payment.setPaymentMethod(orderRequest.getPaymentMethod());
            payment.setStatus("PROCESSING");
            paymentRepository.save(payment);
            
            // 실제 결제 처리 로직이 들어갈 자리
            // 예시로 항상 성공하는 것으로 처리
            payment.setStatus("COMPLETED");
            paymentRepository.save(payment);
            
            PaymentResult result = new PaymentResult(
                orderRequest.getOrderId(),
                "COMPLETED",
                "Payment processed successfully"
            );
            
            // 결제 결과를 order-service로 전송
            kafkaTemplate.send(PAYMENT_RESULT_TOPIC, result);
            
            return result;
            
        } catch (Exception e) {
            PaymentResult errorResult = new PaymentResult(
                orderRequest.getOrderId(),
                "ERROR",
                e.getMessage()
            );
            
            // 에러 결과도 order-service로 전송
            kafkaTemplate.send(PAYMENT_RESULT_TOPIC, errorResult);
            
            throw new RuntimeException("Payment processing failed", e);
        }
    }
} 