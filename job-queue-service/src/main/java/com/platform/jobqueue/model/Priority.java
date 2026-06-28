package com.platform.jobqueue.model;

import lombok.Getter;

@Getter
public enum Priority {
    HIGH(1),
    MEDIUM(5),
    LOW(10);

    private final int weight;

    Priority(int weight) {
        this.weight = weight;
    }
}
