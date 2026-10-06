package local.a2a.scenarios.dronesar.flock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import local.a2a.scenarios.dronesar.model.DroneTelemetry;
import local.a2a.scenarios.dronesar.model.Mission;
import local.a2a.scenarios.dronesar.sim.MissionLoader;
import local.a2a.scenarios.dronesar.sim.SimulationEngine;
import local.a2a.scenarios.dronesar.sim.SimulationResult;
import org.junit.jupiter.api.Test;

class ExpandedSearchTest {

    @Test
    void plannerProducesFollowOnPasses() throws Exception {
        Path missionPath =
                Path.of("../missions/test-mission-multi-target.json").toAbsolutePath().normalize();
        Mission mission = MissionLoader.load(missionPath);
        var world = new SimulationEngine(mission).world();
        var target = mission.targets().get(1);
        var sector = SectorAllocator.assign(mission, world).stream()
                .filter(s -> "d-03".equals(s.droneId()))
                .findFirst()
                .orElseThrow();
        var pass0 = ExpandedSearchPlanner.nextPass(
                target, sector.searchPolygon(), world, "d-03", 0,
                sector.laneXMin(), sector.laneXMax(), sector.laneYMin(), sector.laneYMax(), sector.laneIndex(), 80, 80);
        var pass1 = ExpandedSearchPlanner.nextPass(
                target, sector.searchPolygon(), world, "d-03", 1,
                sector.laneXMin(), sector.laneXMax(), sector.laneYMin(), sector.laneYMax(), sector.laneIndex(), 80, 80);
        assertTrue(pass0.size() >= 2);
        assertTrue(pass1.size() >= 2);
    }

    @Test
    void multiTargetFindsT2DuringExpandedSearchBeforeBatteryRtb() throws Exception {
        Path missionPath =
                Path.of("../missions/test-mission-multi-target.json").toAbsolutePath().normalize();
        Mission mission = MissionLoader.load(missionPath);
        List<DroneTelemetry> telemetry = new ArrayList<>();
        SimulationResult result = new SimulationEngine(mission).run(500, telemetry::add);
        assertTrue(result.allTargetsFound());

        long t2FoundTick = telemetry.stream()
                .filter(t -> t.detection() != null && "t2".equals(t.detection().targetId()))
                .mapToLong(DroneTelemetry::tick)
                .min()
                .orElseThrow();
        assertTrue(t2FoundTick < 120, "t2 should be found during expanded search, not on late RTB");

        long firstExpandedTick = telemetry.stream()
                .filter(t -> "expanded".equals(t.searchPhase())
                        && ("d-03".equals(t.droneId()) || "d-04".equals(t.droneId())))
                .mapToLong(DroneTelemetry::tick)
                .min()
                .orElse(Long.MAX_VALUE);
        assertTrue(firstExpandedTick < t2FoundTick, "expanded search should start before t2 is found");
    }

    @Test
    void expandedSearchEventsEmitted() throws Exception {
        Path missionPath =
                Path.of("../missions/test-mission-multi-target.json").toAbsolutePath().normalize();
        Mission mission = MissionLoader.load(missionPath);
        List<DroneTelemetry> telemetry = new ArrayList<>();
        SimulationEngine engine = new SimulationEngine(mission);
        SimulationResult result = engine.run(500, telemetry::add);
        var replay = local.a2a.scenarios.dronesar.viz.ReplayExporter.build(
                mission, engine.world(), telemetry, result);
        assertFalse(replay.summary().events().stream()
                .filter(e -> "EXPANDED_SEARCH".equals(e.type()))
                .toList()
                .isEmpty());
    }
}
