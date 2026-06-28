package com.platform.inventory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.inventory.dto.ProductRequest;
import com.platform.inventory.dto.SagaEvent;
import com.platform.inventory.model.Product;
import com.platform.inventory.repository.ProductRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final ProductRepository productRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void seedData() {
        if (productRepository.count() == 0) {
            List<Product> products = List.of(
                Product.builder().id("PROD-001").name("Wireless Headphones").description("Noise-cancelling bluetooth headphones").price(BigDecimal.valueOf(79.99)).quantity(100).reservedQuantity(0).build(),
                Product.builder().id("PROD-002").name("Mechanical Keyboard").description("RGB mechanical gaming keyboard").price(BigDecimal.valueOf(129.99)).quantity(50).reservedQuantity(0).build(),
                Product.builder().id("PROD-003").name("USB-C Hub").description("7-in-1 USB-C multiport adapter").price(BigDecimal.valueOf(49.99)).quantity(200).reservedQuantity(0).build(),
                Product.builder().id("PROD-004").name("Monitor Stand").description("Adjustable aluminum monitor stand").price(BigDecimal.valueOf(39.99)).quantity(75).reservedQuantity(0).build(),
                Product.builder().id("PROD-005").name("Webcam HD").description("1080p HD webcam with microphone").price(BigDecimal.valueOf(59.99)).quantity(150).reservedQuantity(0).build()
            );
            productRepository.saveAll(products);
            log.info("Seeded {} products", products.size());
        }
    }

    @KafkaListener(topics = "inventory-events", groupId = "inventory-processor")
    @Transactional
    public void handleInventoryEvent(String message) {
        try {
            SagaEvent event = objectMapper.readValue(message, SagaEvent.class);
            log.info("Inventory service received: {} for order {}", event.getEventType(), event.getOrderId());

            switch (event.getEventType()) {
                case "INVENTORY_RESERVE" -> reserveInventory(event);
                case "INVENTORY_RELEASE" -> releaseInventory(event);
                default -> log.warn("Unknown inventory event: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Failed to process inventory event", e);
        }
    }

    private void reserveInventory(SagaEvent event) {
        String productId = event.getPayload().get("productId").toString();
        int quantity = ((Number) event.getPayload().get("quantity")).intValue();

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            publishEvent("order-events", SagaEvent.builder()
                    .orderId(event.getOrderId()).eventType("INVENTORY_INSUFFICIENT")
                    .payload(Map.of("reason", "Product not found: " + productId))
                    .timestamp(LocalDateTime.now().toString()).build());
            return;
        }

        int available = product.getQuantity() - product.getReservedQuantity();
        if (available >= quantity) {
            product.setReservedQuantity(product.getReservedQuantity() + quantity);
            productRepository.save(product);
            log.info("Reserved {} units of {} (available: {})", quantity, productId, available - quantity);

            publishEvent("order-events", SagaEvent.builder()
                    .orderId(event.getOrderId()).eventType("INVENTORY_RESERVED")
                    .payload(Map.of("productId", productId, "quantity", quantity))
                    .timestamp(LocalDateTime.now().toString()).build());
        } else {
            log.warn("Insufficient inventory for {}: requested={}, available={}", productId, quantity, available);
            publishEvent("order-events", SagaEvent.builder()
                    .orderId(event.getOrderId()).eventType("INVENTORY_INSUFFICIENT")
                    .payload(Map.of("reason", "Only " + available + " units available"))
                    .timestamp(LocalDateTime.now().toString()).build());
        }
    }

    private void releaseInventory(SagaEvent event) {
        String productId = event.getPayload().get("productId").toString();
        int quantity = ((Number) event.getPayload().get("quantity")).intValue();

        productRepository.findById(productId).ifPresent(product -> {
            product.setReservedQuantity(Math.max(0, product.getReservedQuantity() - quantity));
            productRepository.save(product);
            log.info("Released {} units of {}", quantity, productId);

            publishEvent("order-events", SagaEvent.builder()
                    .orderId(event.getOrderId()).eventType("INVENTORY_RELEASED")
                    .payload(Map.of("productId", productId))
                    .timestamp(LocalDateTime.now().toString()).build());
        });
    }

    public Product getProduct(String id) {
        return productRepository.findById(id).orElseThrow(() -> new RuntimeException("Product not found: " + id));
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product addProduct(ProductRequest request) {
        Product product = Product.builder()
                .id(request.getId()).name(request.getName()).description(request.getDescription())
                .price(request.getPrice()).quantity(request.getQuantity()).reservedQuantity(0).build();
        return productRepository.save(product);
    }

    private void publishEvent(String topic, SagaEvent event) {
        try {
            kafkaTemplate.send(topic, event.getOrderId(), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to publish event", e);
        }
    }
}
