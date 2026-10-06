package local.a2a.scenarios.dronesar.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import local.a2a.scenarios.dronesar.model.DroneTelemetry;
import local.a2a.scenarios.dronesar.model.Mission;
import local.a2a.scenarios.dronesar.sim.MissionLoader;
import local.a2a.scenarios.dronesar.sim.SimulationEngine;
import local.a2a.scenarios.dronesar.sim.SimulationResult;
import local.a2a.scenarios.dronesar.viz.VizArtifacts;

/** CLI mission runner: JSON mission file → stdout telemetry + summary (Phase 1). */
public final class MissionRunner {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: MissionRunner <mission.json> [maxTicks] [--sample] [--viz-out <dir>]");
            System.err.println("  --sample       emit every 10th tick only (shorter log)");
            System.err.println("  --viz-out dir  write replay.json, snapshot.svg/png, replay.html");
            System.exit(1);
        }
        Path missionPath = Path.of(args[0]);
        int maxTicks = 600;
        boolean sample = false;
        Path vizOut = null;
        for (int i = 1; i < args.length; i++) {
            if ("--sample".equals(args[i])) {
                sample = true;
            } else if ("--viz-out".equals(args[i]) && i + 1 < args.length) {
                vizOut = Path.of(args[++i]);
            } else {
                maxTicks = Integer.parseInt(args[i]);
            }
        }

        Mission mission = MissionLoader.load(missionPath);
        ObjectMapper json = MissionLoader.mapper();

        System.out.println("=== drone-sar Phase 1 sim ===");
        System.out.println("missionId=" + mission.missionId() + " drones=" + mission.drones().size()
                + " maxTicks=" + maxTicks);
        System.out.println("--- telemetry (JSON lines, ~1 Hz) ---");

        SimulationEngine engine = new SimulationEngine(mission);
        final int tickLimit = maxTicks;
        boolean finalSample = sample;
        List<DroneTelemetry> allTelemetry = vizOut != null ? new ArrayList<>() : null;
        SimulationResult result = engine.run(maxTicks, tel -> {
            if (allTelemetry != null) {
                allTelemetry.add(tel);
            }
            if (finalSample && tel.tick() % 10 != 0 && tel.tick() < tickLimit - 5) {
                return;
            }
            try {
                System.out.println(json.writeValueAsString(tel));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        System.out.println("--- summary ---");
        System.out.println("ticksRun=" + result.ticksRun());
        System.out.println("searchedCells=" + result.searchedCells());
        System.out.println("allRtbLanded=" + result.allRtbLanded());
        System.out.println("anyEmergencyLand=" + result.anyEmergencyLand());
        System.out.println("anyGeofenceViolation=" + result.anyGeofenceViolation());
        System.out.println("allTargetsFound=" + result.allTargetsFound());
        System.out.println("foundTargetIds=" + result.foundTargetIds());
        if (result.fleetRtbTick() != null) {
            System.out.println("fleetRtbTick=" + result.fleetRtbTick() + " (all targets found)");
        }
        System.out.println("successCriteriaMet=" + result.successCriteriaMet());
        System.out.println("--- final drone states ---");
        for (DroneTelemetry t : result.lastTelemetry()) {
            System.out.println(t.droneId() + " mode=" + t.mode() + " batteryPct=" + t.batteryPct()
                    + " pos=(" + t.position().x() + "," + t.position().y() + ")");
        }

        if (vizOut != null && allTelemetry != null) {
            Path replayTemplate = missionPath.getParent().getParent().resolve("viz/replay.html");
            VizArtifacts.write(vizOut, replayTemplate, mission, engine.world(), allTelemetry, result);
            System.out.println("--- viz artifacts ---");
            System.out.println("replay.json=" + vizOut.resolve("replay.json").toAbsolutePath());
            System.out.println("snapshot.svg=" + vizOut.resolve("snapshot.svg").toAbsolutePath());
            System.out.println("snapshot.png=" + vizOut.resolve("snapshot.png").toAbsolutePath());
            System.out.println("replay.html=" + vizOut.resolve("replay.html").toAbsolutePath());
            System.out.println("Open replay: cd " + vizOut.toAbsolutePath() + " && python3 -m http.server 8765");
        }

        System.exit(result.successCriteriaMet() ? 0 : 1);
    }
}
