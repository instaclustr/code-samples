package local.a2a.scenarios.dronesar.flock;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import local.a2a.scenarios.dronesar.model.DroneMode;
import local.a2a.scenarios.dronesar.model.DroneTelemetry;
import local.a2a.scenarios.dronesar.model.Mission;
import local.a2a.scenarios.dronesar.sim.MissionLoader;
import local.a2a.scenarios.dronesar.sim.SimulationEngine;
import org.junit.jupiter.api.Test;

class SimDronePathingTest {

    @Test
    void kafkaSafetyTwoTargetMissionDoesNotFreezeDronesInSearch() throws Exception {
        Path missionPath =
                Path.of("../missions/test-mission-kafka-safety-2target.json").toAbsolutePath().normalize();
        Mission mission = MissionLoader.load(missionPath);
        Map<String, Integer> maxStuckStreak = new HashMap<>();
        Map<String, Integer> currentStreak = new HashMap<>();
        Map<String, Double> lastX = new HashMap<>();
        Map<String, Double> lastY = new HashMap<>();

        new SimulationEngine(mission).run(600, frame -> trackStuck(frame, maxStuckStreak, currentStreak, lastX, lastY));

        for (String droneId : java.util.List.of("d-01", "d-02")) {
            assertTrue(
                    maxStuckStreak.getOrDefault(droneId, 0) < 10,
                    droneId + " stuck in search for " + maxStuckStreak.getOrDefault(droneId, 0) + " consecutive ticks");
        }
    }

    private static void trackStuck(
            DroneTelemetry frame,
            Map<String, Integer> maxStuckStreak,
            Map<String, Integer> currentStreak,
            Map<String, Double> lastX,
            Map<String, Double> lastY) {
        if (frame.mode() != DroneMode.SEARCH) {
            currentStreak.put(frame.droneId(), 0);
            lastX.put(frame.droneId(), frame.position().x());
            lastY.put(frame.droneId(), frame.position().y());
            return;
        }
        double prevX = lastX.getOrDefault(frame.droneId(), frame.position().x());
        double prevY = lastY.getOrDefault(frame.droneId(), frame.position().y());
        boolean moved = Math.hypot(frame.position().x() - prevX, frame.position().y() - prevY) > 0.05;
        int streak = moved ? 0 : currentStreak.getOrDefault(frame.droneId(), 0) + 1;
        currentStreak.put(frame.droneId(), streak);
        maxStuckStreak.merge(frame.droneId(), streak, Math::max);
        lastX.put(frame.droneId(), frame.position().x());
        lastY.put(frame.droneId(), frame.position().y());
    }
}
