package com.platform.notification.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "notifications") @Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    private String userId;
    private String orderId;
    private String type;
    @Column(columnDefinition = "TEXT") private String message;
    @Builder.Default private boolean read = false;
    private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}
