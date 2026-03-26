package com.example.kafka.assignor;

import org.apache.kafka.common.TopicPartition;

public record PartitionMetadata(TopicPartition topicPartition, String leaderRack) {
}
