package com.example.order.service;

import com.example.order.dto.OrderRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.domain.Order;
import com.example.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, OrderRequest> kafkaTemplate;
    private final String PAYMENT_REQUEST_TOPIC = "payment.request";
    
    // 주문 결과를 저장할 Map
    private final ConcurrentHashMap<String, CompletableFuture<OrderResponse>> orderResults = new ConcurrentHashMap<>();
    
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    
    public CompletableFuture<OrderResponse> createOrder(OrderRequest request) {
        // 주문 ID 생성 (없는 경우)
        if (request.getOrderId() == null) {
            request.setOrderId(UUID.randomUUID().toString());
        }
        
        CompletableFuture<OrderResponse> resultFuture = new CompletableFuture<>();
        orderResults.put(request.getOrderId(), resultFuture);
        
        try {
            // 주문 정보 저장
            Order order = new Order();
            order.setOrderId(request.getOrderId());
            order.setAmount(request.getAmount());
            order.setStatus("PENDING");
            orderRepository.save(order);
            
            // 결제 요청을 Kafka로 전송
            kafkaTemplate.send(PAYMENT_REQUEST_TOPIC, request);
            
            // 30초 타임아웃 설정
            setTimeout(request.getOrderId(), 30000);
            
        } catch (Exception e) {
            OrderResponse errorResponse = new OrderResponse(request.getOrderId(), "ERROR", e.getMessage());
            resultFuture.complete(errorResponse);
            orderResults.remove(request.getOrderId());
        }
        
        return resultFuture;
    }
    
    public OrderResponse getOrder(String orderId) {
        Order order = orderRepository.findByOrderId(orderId);
        if (order == null) {
            return new OrderResponse(orderId, "NOT_FOUND", "Order not found");
        }
        return new OrderResponse(orderId, order.getStatus(), "Order found");
    }
    
    // 주문 상태 업데이트 (결제 결과 수신 시 호출)
    public void updateOrderStatus(String orderId, String status, String message) {
        Order order = orderRepository.findByOrderId(orderId);
        if (order != null) {
            // 결제 실패인 경우 주문 취소 처리
            if ("ERROR".equals(status) || "FAILED".equals(status)) {
                compensateOrder(order, message);
            } else {
                order.setStatus(status);
                orderRepository.save(order);
            }
        }
        
        CompletableFuture<OrderResponse> future = orderResults.get(orderId);
        if (future != null) {
            future.complete(new OrderResponse(orderId, status, message));
            orderResults.remove(orderId);
        }
    }
    
    // 보상 트랜잭션 처리
    private void compensateOrder(Order order, String failureReason) {
        try {
            // 주문 상태를 CANCELLED로 변경
            order.setStatus("CANCELLED");
            order.setFailureReason(failureReason);
            orderRepository.save(order);
            
            // 추가적인 보상 로직 구현
            
            log.info("Order {} has been compensated due to payment failure: {}", 
                order.getOrderId(), failureReason);
            
        } catch (Exception e) {
            log.error("Failed to compensate order {}: {}", order.getOrderId(), e.getMessage());
        }
    }


    
    private void setTimeout(String orderId, long timeout) {
        CompletableFuture.delayedExecutor(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
            .execute(() -> {
                CompletableFuture<OrderResponse> future = orderResults.get(orderId);
                if (future != null && !future.isDone()) {
                    future.complete(new OrderResponse(orderId, "TIMEOUT", "Order processing timed out"));
                    orderResults.remove(orderId);
                }
            });
    }
} 