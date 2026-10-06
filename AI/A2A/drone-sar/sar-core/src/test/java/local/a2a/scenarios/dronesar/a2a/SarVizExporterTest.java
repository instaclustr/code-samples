package local.a2a.scenarios.dronesar.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import local.a2a.scenarios.dronesar.model.DroneTelemetry;
import local.a2a.scenarios.dronesar.sim.MissionLoader;
import local.a2a.scenarios.dronesar.sim.SimulationResult;
import local.a2a.scenarios.dronesar.viz.MissionReplay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SarVizExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesReplayAndSnapshotFromStreamedTelemetry() throws Exception {
        Path missionPath = Path.of("../../missions/test-mission-fast.json").toAbsolutePath().normalize();
        SarMissionRequest request = new SarMissionRequest(missionPath, 500, false);
        List<DroneTelemetry> telemetry = new ArrayList<>();
        SimulationResult result = SarSimulationRunner.run(request, new SarSimulationRunner.TickListener() {
            @Override
            public void onTick(long tick, DroneTelemetry tel) {
                telemetry.add(tel);
            }

            @Override
            public void onComplete(SimulationResult ignored) {}
        });

        SarJson.MissionSummary missionSummary = new SarJson.MissionSummary(
                result.ticksRun(),
                result.searchedCells(),
                result.allRtbLanded(),
                result.anyEmergencyLand(),
                result.anyGeofenceViolation(),
                result.allTargetsFound(),
                result.fleetRtbTick(),
                result.foundTargetIds(),
                result.successCriteriaMet());

        Path out = tempDir.resolve("viz");
        SarVizExporter.write(out, SarVizExporter.defaultReplayTemplate(request), request, telemetry, missionSummary);

        assertTrue(Files.isRegularFile(out.resolve("replay.json")));
        assertTrue(Files.isRegularFile(out.resolve("snapshot.png")));
        assertTrue(Files.isRegularFile(out.resolve("snapshot.svg")));
        assertTrue(Files.isRegularFile(out.resolve("replay.html")));
    }

    @Test
    void writesCopilotBundleIntoReplayJson() throws Exception {
        Path missionPath = Path.of("../../missions/test-mission-fast.json").toAbsolutePath().normalize();
        SarMissionRequest request = new SarMissionRequest(missionPath, 500, false);
        List<DroneTelemetry> telemetry = new ArrayList<>();
        SimulationResult result = SarSimulationRunner.run(request, new SarSimulationRunner.TickListener() {
            @Override
            public void onTick(long tick, DroneTelemetry tel) {
                telemetry.add(tel);
            }

            @Override
            public void onComplete(SimulationResult ignored) {}
        });

        SarJson.MissionSummary missionSummary = new SarJson.MissionSummary(
                result.ticksRun(),
                result.searchedCells(),
                result.allRtbLanded(),
                result.anyEmergencyLand(),
                result.anyGeofenceViolation(),
                result.allTargetsFound(),
                result.fleetRtbTick(),
                result.foundTargetIds(),
                result.successCriteriaMet());

        MissionReplay.CopilotReplay copilot = new MissionReplay.CopilotReplay(
                List.of(new MissionReplay.CopilotNarration(10, "TARGET_FOUND", "Target located.", "narrator")),
                List.of(new MissionReplay.CopilotViolation(
                        10, "INFO", "airspace-v1.general", "Monitor sector", "MONITOR", "TARGET_FOUND", "d-01")));

        Path out = tempDir.resolve("viz-copilot");
        SarVizExporter.write(out, SarVizExporter.defaultReplayTemplate(request), request, telemetry, missionSummary, copilot);

        JsonNode replay = MissionLoader.mapper().readTree(out.resolve("replay.json").toFile());
        assertNotNull(replay.path("copilot"));
        assertEquals(1, replay.path("copilot").path("narrations").size());
        assertEquals(1, replay.path("copilot").path("violations").size());
    }
}
