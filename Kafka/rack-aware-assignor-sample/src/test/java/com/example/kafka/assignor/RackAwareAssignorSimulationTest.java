package com.example.kafka.assignor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RackAwareAssignorSimulationTest {

    @Test
    void perfectMetadata_outperformsBaseline() {
        List<String> racks = List.of("rack-a", "rack-b", "rack-c");
        List<PartitionMetadata> partitions = SimulationHarness.generatePartitions(4, 40, racks);
        List<ConsumerMember> members = SimulationHarness.generateMembers(4, 6, racks);

        RackAwareAssignor assignor = new RackAwareAssignor();
        AssignmentResult aware = assignor.assign(partitions, members);
        AssignmentResult baseline = assignor.randomBaseline(partitions, members);

        double awareRate = aware.localityHitRate();
        double baselineRate = baseline.localityHitRate();

        System.out.printf("Locality hit rate (rack-aware): %.2f%%%n", awareRate * 100.0);
        System.out.printf("Locality hit rate (baseline): %.2f%%%n", baselineRate * 100.0);

        assertTrue(awareRate >= baselineRate,
                "Rack-aware assignor should be at least as good as baseline");
        assertTrue(awareRate >= 0.60,
                "Expected meaningful locality improvement in this setup");
    }

    @Test
    void imperfectMetadata_stillImprovesOverBaseline() {
        List<String> racks = List.of("rack-a", "rack-b", "rack-c");
        List<PartitionMetadata> partitions = SimulationHarness.generatePartitionsWithMissingRackMetadata(4, 40, racks);
        List<ConsumerMember> members = SimulationHarness.generateMembersWithMissingRackMetadata(4, 6, racks);

        RackAwareAssignor assignor = new RackAwareAssignor();
        AssignmentResult aware = assignor.assign(partitions, members);
        AssignmentResult baseline = assignor.randomBaseline(partitions, members);

        double awareRate = aware.localityHitRate();
        double baselineRate = baseline.localityHitRate();

        System.out.printf("Imperfect metadata locality (rack-aware): %.2f%%%n", awareRate * 100.0);
        System.out.printf("Imperfect metadata locality (baseline): %.2f%%%n", baselineRate * 100.0);

        assertTrue(awareRate >= baselineRate,
                "Rack-aware assignor should still outperform baseline with partial metadata");
        assertTrue(awareRate >= 0.40,
                "Expected non-trivial locality with missing metadata");
        assertTrue(awareRate < 1.00,
                "Imperfect metadata should produce less than perfect locality");
    }
}
