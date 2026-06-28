package com.platform.jobqueue.dto;

import com.platform.jobqueue.model.Job;
import com.platform.jobqueue.model.JobStatus;
import com.platform.jobqueue.model.Priority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobResponse {

    private UUID id;
    private String name;
    private String payload;
    private Priority priority;
    private JobStatus status;
    private int retryCount;
    private int maxRetries;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime nextRetryAt;

    public static JobResponse fromEntity(Job job) {
        return JobResponse.builder()
                .id(job.getId())
                .name(job.getName())
                .payload(job.getPayload())
                .priority(job.getPriority())
                .status(job.getStatus())
                .retryCount(job.getRetryCount())
                .maxRetries(job.getMaxRetries())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .nextRetryAt(job.getNextRetryAt())
                .build();
    }
}
