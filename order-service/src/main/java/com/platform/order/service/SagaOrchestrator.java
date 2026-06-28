package com.platform.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.order.dto.SagaEvent;
import com.platform.order.model.Order;
import com.platform.order.model.OrderStatus;
import com.platform.order.model.SagaStatus;
import com.platform.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestrator {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-events", groupId = "saga-orchestrator")
    @Transactional
    public void handleSagaEvent(String message) {
        try {
            SagaEvent event = objectMapper.readValue(message, SagaEvent.class);
            UUID orderId = UUID.fromString(event.getOrderId());
            Order order = orderRepository.findById(orderId).orElse(null);

            if (order == null) {
                log.warn("Order not found for saga event: {}", event.getOrderId());
                return;
            }

            log.info("Saga orchestrator received: {} for order {}", event.getEventType(), orderId);

            switch (event.getEventType()) {
                case "INVENTORY_RESERVED" -> handleInventoryReserved(order, event);
                case "INVENTORY_INSUFFICIENT" -> handleInventoryInsufficient(order, event);
                case "PAYMENT_SUCCESS" -> handlePaymentSuccess(order, event);
                case "PAYMENT_FAILED" -> handlePaymentFailed(order, event);
                case "INVENTORY_RELEASED" -> handleInventoryReleased(order, event);
                default -> log.warn("Unknown saga event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Failed to process saga event", e);
        }
    }

    private void handleInventoryReserved(Order order, SagaEvent event) {
        order.setStatus(OrderStatus.INVENTORY_RESERVED);
        order.setSagaStatus(SagaStatus.PAYMENT_PENDING);
        orderRepository.save(order);
        orderService.logSagaStep(order.getId(), "INVENTORY_RESERVED", "SUCCESS", "Inventory reserved");

        // Next step: request payment
        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", order.getTotalAmount().toString());
        payload.put("userId", order.getUserId());

        SagaEvent paymentEvent = SagaEvent.builder()
                .orderId(order.getId().toString())
                .eventType("PAYMENT_REQUEST")
                .payload(payload)
                .timestamp(LocalDateTime.now().toString())
                .build();

        orderService.publishEvent("payment-events", paymentEvent);
    }

    private void handleInventoryInsufficient(Order order, SagaEvent event) {
        order.setStatus(OrderStatus.FAILED);
        order.setSagaStatus(SagaStatus.FAILED);
        order.setFailureReason("Insufficient inventory");
        orderRepository.save(order);
        orderService.logSagaStep(order.getId(), "INVENTORY_INSUFFICIENT", "FAILED", "Not enough stock");

        // Notify about failure
        publishNotification(order, "ORDER_FAILED", "Order failed: insufficient inventory");
    }

    private void handlePaymentSuccess(Order order, SagaEvent event) {
        order.setStatus(OrderStatus.COMPLETED);
        order.setSagaStatus(SagaStatus.COMPLETED);
        orderRepository.save(order);
        orderService.logSagaStep(order.getId(), "PAYMENT_SUCCESS", "SUCCESS", "Payment processed");

        // Notify about success
        publishNotification(order, "ORDER_CONFIRMED", "Order completed successfully");
    }

    private void handlePaymentFailed(Order order, SagaEvent event) {
        order.setStatus(OrderStatus.FAILED);
        order.setSagaStatus(SagaStatus.COMPENSATING);
        order.setFailureReason("Payment failed");
        orderRepository.save(order);
        orderService.logSagaStep(order.getId(), "PAYMENT_FAILED", "FAILED", "Payment declined");

        // Compensate: release inventory
        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", order.getProductId());
        payload.put("quantity", order.getQuantity());

        SagaEvent releaseEvent = SagaEvent.builder()
                .orderId(order.getId().toString())
                .eventType("INVENTORY_RELEASE")
                .payload(payload)
                .timestamp(LocalDateTime.now().toString())
                .build();

        orderService.publishEvent("inventory-events", releaseEvent);
        publishNotification(order, "ORDER_FAILED", "Order failed: payment declined");
    }

    private void handleInventoryReleased(Order order, SagaEvent event) {
        order.setSagaStatus(SagaStatus.COMPENSATION_COMPLETE);
        orderRepository.save(order);
        orderService.logSagaStep(order.getId(), "INVENTORY_RELEASED", "SUCCESS", "Compensation complete");
    }

    private void publishNotification(Order order, String type, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", order.getUserId());
        payload.put("type", type);
        payload.put("message", message);

        SagaEvent notifEvent = SagaEvent.builder()
                .orderId(order.getId().toString())
                .eventType("NOTIFICATION_SEND")
                .payload(payload)
                .timestamp(LocalDateTime.now().toString())
                .build();

        orderService.publishEvent("notification-events", notifEvent);
    }
}
