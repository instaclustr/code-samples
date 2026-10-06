package local.a2a.scenarios.dronesar.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import local.a2a.scenarios.dronesar.model.Mission;
import local.a2a.scenarios.dronesar.sim.MissionLoader;
import local.a2a.scenarios.dronesar.sim.SimulationEngine;
import local.a2a.scenarios.dronesar.sim.SimulationResult;
import org.junit.jupiter.api.Test;

class DetourPlannerTest {

    @Test
    void plansCornerDetourAroundBlockingZone() throws Exception {
        Path missionPath =
                Path.of("../missions/test-mission-three-target-geofence.json").toAbsolutePath().normalize();
        Mission mission = MissionLoader.load(missionPath);
        GridWorld world = new GridWorld(mission);
        assertTrue(DetourPlanner.segmentBlocked(10, 10, 50, 50, world));
        var detour = DetourPlanner.planDetour(10, 10, 50, 50, world);
        assertFalse(detour.isEmpty());
    }

    @Test
    void geofenceMissionCompletesWithoutViolationsWithDetours() throws Exception {
        Path missionPath =
                Path.of("../missions/test-mission-three-target-geofence.json").toAbsolutePath().normalize();
        Mission mission = MissionLoader.load(missionPath);
        SimulationResult result = new SimulationEngine(mission).run(600, tel -> {});
        assertTrue(result.allTargetsFound());
        assertFalse(result.anyGeofenceViolation(), "detours should avoid no-fly zones");
        assertTrue(result.successCriteriaMet());
    }
}
