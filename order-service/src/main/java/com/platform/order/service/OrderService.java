package com.platform.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.order.dto.OrderRequest;
import com.platform.order.dto.SagaEvent;
import com.platform.order.model.*;
import com.platform.order.repository.OrderRepository;
import com.platform.order.repository.SagaLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final SagaLogRepository sagaLogRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public Order createOrder(OrderRequest request) {
        // Idempotency check
        if (request.getIdempotencyKey() != null) {
            Optional<Order> existing = orderRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                log.info("Duplicate order detected with idempotency key: {}", request.getIdempotencyKey());
                return existing.get();
            }
        }

        Order order = Order.builder()
                .userId(request.getUserId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .totalAmount(BigDecimal.valueOf(request.getQuantity() * 29.99))
                .status(OrderStatus.CREATED)
                .sagaStatus(SagaStatus.STARTED)
                .idempotencyKey(request.getIdempotencyKey() != null ? request.getIdempotencyKey() : UUID.randomUUID().toString())
                .build();

        order = orderRepository.save(order);
        logSagaStep(order.getId(), "ORDER_CREATED", "SUCCESS", "Order created");

        // Start saga: request inventory reservation
        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", request.getProductId());
        payload.put("quantity", request.getQuantity());
        payload.put("userId", request.getUserId());

        SagaEvent event = SagaEvent.builder()
                .orderId(order.getId().toString())
                .eventType("INVENTORY_RESERVE")
                .payload(payload)
                .timestamp(LocalDateTime.now().toString())
                .build();

        publishEvent("inventory-events", event);

        order.setSagaStatus(SagaStatus.INVENTORY_PENDING);
        orderRepository.save(order);

        log.info("Order created and saga started: {}", order.getId());
        return order;
    }

    public Order getOrder(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
    }

    public List<Order> getOrders() {
        return orderRepository.findAll();
    }

    public List<SagaLog> getSagaLog(UUID orderId) {
        return sagaLogRepository.findByOrderIdOrderByTimestampAsc(orderId);
    }

    public void logSagaStep(UUID orderId, String step, String status, String payload) {
        SagaLog sagaLog = SagaLog.builder()
                .orderId(orderId)
                .step(step)
                .status(status)
                .payload(payload)
                .build();
        sagaLogRepository.save(sagaLog);
    }

    public void publishEvent(String topic, SagaEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, event.getOrderId(), json);
            log.info("Published event {} to topic {}", event.getEventType(), topic);
        } catch (Exception e) {
            log.error("Failed to publish event to {}", topic, e);
        }
    }
}
