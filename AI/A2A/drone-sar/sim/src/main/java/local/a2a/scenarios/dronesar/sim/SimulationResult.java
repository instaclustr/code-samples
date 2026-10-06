package local.a2a.scenarios.dronesar.sim;

import java.util.List;
import local.a2a.scenarios.dronesar.model.DroneMode;
import local.a2a.scenarios.dronesar.model.DroneTelemetry;
import local.a2a.scenarios.dronesar.model.Mission;

public record SimulationResult(
        long ticksRun,
        int searchedCells,
        boolean allRtbLanded,
        boolean anyEmergencyLand,
        boolean anyGeofenceViolation,
        boolean allTargetsFound,
        Long fleetRtbTick,
        List<String> foundTargetIds,
        List<DroneTelemetry> lastTelemetry) {

    /** @deprecated use {@link #allTargetsFound()} */
    public boolean targetDetected() {
        return allTargetsFound;
    }

    /** @deprecated use {@link #fleetRtbTick()} */
    public Long targetFoundTick() {
        return fleetRtbTick;
    }

    public boolean successCriteriaMet() {
        return allRtbLanded && !anyEmergencyLand && !anyGeofenceViolation && searchedCells > 0;
    }

    public static SimulationResult fromRun(
            long ticks,
            Mission mission,
            List<DroneTelemetry> finalTelemetry,
            int searchedCells,
            boolean geofenceViolation,
            boolean allTargetsFound,
            Long fleetRtbTick,
            List<String> foundTargetIds) {
        boolean allLanded = finalTelemetry.stream().allMatch(t -> t.mode() == DroneMode.LANDED);
        boolean emergency = finalTelemetry.stream().anyMatch(t -> t.mode() == DroneMode.EMERGENCY_LAND);
        return new SimulationResult(
                ticks,
                searchedCells,
                allLanded,
                emergency,
                geofenceViolation,
                allTargetsFound,
                fleetRtbTick,
                foundTargetIds,
                finalTelemetry);
    }
}
