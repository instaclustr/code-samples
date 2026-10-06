package local.a2a.scenarios.dronesar.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import local.a2a.scenarios.dronesar.model.DroneTelemetry;
import local.a2a.scenarios.dronesar.model.Mission;
import local.a2a.scenarios.dronesar.sim.MissionLoader;
import local.a2a.scenarios.dronesar.sim.SimulationResult;
import local.a2a.scenarios.dronesar.viz.MissionReplay;
import local.a2a.scenarios.dronesar.viz.VizArtifacts;
import local.a2a.scenarios.dronesar.world.GridWorld;

/** Writes Phase 1-style replay/snapshot artifacts from A2A-streamed telemetry. */
public final class SarVizExporter {

    private SarVizExporter() {}

    public static Path defaultReplayTemplate(SarMissionRequest request) {
        Path missionsDir = request.missionPath().getParent();
        if (missionsDir != null && missionsDir.getParent() != null) {
            Path candidate = missionsDir.getParent().resolve("viz/replay.html");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public static void write(
            Path outDir,
            Path replayHtmlTemplate,
            SarMissionRequest request,
            List<DroneTelemetry> telemetry,
            SarJson.MissionSummary summary)
            throws IOException {
        write(outDir, replayHtmlTemplate, request, telemetry, summary, null);
    }

    public static void write(
            Path outDir,
            Path replayHtmlTemplate,
            SarMissionRequest request,
            List<DroneTelemetry> telemetry,
            SarJson.MissionSummary summary,
            MissionReplay.CopilotReplay copilot)
            throws IOException {
        if (telemetry == null || telemetry.isEmpty()) {
            throw new IllegalArgumentException("No telemetry to visualize");
        }
        if (summary == null) {
            throw new IllegalArgumentException("Mission summary missing");
        }
        Mission mission = MissionLoader.load(request.missionPath());
        GridWorld world = new GridWorld(mission);
        markSearchedFromTelemetry(world, telemetry);
        SimulationResult result = SarJson.toSimulationResult(summary, telemetry);
        Path template = replayHtmlTemplate != null ? replayHtmlTemplate : defaultReplayTemplate(request);
        VizArtifacts.write(outDir, template, mission, world, telemetry, result, copilot);
    }

    static void markSearchedFromTelemetry(GridWorld world, List<DroneTelemetry> telemetry) {
        for (DroneTelemetry tel : telemetry) {
            int cx = world.cellFromMeters(tel.position().x());
            int cy = world.cellFromMeters(tel.position().y());
            world.markSearched(cx, cy);
        }
    }

    static List<DroneTelemetry> lastTelemetryPerDrone(List<DroneTelemetry> telemetry) {
        Map<String, DroneTelemetry> last = new LinkedHashMap<>();
        for (DroneTelemetry tel : telemetry) {
            DroneTelemetry prev = last.get(tel.droneId());
            if (prev == null || tel.tick() >= prev.tick()) {
                last.put(tel.droneId(), tel);
            }
        }
        return last.values().stream()
                .sorted(Comparator.comparing(DroneTelemetry::droneId))
                .toList();
    }

    public static SarJson.MissionSummary readSummary(JsonNode node) throws IOException {
        return MissionLoader.mapper().treeToValue(node, SarJson.MissionSummary.class);
    }

    public static DroneTelemetry readTelemetry(JsonNode node) throws IOException {
        return MissionLoader.mapper().treeToValue(node, DroneTelemetry.class);
    }

    public static DroneTelemetry readTelemetry(String json) throws IOException {
        return MissionLoader.mapper().readValue(json, DroneTelemetry.class);
    }

    public static List<DroneTelemetry> copyTelemetry(List<DroneTelemetry> telemetry) {
        return telemetry == null ? List.of() : new ArrayList<>(telemetry);
    }
}
