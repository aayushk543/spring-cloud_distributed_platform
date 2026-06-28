package com.platform.inventory.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "products") @Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Product {
    @Id private String id;
    private String name;
    private String description;
    @Column(precision = 10, scale = 2) private BigDecimal price;
    private int quantity;
    private int reservedQuantity;
    @Version private Long version; // Optimistic locking
}
