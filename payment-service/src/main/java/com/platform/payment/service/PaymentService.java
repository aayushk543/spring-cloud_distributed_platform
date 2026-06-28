package com.platform.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.payment.dto.SagaEvent;
import com.platform.payment.model.Payment;
import com.platform.payment.model.PaymentStatus;
import com.platform.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    @KafkaListener(topics = "payment-events", groupId = "payment-processor")
    public void handlePaymentEvent(String message) {
        try {
            SagaEvent event = objectMapper.readValue(message, SagaEvent.class);
            log.info("Payment service received: {} for order {}", event.getEventType(), event.getOrderId());

            switch (event.getEventType()) {
                case "PAYMENT_REQUEST" -> processPayment(event);
                case "PAYMENT_ROLLBACK" -> rollbackPayment(event);
                default -> log.warn("Unknown payment event: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Failed to process payment event", e);
        }
    }

    private void processPayment(SagaEvent event) throws Exception {
        BigDecimal amount = new BigDecimal(event.getPayload().get("amount").toString());

        Payment payment = Payment.builder()
                .orderId(event.getOrderId())
                .amount(amount)
                .status(PaymentStatus.PENDING)
                .build();
        paymentRepository.save(payment);

        // Simulate payment processing delay
        Thread.sleep(1000);

        // 15% chance of failure for demo purposes
        boolean success = random.nextInt(100) >= 15;

        if (success) {
            payment.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);
            log.info("Payment SUCCESS for order {}", event.getOrderId());

            publishEvent("order-events", SagaEvent.builder()
                    .orderId(event.getOrderId())
                    .eventType("PAYMENT_SUCCESS")
                    .payload(Map.of("paymentId", payment.getId().toString()))
                    .timestamp(LocalDateTime.now().toString())
                    .build());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            log.info("Payment FAILED for order {}", event.getOrderId());

            publishEvent("order-events", SagaEvent.builder()
                    .orderId(event.getOrderId())
                    .eventType("PAYMENT_FAILED")
                    .payload(Map.of("reason", "Payment declined by processor"))
                    .timestamp(LocalDateTime.now().toString())
                    .build());
        }
    }

    private void rollbackPayment(SagaEvent event) {
        paymentRepository.findByOrderId(event.getOrderId()).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
            log.info("Payment REFUNDED for order {}", event.getOrderId());
        });
    }

    public Payment getPaymentByOrderId(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for order: " + orderId));
    }

    private void publishEvent(String topic, SagaEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, event.getOrderId(), json);
        } catch (Exception e) {
            log.error("Failed to publish event", e);
        }
    }
}
