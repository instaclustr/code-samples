package local.a2a.scenarios.dronesar.viz;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import local.a2a.scenarios.dronesar.model.DroneTelemetry;
import local.a2a.scenarios.dronesar.model.Mission;
import local.a2a.scenarios.dronesar.sim.MissionLoader;
import local.a2a.scenarios.dronesar.sim.SimulationResult;
import local.a2a.scenarios.dronesar.world.GridWorld;

public final class VizArtifacts {
    private VizArtifacts() {}

    public static void write(
            Path outDir,
            Path replayHtmlTemplate,
            Mission mission,
            GridWorld world,
            List<DroneTelemetry> telemetry,
            SimulationResult result)
            throws IOException {
        write(outDir, replayHtmlTemplate, mission, world, telemetry, result, null);
    }

    public static void write(
            Path outDir,
            Path replayHtmlTemplate,
            Mission mission,
            GridWorld world,
            List<DroneTelemetry> telemetry,
            SimulationResult result,
            MissionReplay.CopilotReplay copilot)
            throws IOException {
        Files.createDirectories(outDir);
        ObjectMapper json = MissionLoader.mapper();
        MissionReplay replay = ReplayExporter.build(mission, world, telemetry, result, copilot);
        json.writeValue(outDir.resolve("replay.json").toFile(), replay);
        MapSnapshotRenderer.write(outDir, mission, world, telemetry, replay);
        if (replayHtmlTemplate != null && Files.isRegularFile(replayHtmlTemplate)) {
            Files.copy(replayHtmlTemplate, outDir.resolve("replay.html"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
