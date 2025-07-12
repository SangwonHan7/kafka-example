package com.example.order.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "saga_transactions")
@Getter
@Setter
public class SagaTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String sagaId;
    
    private String orderId;
    private BigDecimal amount;
    private String currentStep;  // STARTED, ORDER_CREATED, PAYMENT_REQUESTED, COMPLETED, COMPENSATED
    private String status;       // IN_PROGRESS, FINISHED
    private String lastMessage;
    
    private LocalDateTime startedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime finishedAt;
    
    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
} 