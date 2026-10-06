package local.a2a.scenarios.dronesar.viz;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import local.a2a.scenarios.dronesar.flock.SectorAllocator;
import local.a2a.scenarios.dronesar.model.Detection;
import local.a2a.scenarios.dronesar.model.DroneMode;
import local.a2a.scenarios.dronesar.model.DroneTelemetry;
import local.a2a.scenarios.dronesar.model.Mission;
import local.a2a.scenarios.dronesar.model.Target;
import local.a2a.scenarios.dronesar.sim.SimulationResult;
import local.a2a.scenarios.dronesar.world.GridWorld;

public final class ReplayExporter {
    private ReplayExporter() {}

    public static MissionReplay build(
            Mission mission, GridWorld world, List<DroneTelemetry> telemetry, SimulationResult result) {
        return build(mission, world, telemetry, result, null);
    }

    public static MissionReplay build(
            Mission mission,
            GridWorld world,
            List<DroneTelemetry> telemetry,
            SimulationResult result,
            MissionReplay.CopilotReplay copilot) {
        Target primary = mission.targets().get(0);
        MissionReplay.CellPoint base =
                new MissionReplay.CellPoint(mission.base().cellX(), mission.base().cellY());
        MissionReplay.CellPoint target = primary.lastKnownCell() != null
                ? new MissionReplay.CellPoint(
                        primary.lastKnownCell().x(), primary.lastKnownCell().y())
                : null;

        Map<String, List<String>> dronesByTarget = dronesByTarget(mission, world);
        Map<String, Long> foundAtTick = foundAtTickByTarget(telemetry);
        List<MissionReplay.TargetReplay> targetReplays = mission.targets().stream()
                .map(t -> new MissionReplay.TargetReplay(
                        t.id(),
                        t.type(),
                        t.searchArea().polygon(),
                        t.lastKnownCell() != null
                                ? new MissionReplay.CellPoint(
                                        t.lastKnownCell().x(), t.lastKnownCell().y())
                                : null,
                        dronesByTarget.getOrDefault(t.id(), List.of()),
                        foundAtTick.get(t.id())))
                .toList();

        Map<Long, List<DroneTelemetry>> byTick = new TreeMap<>();
        for (DroneTelemetry tel : telemetry) {
            byTick.computeIfAbsent(tel.tick(), t -> new ArrayList<>()).add(tel);
        }

        List<String> missedTargetIds = mission.targets().stream()
                .map(Target::id)
                .filter(id -> !result.foundTargetIds().contains(id))
                .toList();
        List<MissionReplay.MissionEvent> events = buildEvents(mission, telemetry, result, dronesByTarget, missedTargetIds);
        Set<String> cumulativeFound = new LinkedHashSet<>();
        List<MissionReplay.ReplayFrame> frames = new ArrayList<>();
        for (Map.Entry<Long, List<DroneTelemetry>> entry : byTick.entrySet()) {
            long tick = entry.getKey();
            for (DroneTelemetry tel : entry.getValue()) {
                Detection det = tel.detection();
                if (det != null && det.targetId() != null) {
                    cumulativeFound.add(det.targetId());
                }
            }
            List<MissionReplay.DroneFrame> drones = entry.getValue().stream()
                    .sorted(Comparator.comparing(DroneTelemetry::droneId))
                    .map(t -> new MissionReplay.DroneFrame(
                            t.droneId(),
                            t.position().x(),
                            t.position().y(),
                            t.mode().json(),
                            t.batteryPct(),
                            t.assignedTargetId(),
                            t.sectorId(),
                            t.detection() != null ? t.detection().targetId() : null,
                            t.searchLaneComplete(),
                            t.searchPhase(),
                            t.supplementalPassCount()))
                    .toList();
            frames.add(new MissionReplay.ReplayFrame(tick, drones, List.copyOf(cumulativeFound)));
        }

        List<String> droneIds = mission.drones().stream().map(d -> d.id()).toList();
        List<List<List<Double>>> noFly = world.noFlyPolygons();
        MissionReplay.SimulationSummary summary = new MissionReplay.SimulationSummary(
                result.ticksRun(),
                result.searchedCells(),
                result.successCriteriaMet(),
                result.allTargetsFound(),
                result.allRtbLanded(),
                result.anyGeofenceViolation(),
                result.fleetRtbTick(),
                result.foundTargetIds(),
                missedTargetIds.isEmpty() ? null : missedTargetIds,
                events.isEmpty() ? null : events);

        return new MissionReplay(
                mission.missionId(),
                world.gridCells(),
                world.cellSizeM(),
                primary.searchArea().polygon(),
                targetReplays,
                noFly.isEmpty() ? null : noFly,
                base,
                target,
                droneIds,
                frames,
                world.searchedCellCoords(),
                summary,
                copilot);
    }

    private static Map<String, List<String>> dronesByTarget(Mission mission, GridWorld world) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (SectorAllocator.GridLaneSector sector : SectorAllocator.assign(mission, world)) {
            map.computeIfAbsent(sector.targetId(), k -> new ArrayList<>()).add(sector.droneId());
        }
        return map;
    }

    private static Map<String, Long> foundAtTickByTarget(List<DroneTelemetry> telemetry) {
        Map<String, Long> found = new LinkedHashMap<>();
        for (DroneTelemetry tel : telemetry) {
            Detection det = tel.detection();
            if (det == null || det.targetId() == null) {
                continue;
            }
            found.putIfAbsent(det.targetId(), tel.tick());
        }
        return found;
    }

    private static List<MissionReplay.MissionEvent> buildEvents(
            Mission mission,
            List<DroneTelemetry> telemetry,
            SimulationResult result,
            Map<String, List<String>> dronesByTarget,
            List<String> missedTargetIds) {
        List<MissionReplay.MissionEvent> events = new ArrayList<>();
        Set<String> foundTargets = new LinkedHashSet<>();
        Set<String> laneCompleteEmitted = new LinkedHashSet<>();
        Set<String> expandedSearchEmitted = new LinkedHashSet<>();
        Set<String> geofenceEmitted = new LinkedHashSet<>();
        Set<String> emergencyEmitted = new LinkedHashSet<>();
        Set<String> lowBatteryEmitted = new LinkedHashSet<>();
        Map<String, DroneMode> prevMode = new LinkedHashMap<>();
        double rtbBatteryPct = mission.rulesOrDefault().rtbBatteryPct();

        for (DroneTelemetry tel : telemetry) {
            DroneMode mode = tel.mode();
            DroneMode previous = prevMode.put(tel.droneId(), mode);
            Detection det = tel.detection();
            if (det != null && det.targetId() != null && foundTargets.add(det.targetId())) {
                events.add(new MissionReplay.MissionEvent(
                        "TARGET_FOUND",
                        tel.tick(),
                        det.targetId(),
                        tel.droneId(),
                        "confidence " + det.confidence()));
            }
            if (tel.transferredFromTargetId() != null) {
                events.add(new MissionReplay.MissionEvent(
                        "DRONE_TRANSFER",
                        tel.tick(),
                        tel.assignedTargetId(),
                        tel.droneId(),
                        "from "
                                + tel.transferredFromTargetId()
                                + " → "
                                + tel.assignedTargetId()
                                + " · "
                                + tel.sectorId()
                                + " (narrower lane, more coverage)"));
            }
            if (tel.searchLaneComplete() && laneCompleteEmitted.add(tel.droneId())) {
                events.add(new MissionReplay.MissionEvent(
                        "LANE_COMPLETE",
                        tel.tick(),
                        tel.assignedTargetId(),
                        tel.droneId(),
                        "primary search lane finished"));
            }
            if ("expanded".equals(tel.searchPhase())
                    && tel.supplementalPassCount() > 0
                    && expandedSearchEmitted.add(tel.droneId() + ":" + tel.supplementalPassCount())) {
                events.add(new MissionReplay.MissionEvent(
                        "EXPANDED_SEARCH",
                        tel.tick(),
                        tel.assignedTargetId(),
                        tel.droneId(),
                        "pass "
                                + tel.supplementalPassCount()
                                + " — continuing search until battery RTB"));
            }
            if (Boolean.TRUE.equals(tel.geofenceViolation()) && geofenceEmitted.add(tel.droneId())) {
                events.add(new MissionReplay.MissionEvent(
                        "GEOFENCE_VIOLATION",
                        tel.tick(),
                        null,
                        tel.droneId(),
                        "no-fly zone breach"));
            }
            if (tel.mode() == DroneMode.EMERGENCY_LAND && emergencyEmitted.add(tel.droneId())) {
                events.add(new MissionReplay.MissionEvent(
                        "EMERGENCY_LAND",
                        tel.tick(),
                        null,
                        tel.droneId(),
                        "battery depleted — emergency land"));
            }
            if (tel.batteryPct() <= rtbBatteryPct
                    && tel.mode() != DroneMode.LANDED
                    && tel.mode() != DroneMode.EMERGENCY_LAND
                    && lowBatteryEmitted.add(tel.droneId())) {
                events.add(new MissionReplay.MissionEvent(
                        "LOW_BATTERY",
                        tel.tick(),
                        null,
                        tel.droneId(),
                        "battery " + tel.batteryPct() + "% at or below RTB " + rtbBatteryPct + "%"));
            }
            if (previous == DroneMode.SEARCH && mode == DroneMode.RTB) {
                boolean targetFoundForDrone =
                        det != null && det.targetId().equals(tel.assignedTargetId());
                if (!targetFoundForDrone && !foundTargets.contains(tel.assignedTargetId())) {
                    events.add(new MissionReplay.MissionEvent(
                            "BATTERY_RTB",
                            tel.tick(),
                            tel.assignedTargetId(),
                            tel.droneId(),
                            "battery " + tel.batteryPct() + "% — RTB without target found"));
                }
            }
        }
        if (result.fleetRtbTick() != null) {
            events.add(new MissionReplay.MissionEvent(
                    "FLEET_RTB",
                    result.fleetRtbTick(),
                    null,
                    null,
                    "all targets found — fleet RTB"));
        }
        String endDetail = result.allTargetsFound()
                ? "success — all targets found, all landed=" + result.allRtbLanded()
                : "incomplete — missed "
                        + (missedTargetIds.isEmpty() ? "none" : String.join(", ", missedTargetIds))
                        + ", all landed="
                        + result.allRtbLanded();
        events.add(new MissionReplay.MissionEvent(
                "MISSION_END", result.ticksRun(), null, null, endDetail));
        events.sort(Comparator.comparingLong(MissionReplay.MissionEvent::tick)
                .thenComparing(MissionReplay.MissionEvent::type));
        return events;
    }
}
