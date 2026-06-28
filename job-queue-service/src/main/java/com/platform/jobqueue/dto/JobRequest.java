package com.platform.jobqueue.dto;

import com.platform.jobqueue.model.Priority;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobRequest {

    @NotBlank(message = "Job name is required")
    private String name;

    private String payload;

    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    @Builder.Default
    private int maxRetries = 3;
}
