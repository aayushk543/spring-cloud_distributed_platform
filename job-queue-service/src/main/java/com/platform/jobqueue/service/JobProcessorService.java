package com.platform.jobqueue.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.jobqueue.model.Job;
import com.platform.jobqueue.model.JobStatus;
import com.platform.jobqueue.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobProcessorService {

    private final JobRepository jobRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    @KafkaListener(topics = "job-processing", groupId = "job-processor")
    public void processJob(String message) {
        try {
            UUID jobId = UUID.fromString(message.replace("\"", "").trim());
            log.info("Received job for processing: {}", jobId);

            Job job = jobRepository.findById(jobId).orElse(null);
            if (job == null) {
                log.warn("Job {} not found in database, skipping", jobId);
                return;
            }

            if (job.getStatus() != JobStatus.PROCESSING) {
                log.warn("Job {} is not in PROCESSING status (current: {}), skipping", jobId, job.getStatus());
                return;
            }

            // Simulate processing time (1-3 seconds)
            int processingTime = 1000 + random.nextInt(2001);
            log.info("Processing job {} for {}ms", jobId, processingTime);
            Thread.sleep(processingTime);

            // 20% chance of failure
            if (random.nextDouble() < 0.2) {
                throw new RuntimeException("Simulated processing failure for job: " + jobId);
            }

            // Success
            job.setStatus(JobStatus.COMPLETED);
            job.setErrorMessage(null);
            jobRepository.save(job);
            log.info("Job {} completed successfully", jobId);

            kafkaTemplate.send("job-completed", jobId.toString(), jobId.toString());

        } catch (RuntimeException e) {
            log.error("Job processing failed: {}", e.getMessage());
            handleFailure(message, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Job processing interrupted: {}", e.getMessage());
            handleFailure(message, e);
        }
    }

    @Transactional
    private void handleFailure(String message, Exception exception) {
        try {
            UUID jobId = UUID.fromString(message.replace("\"", "").trim());
            Job job = jobRepository.findById(jobId).orElse(null);
            if (job == null) {
                log.error("Cannot handle failure for job {} - not found in database", jobId);
                return;
            }

            job.setRetryCount(job.getRetryCount() + 1);
            job.setErrorMessage(exception.getMessage());

            if (job.getRetryCount() >= job.getMaxRetries()) {
                // Move to Dead Letter Queue
                job.setStatus(JobStatus.DLQ);
                jobRepository.save(job);
                log.warn("Job {} moved to DLQ after {} retries", jobId, job.getRetryCount());

                kafkaTemplate.send("job-dlq", jobId.toString(), jobId.toString());
            } else {
                // Set status to FAILED and calculate next retry with exponential backoff
                job.setStatus(JobStatus.FAILED);
                long backoffSeconds = (long) Math.pow(2, job.getRetryCount());
                job.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds));
                jobRepository.save(job);
                log.info("Job {} failed (attempt {}/{}), next retry at {} (backoff: {}s)",
                        jobId, job.getRetryCount(), job.getMaxRetries(),
                        job.getNextRetryAt(), backoffSeconds);

                kafkaTemplate.send("job-failed", jobId.toString(), jobId.toString());
            }
        } catch (Exception e) {
            log.error("Error handling failure for job: {}", e.getMessage(), e);
        }
    }
}
