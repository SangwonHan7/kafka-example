package com.example.order.service;

import com.example.order.domain.SagaTransaction;
import com.example.order.repository.SagaTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class SagaMonitoringService {
    
    private final SagaTransactionRepository sagaTransactionRepository;
    private final SagaOrchestratorService sagaOrchestratorService;
    
    private static final Logger log = LoggerFactory.getLogger(SagaMonitoringService.class);
    
    /**
     * 주기적으로 타임아웃된 Saga 트랜잭션 정리
     */
    @Scheduled(fixedRate = 30000)
    @Transactional
    public void cleanupTimeoutTransactions() {
        try {
            // 1분 이상 된 IN_PROGRESS 상태의 트랜잭션 조회
            LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(1);
            List<SagaTransaction> timeoutTransactions = 
                sagaTransactionRepository.findTimeoutTransactions(timeoutThreshold);
            
            if (!timeoutTransactions.isEmpty()) {
                log.info("타임아웃된 Saga 트랜잭션 {} 개 발견", timeoutTransactions.size());
                
                for (SagaTransaction saga : timeoutTransactions) {
                    try {
                        log.warn("타임아웃 Saga 보상 처리: sagaId={}, orderId={}, duration={}분", 
                                saga.getSagaId(), 
                                saga.getOrderId(),
                                java.time.Duration.between(saga.getStartedAt(), LocalDateTime.now()).toMinutes());
                        
                        sagaOrchestratorService.compensateSaga(saga, "처리 시간 초과 (1분)");
                        
                    } catch (Exception e) {
                        log.error("타임아웃 Saga 보상 처리 실패: sagaId={}, error={}", 
                                 saga.getSagaId(), e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("타임아웃 트랜잭션 정리 중 오류 발생: {}", e.getMessage());
        }
    }
    
    /**
     * Saga 상태 통계 조회 (매시간 실행)
     */
    @Scheduled(fixedRate = 3600000) // 1시간 = 3,600,000 밀리초
    public void logSagaStatistics() {
        try {
            // 진행 중인 트랜잭션 수
            List<SagaTransaction> inProgressSagas = 
                sagaTransactionRepository.findByCurrentStepAndStatus("*", "IN_PROGRESS");
            
            // 최근 1시간 내 완료된 트랜잭션 수
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);

            log.info("=== Saga 트랜잭션 상태 통계 ===");
            log.info("진행 중인 Saga: {} 개", inProgressSagas.size());
            log.info("==============================");
            
            // 각 단계별 상태 로깅
            logStepStatistics("ORDER_CREATED");
            logStepStatistics("PAYMENT_REQUESTED");
            logStepStatistics("COMPLETED");
            logStepStatistics("COMPENSATED");
            
        } catch (Exception e) {
            log.error("Saga 통계 조회 중 오류 발생: {}", e.getMessage());
        }
    }
    
    /**
     * 특정 단계의 통계 로깅
     */
    private void logStepStatistics(String step) {
        try {
            List<SagaTransaction> stepSagas = 
                sagaTransactionRepository.findByCurrentStepAndStatus(step, "IN_PROGRESS");
            
            if (!stepSagas.isEmpty()) {
                log.info("{} 상태의 Saga: {} 개", step, stepSagas.size());
            }
            
        } catch (Exception e) {
            log.error("단계 통계 조회 실패: step={}, error={}", step, e.getMessage());
        }
    }
    
    /**
     * 수동으로 특정 Saga 재시도
     */
    @Transactional
    public void retrySaga(String sagaId) {
        try {
            SagaTransaction saga = sagaTransactionRepository.findBySagaId(sagaId);
            
            if (saga == null) {
                log.error("Saga를 찾을 수 없음: sagaId={}", sagaId);
                return;
            }
            
            if (!"IN_PROGRESS".equals(saga.getStatus())) {
                log.error("재시도할 수 없는 Saga 상태: sagaId={}, status={}", sagaId, saga.getStatus());
                return;
            }
            
            log.info("Saga 수동 재시도: sagaId={}, currentStep={}", sagaId, saga.getCurrentStep());
            
            // 현재 단계에 따라 적절한 재시도 로직 실행
            switch (saga.getCurrentStep()) {
                case "ORDER_CREATED":
                    // 결제 요청 재전송
                    // sagaOrchestratorService.retryPaymentRequest(saga);
                    break;
                case "PAYMENT_REQUESTED":
                    // 결제 상태 재확인
                    // sagaOrchestratorService.checkPaymentStatus(saga);
                    break;
                default:
                    log.warn("재시도할 수 없는 단계: {}", saga.getCurrentStep());
            }
            
        } catch (Exception e) {
            log.error("Saga 재시도 중 오류 발생: sagaId={}, error={}", sagaId, e.getMessage());
        }
    }
    
    /**
     * 특정 주문의 Saga 상태 조회
     */
    public SagaTransaction getSagaByOrderId(String orderId) {
        return sagaTransactionRepository.findByOrderId(orderId);
    }
    
    /**
     * 모든 진행 중인 Saga 조회
     */
    public List<SagaTransaction> getInProgressSagas() {
        return sagaTransactionRepository.findByCurrentStepAndStatus("*", "IN_PROGRESS");
    }
} 