package com.example.order.controller;

import com.example.order.domain.SagaTransaction;
import com.example.order.dto.OrderRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.service.OrderService;
import com.example.order.service.SagaMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/saga")
@RequiredArgsConstructor
public class SagaController {
    
    private final OrderService orderService;
    private final SagaMonitoringService sagaMonitoringService;
    
    private static final Logger log = LoggerFactory.getLogger(SagaController.class);
    
    /**
     * 보상 트랜잭션을 포함한 주문 생성
     */
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrderWithSaga(@RequestBody OrderRequest orderRequest) {
        try {
            log.info("Saga 기반 주문 생성 요청: orderId={}", orderRequest.getOrderId());
            
            CompletableFuture<OrderResponse> futureResult = 
                orderService.createOrderWithSaga(orderRequest);
            
            // 31초 타임아웃으로 결과 대기
            OrderResponse result = futureResult.get(31, TimeUnit.SECONDS);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Saga 주문 생성 실패: {}", e.getMessage());
            OrderResponse errorResponse = new OrderResponse(
                orderRequest.getOrderId(), 
                "ERROR", 
                "주문 생성 실패: " + e.getMessage()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * 특정 주문의 Saga 상태 조회
     */
    @GetMapping("/orders/{orderId}/status")
    public ResponseEntity<SagaStatusResponse> getSagaStatus(@PathVariable String orderId) {
        try {
            SagaTransaction saga = sagaMonitoringService.getSagaByOrderId(orderId);
            
            if (saga == null) {
                return ResponseEntity.notFound().build();
            }
            
            SagaStatusResponse response = new SagaStatusResponse(
                saga.getSagaId(),
                saga.getOrderId(),
                saga.getCurrentStep(),
                saga.getStatus(),
                saga.getLastMessage(),
                saga.getStartedAt(),
                saga.getUpdatedAt()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Saga 상태 조회 실패: orderId={}, error={}", orderId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 모든 진행 중인 Saga 조회
     */
    @GetMapping("/transactions/in-progress")
    public ResponseEntity<List<SagaTransaction>> getInProgressSagas() {
        try {
            List<SagaTransaction> inProgressSagas = sagaMonitoringService.getInProgressSagas();
            return ResponseEntity.ok(inProgressSagas);
            
        } catch (Exception e) {
            log.error("진행 중인 Saga 조회 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 특정 Saga 수동 재시도
     */
    @PostMapping("/transactions/{sagaId}/retry")
    public ResponseEntity<String> retrySaga(@PathVariable String sagaId) {
        try {
            sagaMonitoringService.retrySaga(sagaId);
            return ResponseEntity.ok("Saga 재시도가 시작되었습니다: " + sagaId);
            
        } catch (Exception e) {
            log.error("Saga 재시도 실패: sagaId={}, error={}", sagaId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Saga 재시도 실패: " + e.getMessage());
        }
    }
    
    /**
     * Saga 상태 응답 DTO
     */
    public static class SagaStatusResponse {
        private String sagaId;
        private String orderId;
        private String currentStep;
        private String status;
        private String lastMessage;
        private java.time.LocalDateTime startedAt;
        private java.time.LocalDateTime updatedAt;
        
        public SagaStatusResponse(String sagaId, String orderId, String currentStep, 
                                String status, String lastMessage, 
                                java.time.LocalDateTime startedAt, 
                                java.time.LocalDateTime updatedAt) {
            this.sagaId = sagaId;
            this.orderId = orderId;
            this.currentStep = currentStep;
            this.status = status;
            this.lastMessage = lastMessage;
            this.startedAt = startedAt;
            this.updatedAt = updatedAt;
        }
        
        // getters and setters
        public String getSagaId() { return sagaId; }
        public void setSagaId(String sagaId) { this.sagaId = sagaId; }
        
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        
        public String getCurrentStep() { return currentStep; }
        public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getLastMessage() { return lastMessage; }
        public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
        
        public java.time.LocalDateTime getStartedAt() { return startedAt; }
        public void setStartedAt(java.time.LocalDateTime startedAt) { this.startedAt = startedAt; }
        
        public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
} 