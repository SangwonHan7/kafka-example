package com.example.payment.controller;

import com.example.payment.dto.OrderRequest;
import com.example.payment.dto.PaymentResult;
import com.example.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    
    private final PaymentService paymentService;
    
    @PostMapping
    public ResponseEntity<PaymentResult> processPayment(@RequestBody OrderRequest orderRequest) {
        try {
            PaymentResult result = paymentService.processPayment(orderRequest);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            PaymentResult errorResult = new PaymentResult(orderRequest.getOrderId(), "ERROR", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResult);
        }
    }
} 