package local.a2a.scenarios.dronesar.a2a;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import local.a2a.scenarios.dronesar.model.Detection;
import local.a2a.scenarios.dronesar.model.DroneMode;
import local.a2a.scenarios.dronesar.model.DroneTelemetry;

/** Derives narratable and safety events from streaming drone telemetry. */
public final class SarSignificantEventDetector {

    private final Set<String> foundTargets = new HashSet<>();
    private final Set<String> transfers = new HashSet<>();
    private final Set<String> geofenceViolations = new HashSet<>();
    private final Set<String> lowBatteryWarnings = new HashSet<>();
    private final Set<String> emergencyLands = new HashSet<>();
    private boolean missionStartAnnounced;
    private double rtbBatteryPct = 30.0;

    public SarSignificantEventDetector configure(double rtbBatteryPct) {
        this.rtbBatteryPct = rtbBatteryPct;
        return this;
    }

    public List<SarMissionEvent> detect(DroneTelemetry telemetry, String missionId) {
        List<SarMissionEvent> events = new ArrayList<>();
        if (!missionStartAnnounced) {
            missionStartAnnounced = true;
            events.add(SarMissionEvent.of("MISSION_START", telemetry.tick(), missionId, null, null, null));
        }
        events.addAll(detectSafety(telemetry, missionId));
        if (telemetry.detection() != null && telemetry.detection().targetId() != null) {
            String targetId = telemetry.detection().targetId();
            if (foundTargets.add(targetId)) {
                events.add(SarMissionEvent.of(
                        "TARGET_FOUND",
                        telemetry.tick(),
                        missionId,
                        targetId,
                        telemetry.droneId(),
                        telemetry.detection().targetType()));
            }
        }
        if (telemetry.transferredFromTargetId() != null) {
            String key = telemetry.tick() + ":" + telemetry.droneId() + ":" + telemetry.assignedTargetId();
            if (transfers.add(key)) {
                events.add(SarMissionEvent.transfer(
                        telemetry.tick(),
                        missionId,
                        telemetry.assignedTargetId(),
                        telemetry.droneId(),
                        telemetry.transferredFromTargetId()));
            }
        }
        return events;
    }

    public List<SarMissionEvent> detectSafety(DroneTelemetry telemetry, String missionId) {
        List<SarMissionEvent> events = new ArrayList<>();
        String droneId = telemetry.droneId();
        if (Boolean.TRUE.equals(telemetry.geofenceViolation()) && geofenceViolations.add(droneId)) {
            events.add(SarMissionEvent.of(
                    "GEOFENCE_VIOLATION",
                    telemetry.tick(),
                    missionId,
                    null,
                    droneId,
                    "entered no-fly zone; escape failed"));
        }
        if (telemetry.mode() == DroneMode.EMERGENCY_LAND && emergencyLands.add(droneId)) {
            events.add(SarMissionEvent.of(
                    "EMERGENCY_LAND",
                    telemetry.tick(),
                    missionId,
                    null,
                    droneId,
                    "battery depleted — emergency land"));
        }
        if (telemetry.batteryPct() <= rtbBatteryPct
                && telemetry.mode() != DroneMode.LANDED
                && telemetry.mode() != DroneMode.EMERGENCY_LAND
                && lowBatteryWarnings.add(droneId)) {
            events.add(SarMissionEvent.of(
                    "LOW_BATTERY",
                    telemetry.tick(),
                    missionId,
                    null,
                    droneId,
                    "battery "
                            + telemetry.batteryPct()
                            + "% at or below RTB threshold "
                            + rtbBatteryPct
                            + "% while still searching"));
        }
        return events;
    }

    public static boolean isSafetyEvent(String type) {
        if (type == null) {
            return false;
        }
        return switch (type.toUpperCase()) {
            case "GEOFENCE_VIOLATION", "EMERGENCY_LAND", "LOW_BATTERY", "MISSION_SAFETY_SUMMARY" -> true;
            default -> false;
        };
    }
}
