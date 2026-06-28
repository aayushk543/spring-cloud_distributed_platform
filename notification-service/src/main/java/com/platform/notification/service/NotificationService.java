package com.platform.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.notification.dto.SagaEvent;
import com.platform.notification.model.Notification;
import com.platform.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {"notification-events", "order-events"}, groupId = "notification-consumer")
    public void handleEvent(String message) {
        try {
            SagaEvent event = objectMapper.readValue(message, SagaEvent.class);

            // Only process notification-relevant events
            if (!"NOTIFICATION_SEND".equals(event.getEventType()) &&
                !"ORDER_CONFIRMED".equals(event.getEventType()) &&
                !"ORDER_FAILED".equals(event.getEventType())) {
                return;
            }

            String userId = event.getPayload().getOrDefault("userId", "unknown").toString();
            String type = event.getPayload().getOrDefault("type", event.getEventType()).toString();
            String msg = event.getPayload().getOrDefault("message", "Notification for order " + event.getOrderId()).toString();

            Notification notification = Notification.builder()
                    .userId(userId)
                    .orderId(event.getOrderId())
                    .type(type)
                    .message(msg)
                    .build();

            notificationRepository.save(notification);
            log.info("📧 Notification saved: [{}] {} -> {}", type, userId, msg);
        } catch (Exception e) {
            log.error("Failed to process notification event", e);
        }
    }

    public List<Notification> getNotifications(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Notification> getNotificationsByOrder(String orderId) {
        return notificationRepository.findByOrderId(orderId);
    }

    public void markAsRead(UUID id) {
        notificationRepository.findById(id).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }
}
