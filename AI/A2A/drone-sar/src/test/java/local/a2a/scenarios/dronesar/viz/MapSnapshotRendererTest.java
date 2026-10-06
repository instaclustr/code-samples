package local.a2a.scenarios.dronesar.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import local.a2a.scenarios.dronesar.model.DroneTelemetry;
import local.a2a.scenarios.dronesar.model.Mission;
import local.a2a.scenarios.dronesar.model.Position;
import local.a2a.scenarios.dronesar.sim.MissionLoader;
import local.a2a.scenarios.dronesar.sim.SimulationEngine;
import local.a2a.scenarios.dronesar.sim.SimulationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapSnapshotRendererTest {

    @TempDir
    Path tempDir;

    @Test
    void writesSvgAndPngSnapshots() throws Exception {
        Path missionPath =
                Path.of("../missions/test-mission-fast.json").toAbsolutePath().normalize();
        Mission mission = MissionLoader.load(missionPath);
        List<DroneTelemetry> telemetry = new ArrayList<>();
        SimulationEngine engine = new SimulationEngine(mission);
        SimulationResult result = engine.run(60, telemetry::add);
        MissionReplay replay = ReplayExporter.build(mission, engine.world(), telemetry, result);

        MapSnapshotRenderer.write(tempDir, mission, engine.world(), telemetry, replay);

        String svg = Files.readString(tempDir.resolve("snapshot.svg"));
        assertTrue(svg.contains("sar-test-fast"));
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("Mission status"));
        assertTrue(Files.exists(tempDir.resolve("snapshot.png")));
        assertTrue(Files.size(tempDir.resolve("snapshot.png")) > 1000);

        assertTrue(replay.frames().size() > 0);
        assertTrue(replay.searchedCells().size() > 0);
        assertTrue(replay.summary().events() != null && !replay.summary().events().isEmpty());
    }
}
