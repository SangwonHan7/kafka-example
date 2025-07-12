package com.example.order.controller;

import com.example.order.dto.OrderRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    
    private final OrderService orderService;
    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    /**
     * 주문 생성 API - Saga 패턴 사용
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest orderRequest) {
        try {
            log.info("주문 생성 요청: orderId={} (Saga 패턴 사용)", orderRequest.getOrderId());
            
            CompletableFuture<OrderResponse> futureResult = orderService.createOrderWithSaga(orderRequest);
            OrderResponse result = futureResult.get(11, TimeUnit.SECONDS);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("주문 생성 실패: {}", e.getMessage());
            OrderResponse errorResponse = new OrderResponse(
                orderRequest.getOrderId(), 
                "ERROR", 
                e.getMessage()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * 주문 조회 API
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }
} 