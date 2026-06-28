package com.platform.jobqueue.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.jobqueue.dto.JobRequest;
import com.platform.jobqueue.dto.JobResponse;
import com.platform.jobqueue.dto.JobStats;
import com.platform.jobqueue.exception.JobNotFoundException;
import com.platform.jobqueue.model.Job;
import com.platform.jobqueue.model.JobStatus;
import com.platform.jobqueue.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobQueueService {

    private static final String PRIORITY_QUEUE_KEY = "job:priority-queue";

    private final JobRepository jobRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public JobResponse submitJob(JobRequest request) {
        Job job = Job.builder()
                .name(request.getName())
                .payload(request.getPayload())
                .priority(request.getPriority())
                .status(JobStatus.QUEUED)
                .retryCount(0)
                .maxRetries(request.getMaxRetries())
                .build();

        job = jobRepository.save(job);
        log.info("Job created with ID: {} and priority: {}", job.getId(), job.getPriority());

        // Add to Redis sorted set: score = priority.weight * 1000 + millis remainder for FIFO within same priority
        double score = job.getPriority().getWeight() * 1000.0 + (System.currentTimeMillis() % 1000);
        redisTemplate.opsForZSet().add(PRIORITY_QUEUE_KEY, job.getId().toString(), score);
        log.info("Job {} added to priority queue with score: {}", job.getId(), score);

        // Publish to Kafka
        try {
            String message = objectMapper.writeValueAsString(JobResponse.fromEntity(job));
            kafkaTemplate.send("job-submitted", job.getId().toString(), message);
            log.info("Job {} published to job-submitted topic", job.getId());
        } catch (Exception e) {
            log.error("Failed to publish job {} to Kafka: {}", job.getId(), e.getMessage());
        }

        return JobResponse.fromEntity(job);
    }

    public JobResponse getJob(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException("Job not found with ID: " + id));
        return JobResponse.fromEntity(job);
    }

    public List<JobResponse> getJobsByStatus(JobStatus status) {
        return jobRepository.findByStatus(status).stream()
                .map(JobResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public JobStats getStats() {
        long queued = jobRepository.countByStatus(JobStatus.QUEUED);
        long processing = jobRepository.countByStatus(JobStatus.PROCESSING);
        long completed = jobRepository.countByStatus(JobStatus.COMPLETED);
        long failed = jobRepository.countByStatus(JobStatus.FAILED);
        long dlq = jobRepository.countByStatus(JobStatus.DLQ);
        long total = queued + processing + completed + failed + dlq;

        double successRate = total > 0 ? (double) completed / total * 100.0 : 0.0;

        return JobStats.builder()
                .totalJobs(total)
                .queuedJobs(queued)
                .processingJobs(processing)
                .completedJobs(completed)
                .failedJobs(failed)
                .dlqJobs(dlq)
                .successRate(Math.round(successRate * 100.0) / 100.0)
                .build();
    }

    @Transactional
    public JobResponse cancelJob(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException("Job not found with ID: " + id));

        if (job.getStatus() != JobStatus.QUEUED) {
            throw new IllegalStateException("Can only cancel jobs in QUEUED status. Current status: " + job.getStatus());
        }

        job.setStatus(JobStatus.FAILED);
        job.setErrorMessage("Job cancelled by user");
        job = jobRepository.save(job);

        // Remove from Redis queue
        redisTemplate.opsForZSet().remove(PRIORITY_QUEUE_KEY, job.getId().toString());
        log.info("Job {} cancelled and removed from queue", job.getId());

        return JobResponse.fromEntity(job);
    }
}
