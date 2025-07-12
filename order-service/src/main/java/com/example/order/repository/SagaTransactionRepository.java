package com.example.order.repository;

import com.example.order.domain.SagaTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SagaTransactionRepository extends JpaRepository<SagaTransaction, Long> {
    SagaTransaction findBySagaId(String sagaId);
    SagaTransaction findByOrderId(String orderId);
    
    @Query("SELECT s FROM SagaTransaction s WHERE s.status = 'IN_PROGRESS' AND s.startedAt < :timeoutThreshold")
    List<SagaTransaction> findTimeoutTransactions(@Param("timeoutThreshold") LocalDateTime timeoutThreshold);
    
    List<SagaTransaction> findByCurrentStepAndStatus(String currentStep, String status);
} 