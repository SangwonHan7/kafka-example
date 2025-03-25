package com.example.payment.repository;

import com.example.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Payment findByOrderId(String orderId);
} 