package com.platform.inventory.dto;

import lombok.*;
import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor
public class ProductRequest {
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private int quantity;
}
