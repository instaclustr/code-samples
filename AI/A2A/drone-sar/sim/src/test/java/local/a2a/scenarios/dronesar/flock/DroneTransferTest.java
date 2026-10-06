package local.a2a.scenarios.dronesar.flock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import local.a2a.scenarios.dronesar.model.DroneTelemetry;
import local.a2a.scenarios.dronesar.model.Mission;
import local.a2a.scenarios.dronesar.sim.MissionLoader;
import local.a2a.scenarios.dronesar.sim.SimulationEngine;
import local.a2a.scenarios.dronesar.sim.SimulationResult;
import local.a2a.scenarios.dronesar.world.GridWorld;
import org.junit.jupiter.api.Test;

class DroneTransferTest {

    @Test
    void assignForTargetCreatesNarrowerLanesWithMoreDrones() throws Exception {
        Path missionPath =
                Path.of("../missions/test-mission-multi-target.json").toAbsolutePath().normalize();
        Mission mission = MissionLoader.load(missionPath);
        var world = new GridWorld(mission);
        var twoLane = SectorAllocator.assignForTarget(mission, world, "t2", List.of("d-03", "d-04"));
        var fourLane = SectorAllocator.assignForTarget(
                mission, world, "t2", List.of("d-01", "d-02", "d-03", "d-04"));
        assertEquals(2, twoLane.size());
        assertEquals(4, fourLane.size());
        double twoLaneWidth = twoLane.get(0).laneXMax() - twoLane.get(0).laneXMin();
        double fourLaneWidth = fourLane.get(0).laneXMax() - fourLane.get(0).laneXMin();
        assertTrue(fourLaneWidth < twoLaneWidth, "more drones → narrower lanes → better coverage");
    }

    @Test
    void transferEmitsEventsInReplay() throws Exception {
        Path missionPath =
                Path.of("../missions/test-mission-multi-target.json").toAbsolutePath().normalize();
        Mission mission = MissionLoader.load(missionPath);
        List<DroneTelemetry> telemetry = new ArrayList<>();
        SimulationEngine engine = new SimulationEngine(mission);
        SimulationResult result = engine.run(500, telemetry::add);
        var replay = local.a2a.scenarios.dronesar.viz.ReplayExporter.build(
                mission, engine.world(), telemetry, result);
        long transfers = replay.summary().events().stream()
                .filter(e -> "DRONE_TRANSFER".equals(e.type()))
                .count();
        assertTrue(transfers >= 2, "transferred drones should appear in event log");
    }

    @Test
    void threeTargetMissionFindsAllWithProgressiveTransfer() throws Exception {
        Path missionPath =
                Path.of("../missions/test-mission-three-target.json").toAbsolutePath().normalize();
        Mission mission = MissionLoader.load(missionPath);
        List<DroneTelemetry> telemetry = new ArrayList<>();
        SimulationResult result = new SimulationEngine(mission).run(600, telemetry::add);
        assertTrue(result.allTargetsFound(), "all three targets should be found");
        assertEquals(3, result.foundTargetIds().size());
        assertTrue(result.foundTargetIds().containsAll(List.of("t1", "t2", "t3")));
        assertTrue(result.allRtbLanded());

        long t1Found = telemetry.stream()
                .filter(t -> t.detection() != null && "t1".equals(t.detection().targetId()))
                .mapToLong(DroneTelemetry::tick)
                .min()
                .orElseThrow();
        assertTrue(telemetry.stream().anyMatch(t -> t.tick() == t1Found
                && t.transferredFromTargetId() != null
                && "t1".equals(t.transferredFromTargetId())));

        for (DroneTelemetry tel : telemetry) {
            if (tel.tick() > t1Found && tel.tick() < result.fleetRtbTick()) {
                assertTrue(
                        List.of("t2", "t3").contains(tel.assignedTargetId()),
                        tel.droneId() + " should only search remaining targets after t1 found");
            }
        }
    }
}
