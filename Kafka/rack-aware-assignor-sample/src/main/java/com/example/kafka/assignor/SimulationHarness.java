package com.example.kafka.assignor;

import org.apache.kafka.common.TopicPartition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SimulationHarness {
    public static void main(String[] args) {
        int topics = 3;
        int partitionsPerTopic = 30;
        int membersPerRack = 8;
        List<String> racks = List.of("rack-a", "rack-b", "rack-c");

        runProfile(
                "perfect-metadata",
                generatePartitions(topics, partitionsPerTopic, racks),
                generateMembers(topics, membersPerRack, racks)
        );
        runProfile(
                "imperfect-metadata",
                generatePartitionsWithMissingRackMetadata(topics, partitionsPerTopic, racks),
                generateMembersWithMissingRackMetadata(topics, membersPerRack, racks)
        );
    }

    private static void runProfile(String name, List<PartitionMetadata> partitions, List<ConsumerMember> members) {
        RackAwareAssignor assignor = new RackAwareAssignor();
        AssignmentResult aware = assignor.assign(partitions, members);
        AssignmentResult baseline = assignor.randomBaseline(partitions, members);

        System.out.println("=== Rack-aware assignor simulation: " + name + " ===");
        System.out.printf("Partitions assigned: %d%n", aware.totalAssigned());
        System.out.printf("Local assignments: %d%n", aware.localAssignments());
        System.out.printf("Locality hit rate (rack-aware): %.2f%%%n", aware.localityHitRate() * 100.0);
        System.out.printf("Locality hit rate (baseline): %.2f%%%n", baseline.localityHitRate() * 100.0);
    }

    static List<PartitionMetadata> generatePartitions(int topics, int partitionsPerTopic, List<String> racks) {
        List<PartitionMetadata> out = new ArrayList<>();
        for (int t = 0; t < topics; t++) {
            String topic = "topic-" + t;
            for (int p = 0; p < partitionsPerTopic; p++) {
                String rack = racks.get(Math.floorMod(t + p, racks.size()));
                out.add(new PartitionMetadata(new TopicPartition(topic, p), rack));
            }
        }
        return out;
    }

    static List<ConsumerMember> generateMembers(int topics, int membersPerRack, List<String> racks) {
        List<ConsumerMember> out = new ArrayList<>();
        int id = 1;
        Set<String> allTopics = new HashSet<>();
        for (int t = 0; t < topics; t++) {
            allTopics.add("topic-" + t);
        }

        for (String rack : racks) {
            for (int i = 0; i < membersPerRack; i++) {
                out.add(new ConsumerMember("member-" + (id++), rack, allTopics));
            }
        }
        return out;
    }

    static List<PartitionMetadata> generatePartitionsWithMissingRackMetadata(
            int topics,
            int partitionsPerTopic,
            List<String> racks
    ) {
        List<PartitionMetadata> out = new ArrayList<>();
        for (int t = 0; t < topics; t++) {
            String topic = "topic-" + t;
            for (int p = 0; p < partitionsPerTopic; p++) {
                String rack = racks.get(Math.floorMod(t + p, racks.size()));
                // Intentionally remove rack metadata for some partitions.
                if (Math.floorMod(t * partitionsPerTopic + p, 5) == 0) {
                    rack = null;
                }
                out.add(new PartitionMetadata(new TopicPartition(topic, p), rack));
            }
        }
        return out;
    }

    static List<ConsumerMember> generateMembersWithMissingRackMetadata(
            int topics,
            int membersPerRack,
            List<String> racks
    ) {
        List<ConsumerMember> out = new ArrayList<>();
        int id = 1;
        Set<String> allTopics = new HashSet<>();
        for (int t = 0; t < topics; t++) {
            allTopics.add("topic-" + t);
        }

        for (String rack : racks) {
            for (int i = 0; i < membersPerRack; i++) {
                String memberRack = rack;
                // Intentionally remove rack metadata for some members.
                if (Math.floorMod(id, 4) == 0) {
                    memberRack = null;
                }
                out.add(new ConsumerMember("member-" + (id++), memberRack, allTopics));
            }
        }
        return out;
    }
}
