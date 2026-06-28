package com.platform.payment.dto;

import lombok.*;
import java.util.HashMap;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SagaEvent {
    private String orderId;
    private String eventType;
    @Builder.Default private Map<String, Object> payload = new HashMap<>();
    private String timestamp;
}
