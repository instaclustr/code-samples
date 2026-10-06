package local.a2a.scenarios.dronesar.flock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import local.a2a.scenarios.dronesar.model.DroneMode;
import local.a2a.scenarios.dronesar.model.DroneTelemetry;
import local.a2a.scenarios.dronesar.model.Mission;
import local.a2a.scenarios.dronesar.sim.MissionLoader;
import local.a2a.scenarios.dronesar.sim.SimulationEngine;
import local.a2a.scenarios.dronesar.sim.SimulationResult;
import local.a2a.scenarios.dronesar.world.GridWorld;
import org.junit.jupiter.api.Test;

class SimulationEngineTest {

    @Test
    void assignsOneSectorPerDrone() throws Exception {
        Path missionPath = Path.of("../missions/sample-mission.json").toAbsolutePath().normalize();
        Mission mission = MissionLoader.load(missionPath);
        GridWorld world = new GridWorld(mission);
        assertEquals(mission.drones().size(), SectorAllocator.assign(mission, world).size());
    }

    @Test
    void assignsDronesAcrossTargets() throws Exception {
        Path missionPath =
                Path.of("../missions/test-mission-multi-target.json").toAbsolutePath().normalize();
        Mission mission = MissionLoader.load(missionPath);
        List<SectorAllocator.GridLaneSector> sectors = SectorAllocator.assign(mission, new GridWorld(mission));
        assertEquals(4, sectors.size());
        long t1 = sectors.stream().filter(s -> "t1".equals(s.targetId())).count();
        long t2 = sectors.stream().filter(s -> "t2".equals(s.targetId())).count();
        assertEquals(2, t1);
        assertEquals(2, t2);
    }

    @Test
    void fastMissionRtbWithoutGeofenceViolations() throws Exception {
        Path missionPath =
                Path.of("../missions/test-mission-fast.json").toAbsolutePath().normalize();
        Mission mission = MissionLoader.load(missionPath);
        SimulationResult result = new SimulationEngine(mission).run(500, tel -> {});
        assertFalse(result.anyGeofenceViolation(), "drones should not remain inside no-fly zones");
        assertFalse(result.anyEmergencyLand(), "drones should RTB before battery depletion");
        assertTrue(result.allRtbLanded(), "all drones should land at base");
        assertTrue(result.searchedCells() > 0);
        assertTrue(result.successCriteriaMet());
    }

    @Test
    void ordersFleetRtbWhenTargetDetected() throws Exception {
        Path missionPath =
                Path.of("../missions/test-mission-fast.json").toAbsolutePath().normalize();
        Mission mission = MissionLoader.load(missionPath);
        List<DroneTelemetry> telemetry = new ArrayList<>();
        SimulationResult result = new SimulationEngine(mission).run(500, telemetry::add);
        assertTrue(result.allTargetsFound());
        long firstDetectionTick = telemetry.stream()
                .filter(t -> t.detection() != null)
                .mapToLong(DroneTelemetry::tick)
                .min()
                .orElseThrow();
        assertEquals(firstDetectionTick, result.fleetRtbTick(), "single target RTB on first detection");
        for (DroneTelemetry tel : telemetry) {
            if (tel.tick() > firstDetectionTick) {
                assertTrue(
                        tel.mode() == DroneMode.RTB || tel.mode() == DroneMode.LANDED,
                        tel.droneId() + " should RTB after target found at tick " + tel.tick());
            }
        }
    }

    @Test
    void multiTargetTransfersSearchersWhenFirstTargetFound() throws Exception {
        Path missionPath =
                Path.of("../missions/test-mission-multi-target.json").toAbsolutePath().normalize();
        Mission mission = MissionLoader.load(missionPath);
        List<DroneTelemetry> telemetry = new ArrayList<>();
        SimulationResult result = new SimulationEngine(mission).run(500, telemetry::add);
        assertTrue(result.allTargetsFound());
        assertEquals(List.of("t1", "t2"), result.foundTargetIds());
        assertTrue(result.allRtbLanded());

        long t1FoundTick = telemetry.stream()
                .filter(t -> t.detection() != null && "t1".equals(t.detection().targetId()))
                .mapToLong(DroneTelemetry::tick)
                .min()
                .orElseThrow();

        assertTrue(telemetry.stream().anyMatch(t -> t.tick() == t1FoundTick
                && "d-01".equals(t.droneId())
                && t.transferredFromTargetId() != null
                && "t2".equals(t.assignedTargetId())));

        for (DroneTelemetry tel : telemetry) {
            if (tel.tick() > t1FoundTick && tel.tick() < result.fleetRtbTick()) {
                assertEquals("t2", tel.assignedTargetId(), tel.droneId() + " should search t2 after transfer");
                assertTrue(
                        tel.mode() == DroneMode.SEARCH || tel.mode() == DroneMode.RTB,
                        tel.droneId() + " should keep working until fleet RTB");
            }
        }

        long fleetRtbTick = result.fleetRtbTick();
        assertTrue(fleetRtbTick > t1FoundTick);
        for (String id : List.of("d-01", "d-02", "d-03", "d-04")) {
            assertTrue(telemetry.stream().anyMatch(t -> t.tick() >= fleetRtbTick
                    && id.equals(t.droneId())
                    && (t.mode() == DroneMode.RTB || t.mode() == DroneMode.LANDED)));
        }
    }
}
