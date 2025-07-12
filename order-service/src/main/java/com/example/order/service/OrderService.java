package com.example.order.service;

import com.example.order.dto.OrderRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.dto.PaymentResult;
import com.example.order.repository.OrderRepository;
import com.example.order.domain.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final SagaOrchestratorService sagaOrchestratorService;
    private final OrderRepository orderRepository;
    
    // 주문 결과를 저장할 Map (Saga ID 기반)
    private final ConcurrentHashMap<String, CompletableFuture<OrderResponse>> orderResults = new ConcurrentHashMap<>();
    // 주문 ID와 Saga ID를 매핑하는 Map
    private final ConcurrentHashMap<String, String> sagaOrderMapping = new ConcurrentHashMap<>();
    
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    
    /**
     * 보상 트랜잭션을 포함한 주문 생성
     */
    public CompletableFuture<OrderResponse> createOrderWithSaga(OrderRequest request) {
        CompletableFuture<OrderResponse> resultFuture = new CompletableFuture<>();
        
        try {
            // 주문 ID가 없는 경우 생성
            if (request.getOrderId() == null) {
                request.setOrderId(java.util.UUID.randomUUID().toString());
            }
            
            // Saga 트랜잭션 시작
            String sagaId = sagaOrchestratorService.startOrderPaymentSaga(request);
            
            // 결과 대기를 위한 Future 등록
            orderResults.put(sagaId, resultFuture);
            // Saga ID와 주문 ID 매핑
            sagaOrderMapping.put(sagaId, request.getOrderId());
            
            // 타임아웃 설정 (10초)
            setTimeout(sagaId, 10000);
            
            log.info("주문 생성 Saga 시작: sagaId={}, orderId={}", sagaId, request.getOrderId());
            
        } catch (Exception e) {
            log.error("주문 생성 중 오류 발생: {}", e.getMessage());
            OrderResponse errorResponse = new OrderResponse(
                request.getOrderId(), 
                "ERROR", 
                "주문 생성 실패: " + e.getMessage()
            );
            resultFuture.complete(errorResponse);
        }
        
        return resultFuture;
    }
    
    /**
     * 결제 결과 수신 시 호출되는 메서드
     */
    public void handlePaymentResult(PaymentResult result, String sagaId) {
        try {
            log.info("결제 결과 수신: sagaId={}, status={}, message={}", sagaId, result.getStatus(), result.getMessage());
            
            // Saga 오케스트레이터에 결과 전달
            sagaOrchestratorService.handlePaymentResult(sagaId, result.getStatus(), result.getMessage());
            
            // 주문 ID 확인 (result.getOrderId()가 null인 경우 매핑에서 찾기)
            String orderId = result.getOrderId();
            if (orderId == null) {
                orderId = sagaOrderMapping.get(sagaId);
            }
            
            // 그래도 orderId가 없다면 DB에서 찾기
            if (orderId == null) {
                // Saga ID로 주문 찾기
                List<Order> orders = orderRepository.findBySagaId(sagaId);
                if (!orders.isEmpty()) {
                    orderId = orders.get(0).getOrderId();
                    log.info("DB에서 주문 ID 찾음: sagaId={}, orderId={}", sagaId, orderId);
                }
            }
            
            // 대기 중인 Future에 결과 전달
            CompletableFuture<OrderResponse> future = orderResults.get(sagaId);
            if (future != null) {
                OrderResponse response = new OrderResponse(
                    orderId, // null이 아닌 orderId 사용
                    result.getStatus(),
                    result.getMessage()
                );
                future.complete(response);
                orderResults.remove(sagaId);
                sagaOrderMapping.remove(sagaId);
                
                log.info("주문 결과 완료: sagaId={}, orderId={}, status={}", 
                        sagaId, orderId, result.getStatus());
            } else {
                log.warn("대기 중인 Future가 없음: sagaId={}", sagaId);
            }
            
        } catch (Exception e) {
            log.error("결제 결과 처리 중 오류 발생: sagaId={}, error={}", sagaId, e.getMessage());
            
            CompletableFuture<OrderResponse> future = orderResults.get(sagaId);
            if (future != null) {
                // 주문 ID 확인
                String orderId = result.getOrderId();
                if (orderId == null) {
                    orderId = sagaOrderMapping.get(sagaId);
                }
                
                OrderResponse errorResponse = new OrderResponse(
                    orderId, // null이 아닌 orderId 사용
                    "ERROR",
                    "결제 결과 처리 실패: " + e.getMessage()
                );
                future.complete(errorResponse);
                orderResults.remove(sagaId);
                sagaOrderMapping.remove(sagaId);
            }
        }
    }
    
    /**
     * 주문 조회 기능
     */
    public OrderResponse getOrder(String orderId) {
        Order order = orderRepository.findByOrderId(orderId);
        if (order == null) {
            return new OrderResponse(orderId, "NOT_FOUND", "주문을 찾을 수 없습니다");
        }
        return new OrderResponse(orderId, order.getStatus(), "주문 조회 성공");
    }
    
    /**
     * 주문 상태 업데이트 (결제 결과 수신 시 호출)
     */
    public void updateOrderStatus(String orderId, String status, String message) {
        try {
            log.info("주문 상태 업데이트: orderId={}, status={}", orderId, status);
            
            Order order = orderRepository.findByOrderId(orderId);
            if (order != null) {
                // 결제 실패인 경우 주문 취소 처리
                if ("ERROR".equals(status) || "FAILED".equals(status)) {
                    compensateOrder(order, message);
                } else {
                    order.setStatus(status);
                    orderRepository.save(order);
                }
                
                log.info("주문 상태 업데이트 완료: orderId={}, status={}", orderId, status);
            } else {
                log.warn("업데이트할 주문을 찾을 수 없음: orderId={}", orderId);
            }
        } catch (Exception e) {
            log.error("주문 상태 업데이트 중 오류: orderId={}, error={}", orderId, e.getMessage());
        }
    }
    
    /**
     * 보상 트랜잭션 처리
     */
    private void compensateOrder(Order order, String failureReason) {
        try {
            // 주문 상태를 CANCELLED로 변경
            order.setStatus("CANCELLED");
            order.setFailureReason(failureReason);
            orderRepository.save(order);
            
            // 추가적인 보상 로직 구현
            
            log.info("주문 {} 결제 실패로 인해 취소 처리됨: {}", 
                order.getOrderId(), failureReason);
            
        } catch (Exception e) {
            log.error("주문 보상 처리 중 오류: orderId={}, error={}", 
                    order.getOrderId(), e.getMessage());
        }
    }
    
    /**
     * 타임아웃 처리
     */
    private void setTimeout(String sagaId, long timeout) {
        CompletableFuture.delayedExecutor(timeout, TimeUnit.MILLISECONDS)
            .execute(() -> {
                CompletableFuture<OrderResponse> future = orderResults.get(sagaId);
                if (future != null && !future.isDone()) {
                    log.warn("주문 처리 타임아웃: sagaId={}", sagaId);
                    
                    // 주문 ID 확인
                    String orderId = sagaOrderMapping.get(sagaId);
                    
                    // 타임아웃 시에도 보상 트랜잭션 실행
                    try {
                        // SagaTransaction 조회
                        sagaOrchestratorService.compensateSaga(
                            // sagaTransaction을 조회해서 전달해야 함
                            null, // 실제로는 repository에서 조회
                            "처리 시간 초과"
                        );
                    } catch (Exception e) {
                        log.error("타임아웃 보상 트랜잭션 실행 중 오류: {}", e.getMessage());
                    }
                    
                    OrderResponse timeoutResponse = new OrderResponse(
                        orderId, // null이 아닌 orderId 사용
                        "TIMEOUT", 
                        "주문 처리 시간이 초과되었습니다"
                    );
                    future.complete(timeoutResponse);
                    orderResults.remove(sagaId);
                    sagaOrderMapping.remove(sagaId);
                }
            });
    }
} 