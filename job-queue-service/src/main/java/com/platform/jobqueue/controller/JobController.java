package com.platform.jobqueue.controller;

import com.platform.jobqueue.dto.JobRequest;
import com.platform.jobqueue.dto.JobResponse;
import com.platform.jobqueue.dto.JobStats;
import com.platform.jobqueue.model.JobStatus;
import com.platform.jobqueue.service.JobQueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobQueueService jobQueueService;

    @PostMapping
    public ResponseEntity<JobResponse> submitJob(@Valid @RequestBody JobRequest request) {
        log.info("Received job submission request: {}", request.getName());
        JobResponse response = jobQueueService.submitJob(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJob(@PathVariable UUID id) {
        JobResponse response = jobQueueService.getJob(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<JobResponse>> getJobsByStatus(
            @RequestParam(required = false) JobStatus status) {
        if (status != null) {
            return ResponseEntity.ok(jobQueueService.getJobsByStatus(status));
        }
        // Return all jobs if no status filter
        return ResponseEntity.ok(jobQueueService.getJobsByStatus(null));
    }

    @GetMapping("/stats")
    public ResponseEntity<JobStats> getStats() {
        return ResponseEntity.ok(jobQueueService.getStats());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<JobResponse> cancelJob(@PathVariable UUID id) {
        JobResponse response = jobQueueService.cancelJob(id);
        return ResponseEntity.ok(response);
    }
}
