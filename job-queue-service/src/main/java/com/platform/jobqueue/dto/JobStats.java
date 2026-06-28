package com.platform.jobqueue.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobStats {

    private long totalJobs;
    private long queuedJobs;
    private long processingJobs;
    private long completedJobs;
    private long failedJobs;
    private long dlqJobs;
    private double successRate;
}
