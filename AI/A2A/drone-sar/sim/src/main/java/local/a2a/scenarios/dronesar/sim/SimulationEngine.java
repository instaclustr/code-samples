package local.a2a.scenarios.dronesar.sim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import local.a2a.scenarios.dronesar.flock.SectorAllocator;
import local.a2a.scenarios.dronesar.flock.SimDrone;
import local.a2a.scenarios.dronesar.model.DroneMode;
import local.a2a.scenarios.dronesar.model.DroneTelemetry;
import local.a2a.scenarios.dronesar.model.Mission;
import local.a2a.scenarios.dronesar.model.Target;
import local.a2a.scenarios.dronesar.world.GridWorld;

/** 1 Hz tick loop — flock coordinator library in-process (Phase 1, no A2A). */
public final class SimulationEngine {
    private final Mission mission;
    private final GridWorld world;
    private final List<SimDrone> drones;
    private final Set<String> foundTargetIds = new LinkedHashSet<>();
    private double baseXM;
    private double baseYM;
    private long tick;
    private boolean geofenceViolation;
    private boolean allTargetsFound;
    private Long fleetRtbTick;

    public SimulationEngine(Mission mission) {
        this.mission = mission;
        this.world = new GridWorld(mission);
        this.baseXM = world.metersX(mission.base().cellX());
        this.baseYM = world.metersY(mission.base().cellY());
        List<SectorAllocator.GridLaneSector> sectors = SectorAllocator.assign(mission, world);
        this.drones = new ArrayList<>();
        double startAgl = mission.rulesOrDefault().minAglM() + 5;
        for (int i = 0; i < mission.drones().size(); i++) {
            drones.add(new SimDrone(
                    mission.drones().get(i),
                    sectors.get(i),
                    baseXM + i * 2.0,
                    baseYM,
                    startAgl));
        }
    }

    public GridWorld world() {
        return world;
    }

    public SimulationResult run(int maxTicks, Consumer<DroneTelemetry> onTelemetry) {
        int targetCount = mission.targets().size();
        for (tick = 1; tick <= maxTicks; tick++) {
            if (mission.base().mobile() && tick % 120 == 0) {
                baseXM += world.cellSizeM();
            }
            List<String> newlyFound = new ArrayList<>();
            for (SimDrone drone : drones) {
                drone.tick(world, mission, baseXM, baseYM);
                if (mission.demoViolationsEnabled()) {
                    injectDemoViolations(drone, tick);
                }
                if (drone.geofenceViolations()) {
                    geofenceViolation = true;
                }
                String foundId = drone.targetFoundIdThisTick();
                if (foundId != null && foundTargetIds.add(foundId)) {
                    newlyFound.add(foundId);
                }
            }
            for (String foundId : newlyFound) {
                if (foundTargetIds.size() < targetCount) {
                    transferSearchersFromFoundTarget(foundId);
                }
            }
            if (!allTargetsFound && foundTargetIds.size() >= targetCount) {
                allTargetsFound = true;
                fleetRtbTick = tick;
                orderFleetRtb();
            }
            for (SimDrone drone : drones) {
                DroneTelemetry tel = drone.telemetry(tick, mission.missionId());
                if (onTelemetry != null) {
                    onTelemetry.accept(tel);
                }
            }
            if (allLanded()) {
                break;
            }
        }
        List<DroneTelemetry> last = new ArrayList<>();
        for (SimDrone d : drones) {
            last.add(d.telemetry(tick, mission.missionId()));
        }
        return SimulationResult.fromRun(
                tick,
                mission,
                last,
                world.searchedCellCount(),
                geofenceViolation,
                allTargetsFound,
                fleetRtbTick,
                List.copyOf(foundTargetIds));
    }

    /**
     * When a target is found but others remain, reassign that target's search drones to help
     * remaining target(s). All drones on a remaining target get new narrower lanes — more
     * parallel coverage speeds up the search.
     */
    private void transferSearchersFromFoundTarget(String foundTargetId) {
        List<String> remaining = mission.targets().stream()
                .map(Target::id)
                .filter(id -> !foundTargetIds.contains(id))
                .toList();
        if (remaining.isEmpty()) {
            return;
        }

        List<String> transferIds = drones.stream()
                .filter(d -> foundTargetId.equals(d.assignedTargetId()) && d.mode() == DroneMode.SEARCH)
                .map(SimDrone::id)
                .sorted()
                .toList();
        if (transferIds.isEmpty()) {
            return;
        }

        List<String> pool = new ArrayList<>(transferIds);
        Map<String, List<String>> additions = new LinkedHashMap<>();
        for (String remainingId : remaining) {
            additions.put(remainingId, new ArrayList<>());
        }
        for (int i = 0; i < pool.size(); i++) {
            String remainingId = remaining.get(i % remaining.size());
            additions.get(remainingId).add(pool.get(i));
        }
        for (String remainingId : remaining) {
            List<String> existing = drones.stream()
                    .filter(d -> remainingId.equals(d.assignedTargetId()) && d.mode() == DroneMode.SEARCH)
                    .map(SimDrone::id)
                    .sorted()
                    .toList();
            List<String> reassigned = new ArrayList<>(existing);
            reassigned.addAll(additions.get(remainingId));
            reassigned = reassigned.stream().sorted().distinct().toList();
            if (!reassigned.isEmpty()) {
                reassignTargetSearch(remainingId, reassigned, foundTargetId);
            }
        }
    }

    private void reassignTargetSearch(String targetId, List<String> droneIds, String fromTargetId) {
        List<SectorAllocator.GridLaneSector> sectors =
                SectorAllocator.assignForTarget(mission, world, targetId, droneIds);
        Map<String, SectorAllocator.GridLaneSector> byDrone = sectors.stream()
                .collect(Collectors.toMap(SectorAllocator.GridLaneSector::droneId, s -> s));
        for (SimDrone drone : drones) {
            SectorAllocator.GridLaneSector sector = byDrone.get(drone.id());
            if (sector == null) {
                continue;
            }
            if (fromTargetId.equals(drone.assignedTargetId())) {
                drone.transferToSector(sector, fromTargetId);
            } else {
                drone.reassignToSector(sector);
            }
        }
    }

    private void orderFleetRtb() {
        for (SimDrone drone : drones) {
            if (drone.mode() == DroneMode.SEARCH || drone.mode() == DroneMode.RTB) {
                drone.setRtbPath(baseXM, baseYM);
            }
        }
    }

    private boolean allLanded() {
        return drones.stream().allMatch(d -> d.mode() == DroneMode.LANDED || d.mode() == DroneMode.EMERGENCY_LAND);
    }

    /** Spread deterministic geofence stress events across drones for demo missions. */
    private void injectDemoViolations(SimDrone drone, long tick) {
        int slot = (int) ((tick + drone.id().hashCode()) % 37);
        if (slot == 0 || slot == 11) {
            drone.injectGeofenceViolation();
        }
        if ("d-03".equals(drone.id()) && tick == 12) {
            drone.injectEmergencyLand();
        }
    }
}
