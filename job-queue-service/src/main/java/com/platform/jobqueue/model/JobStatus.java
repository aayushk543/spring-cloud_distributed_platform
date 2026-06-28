package com.platform.jobqueue.model;

public enum JobStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED,
    DLQ
}
