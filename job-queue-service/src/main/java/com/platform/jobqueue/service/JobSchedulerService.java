package com.platform.jobqueue.service;

import com.platform.jobqueue.model.Job;
import com.platform.jobqueue.model.JobStatus;
import com.platform.jobqueue.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobSchedulerService {

    private static final String PRIORITY_QUEUE_KEY = "job:priority-queue";

    private final JobRepository jobRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void pollQueue() {
        // Pop the job with the lowest score (highest priority) from Redis sorted set
        Set<ZSetOperations.TypedTuple<String>> result = redisTemplate.opsForZSet()
                .popMin(PRIORITY_QUEUE_KEY, 1);

        if (result == null || result.isEmpty()) {
            return;
        }

        ZSetOperations.TypedTuple<String> tuple = result.iterator().next();
        String jobIdStr = tuple.getValue();

        if (jobIdStr == null) {
            return;
        }

        try {
            UUID jobId = UUID.fromString(jobIdStr);
            Job job = jobRepository.findById(jobId).orElse(null);

            if (job == null) {
                log.warn("Job {} from Redis queue not found in database", jobId);
                return;
            }

            if (job.getStatus() != JobStatus.QUEUED) {
                log.warn("Job {} popped from queue but status is {}, skipping", jobId, job.getStatus());
                return;
            }

            // Update status to PROCESSING
            job.setStatus(JobStatus.PROCESSING);
            jobRepository.save(job);
            log.info("Job {} set to PROCESSING, publishing to job-processing topic", jobId);

            // Publish job ID to Kafka for processing
            kafkaTemplate.send("job-processing", jobId.toString(), jobId.toString());

        } catch (Exception e) {
            log.error("Error polling queue for job {}: {}", jobIdStr, e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void retryFailedJobs() {
        List<Job> failedJobs = jobRepository.findByStatusAndNextRetryAtBefore(
                JobStatus.FAILED, LocalDateTime.now());

        if (failedJobs.isEmpty()) {
            return;
        }

        log.info("Found {} failed jobs eligible for retry", failedJobs.size());

        for (Job job : failedJobs) {
            try {
                // Re-queue to Redis sorted set
                double score = job.getPriority().getWeight() * 1000.0 + (System.currentTimeMillis() % 1000);
                redisTemplate.opsForZSet().add(PRIORITY_QUEUE_KEY, job.getId().toString(), score);

                // Update status back to QUEUED
                job.setStatus(JobStatus.QUEUED);
                job.setNextRetryAt(null);
                jobRepository.save(job);

                log.info("Job {} re-queued for retry (attempt {}/{})",
                        job.getId(), job.getRetryCount(), job.getMaxRetries());
            } catch (Exception e) {
                log.error("Error re-queuing job {}: {}", job.getId(), e.getMessage(), e);
            }
        }
    }
}
