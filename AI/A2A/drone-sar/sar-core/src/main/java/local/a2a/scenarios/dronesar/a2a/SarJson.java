package local.a2a.scenarios.dronesar.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import local.a2a.scenarios.dronesar.model.DroneTelemetry;
import local.a2a.scenarios.dronesar.sim.MissionLoader;
import local.a2a.scenarios.dronesar.sim.SimulationResult;

public final class SarJson {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private SarJson() {}

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String telemetryArtifact(DroneTelemetry telemetry) throws Exception {
        return MAPPER.writeValueAsString(telemetry);
    }

    public static String summaryArtifact(SimulationResult result) throws Exception {
        return MAPPER.writeValueAsString(new MissionSummary(
                result.ticksRun(),
                result.searchedCells(),
                result.allRtbLanded(),
                result.anyEmergencyLand(),
                result.anyGeofenceViolation(),
                result.allTargetsFound(),
                result.fleetRtbTick(),
                result.foundTargetIds(),
                result.successCriteriaMet()));
    }

    public record MissionSummary(
            long ticksRun,
            int searchedCells,
            boolean allRtbLanded,
            boolean anyEmergencyLand,
            boolean anyGeofenceViolation,
            boolean allTargetsFound,
            Long fleetRtbTick,
            java.util.List<String> foundTargetIds,
            boolean successCriteriaMet) {}

    public static SimulationResult toSimulationResult(MissionSummary summary, java.util.List<DroneTelemetry> telemetry) {
        java.util.List<DroneTelemetry> last = SarVizExporter.lastTelemetryPerDrone(telemetry);
        return new SimulationResult(
                summary.ticksRun(),
                summary.searchedCells(),
                summary.allRtbLanded(),
                summary.anyEmergencyLand(),
                summary.anyGeofenceViolation(),
                summary.allTargetsFound(),
                summary.fleetRtbTick(),
                summary.foundTargetIds(),
                last);
    }

    public static DroneTelemetry parseTelemetry(String json) throws Exception {
        return MissionLoader.mapper().readValue(json, DroneTelemetry.class);
    }

    public static MissionSummary parseSummary(JsonNode node) throws Exception {
        return MissionLoader.mapper().treeToValue(node, MissionSummary.class);
    }
}
