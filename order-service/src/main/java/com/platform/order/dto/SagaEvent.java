package com.platform.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaEvent {
    private String orderId;
    private String eventType;
    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();
    private String timestamp;
}
