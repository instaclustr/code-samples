package local.a2a.scenarios.dronesar.viz;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Serializable replay bundle for HTML canvas playback. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MissionReplay(
        String missionId,
        int gridCells,
        double cellSizeM,
        List<List<Double>> searchPolygon,
        List<TargetReplay> targets,
        List<List<List<Double>>> noFlyZones,
        CellPoint baseCell,
        CellPoint targetCell,
        List<String> droneIds,
        List<ReplayFrame> frames,
        List<int[]> searchedCells,
        SimulationSummary summary,
        CopilotReplay copilot) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CopilotReplay(List<CopilotNarration> narrations, List<CopilotViolation> violations) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CopilotNarration(long tick, String type, String text, String source) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CopilotViolation(
            long tick,
            String severity,
            String ruleId,
            String summary,
            String recommendedAction,
            String sourceEventType,
            String droneId) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CellPoint(int x, int y) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TargetReplay(
            String id,
            String type,
            List<List<Double>> searchPolygon,
            CellPoint lastKnownCell,
            List<String> assignedDroneIds,
            Long foundAtTick) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReplayFrame(long tick, List<DroneFrame> drones, List<String> foundTargetIds) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DroneFrame(
            String id,
            double xM,
            double yM,
            String mode,
            double batteryPct,
            String assignedTargetId,
            String sectorId,
            String detectionTargetId,
            boolean searchLaneComplete,
            String searchPhase,
            int supplementalPassCount) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MissionEvent(String type, long tick, String targetId, String droneId, String detail) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SimulationSummary(
            long ticksRun,
            int searchedCellCount,
            boolean successCriteriaMet,
            boolean allTargetsFound,
            boolean allRtbLanded,
            boolean anyGeofenceViolation,
            Long fleetRtbTick,
            List<String> foundTargetIds,
            List<String> missedTargetIds,
            List<MissionEvent> events) {}
}
