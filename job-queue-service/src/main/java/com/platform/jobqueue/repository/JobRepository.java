package com.platform.jobqueue.repository;

import com.platform.jobqueue.model.Job;
import com.platform.jobqueue.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {

    List<Job> findByStatus(JobStatus status);

    long countByStatus(JobStatus status);

    List<Job> findByStatusAndNextRetryAtBefore(JobStatus status, LocalDateTime dateTime);
}
