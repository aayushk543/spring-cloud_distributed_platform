package com.platform.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
    private String userId;
    private String productId;
    private int quantity;
    private String idempotencyKey;
}
