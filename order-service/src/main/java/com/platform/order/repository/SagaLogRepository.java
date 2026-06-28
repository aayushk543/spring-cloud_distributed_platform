package com.platform.order.repository;

import com.platform.order.model.SagaLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SagaLogRepository extends JpaRepository<SagaLog, UUID> {
    List<SagaLog> findByOrderIdOrderByTimestampAsc(UUID orderId);
}
