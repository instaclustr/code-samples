package local.a2a.scenarios.dronesar.a2a;

import java.util.Locale;
import java.util.stream.Collectors;

/** Deterministic fallback narrations when Ollama is disabled or unreachable. */
public final class SarEventTemplates {

    private SarEventTemplates() {}

    public static String narrate(SarMissionEvent event) {
        if (event == null) {
            return "Mission update received.";
        }
        return switch (event.type().toUpperCase(Locale.ROOT)) {
            case "MISSION_START" -> "Mission "
                    + label(event.missionId())
                    + " started; drones launching search pattern at tick "
                    + event.tick()
                    + ".";
            case "TARGET_FOUND" -> "Tick "
                    + event.tick()
                    + ": "
                    + label(event.droneId())
                    + " located target "
                    + label(event.targetId())
                    + ".";
            case "DRONE_TRANSFER" -> "Tick "
                    + event.tick()
                    + ": "
                    + label(event.droneId())
                    + " reassigned from "
                    + label(event.fromTargetId())
                    + " to help search "
                    + label(event.targetId())
                    + ".";
            case "FLEET_RTB" -> "Tick "
                    + event.tick()
                    + ": all targets accounted for; fleet ordered to return to base.";
            case "MISSION_COMPLETE" -> "Mission "
                    + label(event.missionId())
                    + " complete at tick "
                    + event.tick()
                    + (event.detail() != null ? " — " + event.detail() : ".");
            default -> "Tick " + event.tick() + ": " + event.type().replace('_', ' ') + ".";
        };
    }

    public static String narrateSummary(String missionId, SarJson.MissionSummary summary) {
        String found = summary.foundTargetIds() == null || summary.foundTargetIds().isEmpty()
                ? "none"
                : summary.foundTargetIds().stream().collect(Collectors.joining(", "));
        return "After-action: mission "
                + label(missionId)
                + " ran "
                + summary.ticksRun()
                + " ticks, searched "
                + summary.searchedCells()
                + " cells, found ["
                + found
                + "] (allTargetsFound="
                + summary.allTargetsFound()
                + "), geofence violation="
                + summary.anyGeofenceViolation()
                + ", emergency land="
                + summary.anyEmergencyLand()
                + ", all drones landed="
                + summary.allRtbLanded()
                + ", success="
                + summary.successCriteriaMet()
                + ".";
    }

    private static String label(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
