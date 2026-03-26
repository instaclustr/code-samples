package com.example.kafka.assignor;

import org.apache.kafka.common.TopicPartition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * KIP-1101 style locality-first assignment simulation:
 * 1) Prefer a consumer in same rack as partition leader
 * 2) Use least-loaded member to maintain balance
 * 3) Fall back to any subscriber if no rack-local candidate
 */
public class RackAwareAssignor {

    public AssignmentResult assign(
            List<PartitionMetadata> partitions,
            List<ConsumerMember> members
    ) {
        Map<String, List<TopicPartition>> assignments = new LinkedHashMap<>();
        for (ConsumerMember member : members) {
            assignments.put(member.memberId(), new ArrayList<>());
        }

        int localAssignments = 0;
        int totalAssigned = 0;

        for (PartitionMetadata partition : partitions) {
            List<ConsumerMember> subscribers = members.stream()
                    .filter(m -> m.topics().contains(partition.topicPartition().topic()))
                    .collect(Collectors.toList());
            if (subscribers.isEmpty()) {
                continue;
            }

            List<ConsumerMember> rackLocal = subscribers.stream()
                    .filter(m -> partition.leaderRack() != null && partition.leaderRack().equals(m.rack()))
                    .collect(Collectors.toList());

            ConsumerMember chosen;
            if (!rackLocal.isEmpty()) {
                chosen = leastLoaded(rackLocal, assignments);
                localAssignments++;
            } else {
                chosen = leastLoaded(subscribers, assignments);
            }

            assignments.get(chosen.memberId()).add(partition.topicPartition());
            totalAssigned++;
        }

        return new AssignmentResult(assignments, totalAssigned, localAssignments);
    }

    private ConsumerMember leastLoaded(
            List<ConsumerMember> candidates,
            Map<String, List<TopicPartition>> assignments
    ) {
        return candidates.stream()
                .min(Comparator
                        .comparingInt((ConsumerMember m) -> assignments.get(m.memberId()).size())
                        .thenComparing(ConsumerMember::memberId))
                .orElseThrow();
    }

    public AssignmentResult randomBaseline(
            List<PartitionMetadata> partitions,
            List<ConsumerMember> members
    ) {
        Map<String, List<TopicPartition>> assignments = new HashMap<>();
        for (ConsumerMember m : members) {
            assignments.put(m.memberId(), new ArrayList<>());
        }

        int local = 0;
        int total = 0;
        for (PartitionMetadata p : partitions) {
            List<ConsumerMember> subscribers = members.stream()
                    .filter(m -> m.topics().contains(p.topicPartition().topic()))
                    .collect(Collectors.toList());
            if (subscribers.isEmpty()) {
                continue;
            }
            ConsumerMember chosen = subscribers.get(Math.floorMod(p.topicPartition().partition(), subscribers.size()));
            assignments.get(chosen.memberId()).add(p.topicPartition());
            if (p.leaderRack() != null && p.leaderRack().equals(chosen.rack())) {
                local++;
            }
            total++;
        }
        return new AssignmentResult(assignments, total, local);
    }
}
