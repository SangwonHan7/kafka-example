package com.example.order.controller;

import com.example.order.dto.OrderRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    
    private final OrderService orderService;
    
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest orderRequest) {
        try {
            CompletableFuture<OrderResponse> futureResult = orderService.createOrder(orderRequest);
            OrderResponse result = futureResult.get(31, TimeUnit.SECONDS);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            OrderResponse errorResponse = new OrderResponse(null, "ERROR", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }
} 