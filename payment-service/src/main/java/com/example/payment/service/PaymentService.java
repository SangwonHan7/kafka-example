package com.example.payment.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.domain.Payment;
import com.example.payment.dto.OrderRequest;
import com.example.payment.dto.PaymentResult;
import com.example.payment.dto.PaymentCancelRequest;
import com.example.payment.dto.PaymentResultWithSaga;
import com.example.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class PaymentService {
    
    @Qualifier("paymentSagaKafkaTemplate")
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentRepository paymentRepository;
    private final String PAYMENT_RESULT_TOPIC = "payment.result";
    
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    
    /**
     * Saga ID를 포함한 결제 처리
     */
    @Transactional
    public PaymentResult processPaymentWithSaga(OrderRequest orderRequest, String sagaId) {
        Payment payment = null;
        
        try {
            log.info("결제 처리 시작: orderId={}, sagaId={}", orderRequest.getOrderId(), sagaId);
            
            // 1. 결제 정보 저장
            payment = createPayment(orderRequest, sagaId);
            
            // 2. 실제 결제 처리 (외부 결제 게이트웨이 호출)
            boolean paymentSuccess = processExternalPayment(payment);
            
            PaymentResult result;
            if (paymentSuccess) {
                // 결제 성공
                payment.setStatus("COMPLETED");
                paymentRepository.save(payment);
                
                result = new PaymentResult(
                    orderRequest.getOrderId(),
                    "COMPLETED",
                    "결제가 성공적으로 완료되었습니다"
                );
                
                log.info("결제 성공: orderId={}, sagaId={}", orderRequest.getOrderId(), sagaId);
            } else {
                // 결제 실패
                payment.setStatus("FAILED");
                payment.setFailureReason("외부 결제 게이트웨이 오류");
                paymentRepository.save(payment);
                
                result = new PaymentResult(
                    orderRequest.getOrderId(),
                    "FAILED",
                    "결제 처리에 실패했습니다"
                );
                
                log.warn("결제 실패: orderId={}, sagaId={}", orderRequest.getOrderId(), sagaId);
            }
            
            // 3. 결제 결과를 Saga 오케스트레이터로 전송
            sendPaymentResultWithSaga(result, sagaId);
            
            return result;
            
        } catch (Exception e) {
            log.error("결제 처리 중 오류 발생: orderId={}, sagaId={}, error={}", 
                     orderRequest.getOrderId(), sagaId, e.getMessage());
            
            // 오류 시 결제 상태 업데이트
            if (payment != null) {
                payment.setStatus("ERROR");
                payment.setFailureReason(e.getMessage());
                paymentRepository.save(payment);
            }
            
            PaymentResult errorResult = new PaymentResult(
                orderRequest.getOrderId(),
                "ERROR",
                "결제 처리 중 오류가 발생했습니다: " + e.getMessage()
            );
            
            // 오류 결과도 Saga 오케스트레이터로 전송
            sendPaymentResultWithSaga(errorResult, sagaId);
            
            return errorResult;
        }
    }
    
    /**
     * 보상 트랜잭션 - 결제 취소
     */
    @Transactional
    public void cancelPayment(PaymentCancelRequest cancelRequest) {
        try {
            log.info("결제 취소 요청 처리: orderId={}, sagaId={}, reason={}", 
                    cancelRequest.getOrderId(), cancelRequest.getSagaId(), cancelRequest.getReason());
            
            Payment payment = paymentRepository.findByOrderId(cancelRequest.getOrderId());
            
            if (payment != null && "COMPLETED".equals(payment.getStatus())) {
                // 실제 결제 취소 처리 (외부 결제 게이트웨이 호출)
                boolean cancelSuccess = processExternalCancel(payment);
                
                if (cancelSuccess) {
                    payment.setStatus("CANCELLED");
                    payment.setFailureReason(cancelRequest.getReason());
                    paymentRepository.save(payment);
                    
                    log.info("결제 취소 완료: orderId={}, sagaId={}", 
                            cancelRequest.getOrderId(), cancelRequest.getSagaId());
                } else {
                    log.error("결제 취소 실패: orderId={}, sagaId={}", 
                             cancelRequest.getOrderId(), cancelRequest.getSagaId());
                    
                    payment.setStatus("CANCEL_FAILED");
                    paymentRepository.save(payment);
                }
            } else {
                log.warn("취소할 결제를 찾을 수 없거나 이미 처리됨: orderId={}, status={}", 
                        cancelRequest.getOrderId(), payment != null ? payment.getStatus() : "NOT_FOUND");
            }
            
        } catch (Exception e) {
            log.error("결제 취소 처리 중 오류: orderId={}, sagaId={}, error={}", 
                     cancelRequest.getOrderId(), cancelRequest.getSagaId(), e.getMessage());
        }
    }
    
    /**
     * 결제 정보 생성
     */
    private Payment createPayment(OrderRequest orderRequest, String sagaId) {
        Payment payment = new Payment();
        payment.setOrderId(orderRequest.getOrderId());
        payment.setAmount(orderRequest.getAmount());
        payment.setCurrency(orderRequest.getCurrency());
        payment.setPaymentMethod(orderRequest.getPaymentMethod());
        payment.setSagaId(sagaId);  // Saga ID 저장
        payment.setStatus("PROCESSING");
        
        return paymentRepository.save(payment);
    }
    
    /**
     * 외부 결제 게이트웨이 처리 시뮬레이션
     */
    private boolean processExternalPayment(Payment payment) {
        try {
            // 실제로는 외부 결제 API 호출
            // 여기서는 시뮬레이션을 위해 간단한 로직 사용
            
            // 90% 확률로 성공
            double successRate = 0.9;
            boolean success = Math.random() < successRate;
            
            // 처리 시간 시뮬레이션 (1-3초)
            Thread.sleep(1000 + (long)(Math.random() * 2000));
            
            return success;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.error("외부 결제 처리 오류: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 외부 결제 취소 처리 시뮬레이션
     */
    private boolean processExternalCancel(Payment payment) {
        try {
            // 실제로는 외부 결제 취소 API 호출
            log.info("외부 결제 취소 API 호출: paymentId={}", payment.getId());
            
            // 95% 확률로 취소 성공
            return Math.random() < 0.95;
            
        } catch (Exception e) {
            log.error("외부 결제 취소 오류: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Saga ID와 함께 결제 결과 전송
     */
    private void sendPaymentResultWithSaga(PaymentResult result, String sagaId) {
        try {
            // Saga ID를 포함한 메시지 전송
            PaymentResultWithSaga sagaResult = new PaymentResultWithSaga(
                result.getOrderId(),
                result.getStatus(),
                result.getMessage(),
                sagaId
            );
            
            kafkaTemplate.send(PAYMENT_RESULT_TOPIC, sagaResult);
            log.info("결제 결과 전송 완료: orderId={}, status={}, sagaId={}", 
                    result.getOrderId(), result.getStatus(), sagaId);
            
        } catch (Exception e) {
            log.error("결제 결과 전송 실패: sagaId={}, error={}", sagaId, e.getMessage());
        }
    }
} 