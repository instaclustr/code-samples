package local.a2a.scenarios.dronesar.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Streaming artifact shape aligned with design doc §5 (~1 Hz). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DroneTelemetry(
        String droneId,
        long tick,
        Position position,
        double headingDeg,
        double batteryPct,
        DroneMode mode,
        String sectorId,
        String assignedTargetId,
        boolean searchLaneComplete,
        String searchPhase,
        int supplementalPassCount,
        String transferredFromTargetId,
        Detection detection,
        String photoRef,
        Boolean geofenceViolation) {}
