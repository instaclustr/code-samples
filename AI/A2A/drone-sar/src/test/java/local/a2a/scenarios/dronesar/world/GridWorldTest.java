package local.a2a.scenarios.dronesar.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import local.a2a.scenarios.dronesar.model.Mission;
import local.a2a.scenarios.dronesar.sim.MissionLoader;
import org.junit.jupiter.api.Test;

class GridWorldTest {

    @Test
    void noFlyZoneUsesCellCoordinates() throws Exception {
        Path missionPath = Path.of("../missions/sample-mission.json").toAbsolutePath().normalize();
        Mission mission = MissionLoader.load(missionPath);
        GridWorld world = new GridWorld(mission);
        double cell = world.cellSizeM();
        assertTrue(world.isNoFly(50 * cell, 50 * cell));
        assertFalse(world.isNoFly(20 * cell, 20 * cell));
    }
}
