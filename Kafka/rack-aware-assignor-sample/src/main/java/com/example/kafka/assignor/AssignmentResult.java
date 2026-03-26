package com.example.kafka.assignor;

import org.apache.kafka.common.TopicPartition;

import java.util.List;
import java.util.Map;

public record AssignmentResult(
        Map<String, List<TopicPartition>> assignments,
        int totalAssigned,
        int localAssignments
) {
    public double localityHitRate() {
        if (totalAssigned == 0) {
            return 0.0;
        }
        return (double) localAssignments / totalAssigned;
    }
}
