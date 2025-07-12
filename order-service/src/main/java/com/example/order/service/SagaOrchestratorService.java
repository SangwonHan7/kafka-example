package com.example.order.service;

import com.example.order.dto.OrderRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.domain.Order;
import com.example.order.domain.SagaTransaction;
import com.example.order.repository.OrderRepository;
import com.example.order.repository.SagaTransactionRepository;
import com.example.order.dto.PaymentRequestWithSaga;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SagaOrchestratorService {
    
    private final OrderRepository orderRepository;
    private final SagaTransactionRepository sagaTransactionRepository;
    
    @Qualifier("sagaKafkaTemplate")
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final Logger log = LoggerFactory.getLogger(SagaOrchestratorService.class);
    
    /**
     * Saga 트랜잭션 시작 - 주문 생성부터 결제 완료까지의 전체 플로우를 관리
     */
    @Transactional
    public String startOrderPaymentSaga(OrderRequest request) {
        // 1. 주문 ID 생성 (없는 경우)
        String orderId = request.getOrderId() != null ? request.getOrderId() : UUID.randomUUID().toString();
        request.setOrderId(orderId); // 명시적으로 OrderRequest에 설정
        
        // 2. Saga 트랜잭션 기록 생성
        SagaTransaction sagaTransaction = createSagaTransaction(request);
        
        try {
            // 3. 주문 생성 (첫 번째 단계)
            Order order = createOrder(request, sagaTransaction.getSagaId());
            
            // 로깅 추가
            log.info("주문 생성: orderId={}, sagaId={}", order.getOrderId(), sagaTransaction.getSagaId());
            
            // 4. Saga 상태 업데이트
            updateSagaStep(sagaTransaction, "ORDER_CREATED", "주문이 생성되었습니다.");
            
            // 5. 결제 요청 전송 (두 번째 단계)
            sendPaymentRequest(request, sagaTransaction.getSagaId());
            updateSagaStep(sagaTransaction, "PAYMENT_REQUESTED", "결제 요청이 전송되었습니다.");
            
            return sagaTransaction.getSagaId();
            
        } catch (Exception e) {
            // 실패 시 즉시 보상 트랜잭션 실행
            compensateSaga(sagaTransaction, e.getMessage());
            throw new RuntimeException("주문 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    /**
     * 결제 결과 처리
     */
    @Transactional
    public void handlePaymentResult(String sagaId, String status, String message) {
        log.info("결제 결과 처리: sagaId={}, status={}, message={}", sagaId, status, message);
        
        SagaTransaction sagaTransaction = sagaTransactionRepository.findBySagaId(sagaId);
        
        if (sagaTransaction == null) {
            log.error("Saga transaction not found: {}", sagaId);
            return;
        }
        
        if ("COMPLETED".equals(status) || "SUCCESS".equals(status)) {
            // 결제 성공 - Saga 완료
            completeSaga(sagaTransaction);
        } else {
            // 결제 실패 - 보상 트랜잭션 실행
            compensateSaga(sagaTransaction, message);
        }
    }
    
    /**
     * 보상 트랜잭션 실행 (역순으로 작업 취소)
     */
    @Transactional
    public void compensateSaga(SagaTransaction sagaTransaction, String reason) {
        log.info("보상 트랜잭션 시작: sagaId={}, orderId={}, reason={}", 
                sagaTransaction.getSagaId(), sagaTransaction.getOrderId(), reason);
        
        try {
            // 현재 단계에 따라 보상 작업 수행
            String currentStep = sagaTransaction.getCurrentStep();
            
            switch (currentStep) {
                case "PAYMENT_REQUESTED":
                case "PAYMENT_PROCESSING":
                    // 결제 취소 요청 (필요시)
                    cancelPaymentIfNeeded(sagaTransaction);
                    // fall through to order cancellation
                    
                case "ORDER_CREATED":
                    // 주문 취소
                    cancelOrder(sagaTransaction, reason);
                    break;
                    
                default:
                    log.warn("알 수 없는 Saga 단계: {}", currentStep);
            }
            
            // Saga 상태를 COMPENSATED로 변경
            updateSagaStep(sagaTransaction, "COMPENSATED", 
                         "보상 트랜잭션이 완료되었습니다. 사유: " + reason);
            
        } catch (Exception e) {
            log.error("보상 트랜잭션 실패: sagaId={}, error={}", 
                     sagaTransaction.getSagaId(), e.getMessage());
            updateSagaStep(sagaTransaction, "COMPENSATION_FAILED", 
                         "보상 트랜잭션 실패: " + e.getMessage());
        }
    }
    
    /**
     * Saga 완료 처리
     */
    private void completeSaga(SagaTransaction sagaTransaction) {
        log.info("Saga 완료 처리 시작: sagaId={}, orderId={}", 
               sagaTransaction.getSagaId(), sagaTransaction.getOrderId());
        
        // SagaId로 주문 찾기 (주문ID로 찾기가 실패할 경우 대비)
        Order order = orderRepository.findByOrderId(sagaTransaction.getOrderId());
        
        // 첫 번째 방법으로 못 찾으면 SagaId로 찾기 시도
        if (order == null) {
            List<Order> ordersBySagaId = orderRepository.findBySagaId(sagaTransaction.getSagaId());
            if (!ordersBySagaId.isEmpty()) {
                order = ordersBySagaId.get(0);
                log.info("SagaId로 주문 찾음: sagaId={}, orderId={}", 
                       sagaTransaction.getSagaId(), order.getOrderId());
            }
        }
        
        if (order != null) {
            order.setStatus("COMPLETED");
            orderRepository.save(order);
            log.info("주문 상태 업데이트 완료: orderId={}, status=COMPLETED", order.getOrderId());
        } else {
            log.error("주문을 찾을 수 없어 상태 업데이트 실패: orderId={}, sagaId={}", 
                    sagaTransaction.getOrderId(), sagaTransaction.getSagaId());
        }
        
        updateSagaStep(sagaTransaction, "COMPLETED", "주문과 결제가 모두 성공적으로 완료되었습니다.");
        log.info("Saga 완료: sagaId={}, orderId={}", 
                sagaTransaction.getSagaId(), sagaTransaction.getOrderId());
    }
    
    /**
     * 주문 생성
     */
    private Order createOrder(OrderRequest request, String sagaId) {
        Order order = new Order();
        order.setOrderId(request.getOrderId());  // 반드시 request의 orderId 사용
        order.setAmount(request.getAmount());
        order.setStatus("PENDING");
        order.setSagaId(sagaId);  // Saga ID 연결
        
        Order savedOrder = orderRepository.save(order);
        log.info("주문 저장 완료: orderId={}, sagaId={}", savedOrder.getOrderId(), sagaId);
        
        return savedOrder;
    }
    
    /**
     * 결제 요청 전송
     */
    private void sendPaymentRequest(OrderRequest request, String sagaId) {
        // Saga ID를 포함한 결제 요청 객체 생성
        PaymentRequestWithSaga paymentRequest = new PaymentRequestWithSaga(
            request.getOrderId(),
            request.getAmount(),
            request.getCurrency(),
            request.getPaymentMethod(),
            sagaId
        );
        
        // 결제 요청 전송
        kafkaTemplate.send("payment.request", paymentRequest);
        log.info("결제 요청 전송 완료: orderId={}, sagaId={}", request.getOrderId(), sagaId);
    }
    
    /**
     * 주문 취소
     */
    private void cancelOrder(SagaTransaction sagaTransaction, String reason) {
        // 주문ID로 찾기
        Order order = orderRepository.findByOrderId(sagaTransaction.getOrderId());
        
        // 못 찾으면 SagaId로 찾기 시도
        if (order == null) {
            List<Order> ordersBySagaId = orderRepository.findBySagaId(sagaTransaction.getSagaId());
            if (!ordersBySagaId.isEmpty()) {
                order = ordersBySagaId.get(0);
            }
        }
        
        if (order != null) {
            order.setStatus("CANCELLED");
            order.setFailureReason(reason);
            orderRepository.save(order);
            
            log.info("주문 취소 완료: orderId={}, reason={}", order.getOrderId(), reason);
        } else {
            log.error("취소할 주문을 찾을 수 없음: orderId={}, sagaId={}", 
                    sagaTransaction.getOrderId(), sagaTransaction.getSagaId());
        }
    }
    
    /**
     * 결제 취소 (필요시)
     */
    private void cancelPaymentIfNeeded(SagaTransaction sagaTransaction) {
        // 결제 서비스에 취소 요청 전송
        Map<String, Object> cancelRequest = new HashMap<>();
        cancelRequest.put("orderId", sagaTransaction.getOrderId());
        cancelRequest.put("sagaId", sagaTransaction.getSagaId());
        cancelRequest.put("reason", "Order compensation required");
        
        kafkaTemplate.send("payment.cancel", cancelRequest);
        log.info("결제 취소 요청 전송: sagaId={}", sagaTransaction.getSagaId());
    }
    
    /**
     * Saga 트랜잭션 생성
     */
    private SagaTransaction createSagaTransaction(OrderRequest request) {
        SagaTransaction saga = new SagaTransaction();
        String sagaId = UUID.randomUUID().toString();
        saga.setSagaId(sagaId);
        
        // OrderRequest에서 orderId 사용
        saga.setOrderId(request.getOrderId());  // 주문 ID 정확하게 설정
        saga.setAmount(request.getAmount());
        saga.setCurrentStep("STARTED");
        saga.setStatus("IN_PROGRESS");
        saga.setStartedAt(LocalDateTime.now());
        
        SagaTransaction savedSaga = sagaTransactionRepository.save(saga);
        log.info("Saga 트랜잭션 생성 완료: sagaId={}, orderId={}", 
                savedSaga.getSagaId(), savedSaga.getOrderId());
        
        return savedSaga;
    }
    
    /**
     * Saga 단계 업데이트
     */
    private void updateSagaStep(SagaTransaction saga, String step, String message) {
        saga.setCurrentStep(step);
        saga.setLastMessage(message);
        saga.setUpdatedAt(LocalDateTime.now());
        
        if ("COMPLETED".equals(step) || "COMPENSATED".equals(step) || "COMPENSATION_FAILED".equals(step)) {
            saga.setStatus("FINISHED");
            saga.setFinishedAt(LocalDateTime.now());
        }
        
        sagaTransactionRepository.save(saga);
    }
} 