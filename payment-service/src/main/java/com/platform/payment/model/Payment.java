package com.platform.payment.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "payments") @Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    private String orderId;
    @Column(precision = 10, scale = 2) private BigDecimal amount;
    @Enumerated(EnumType.STRING) private PaymentStatus status;
    private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}
