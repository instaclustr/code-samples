package local.a2a.scenarios.dronesar.flock;

import java.util.ArrayList;
import java.util.List;
import local.a2a.scenarios.dronesar.model.Detection;
import local.a2a.scenarios.dronesar.model.DroneMode;
import local.a2a.scenarios.dronesar.model.DroneSpec;
import local.a2a.scenarios.dronesar.model.DroneTelemetry;
import local.a2a.scenarios.dronesar.model.Mission;
import local.a2a.scenarios.dronesar.model.Position;
import local.a2a.scenarios.dronesar.model.RulesSpec;
import local.a2a.scenarios.dronesar.model.Target;
import local.a2a.scenarios.dronesar.world.DetourPlanner;
import local.a2a.scenarios.dronesar.world.GridWorld;

public final class SimDrone {
    private final String id;
    private final List<String> sensors;
    private String assignedTargetId;
    private String sectorId;
    private List<List<Double>> searchPolygon;
    private double laneXMin;
    private double laneXMax;
    private double laneYMin;
    private double laneYMax;
    private int laneIndex;
    private final List<SectorAllocator.Waypoint> waypoints;
    private int waypointIndex;
    private double xM;
    private double yM;
    private double altAglM;
    private double headingDeg;
    private double batteryPct = 100.0;
    private DroneMode mode = DroneMode.SEARCH;
    private String searchPhase = "primary";
    private int supplementalPassCount;
    private Detection lastDetection;
    private boolean geofenceViolations;
    private boolean geofenceViolationThisTick;
    private boolean targetFoundLatch;
    private String targetFoundIdThisTick;
    private boolean searchLaneComplete;
    private boolean expandedSearchStartedThisTick;
    private boolean transferredThisTick;
    private String transferredFromTargetId;
    private int detourInsertsForLeg;
    private int blockedLegTicks;

    public SimDrone(DroneSpec spec, SectorAllocator.GridLaneSector sector, double startXM, double startYM, double startAgl) {
        this.id = spec.id();
        this.assignedTargetId = sector.targetId();
        this.sensors = spec.sensors();
        this.sectorId = sector.sectorId();
        this.searchPolygon = sector.searchPolygon();
        this.laneXMin = sector.laneXMin();
        this.laneXMax = sector.laneXMax();
        this.laneYMin = sector.laneYMin();
        this.laneYMax = sector.laneYMax();
        this.laneIndex = sector.laneIndex();
        this.waypoints = new ArrayList<>(sector.waypoints());
        this.xM = startXM;
        this.yM = startYM;
        this.altAglM = startAgl;
        this.headingDeg = 0;
        if (spec.initialBatteryPct() != null) {
            this.batteryPct = spec.initialBatteryPct();
        }
    }

    public String id() {
        return id;
    }

    public String assignedTargetId() {
        return assignedTargetId;
    }

    public DroneMode mode() {
        return mode;
    }

    public double batteryPct() {
        return batteryPct;
    }

    public boolean geofenceViolations() {
        return geofenceViolations;
    }

    /** Demo/stress hook — marks a policy violation for this tick (Kafka safety pipeline). */
    public void injectGeofenceViolation() {
        geofenceViolations = true;
        geofenceViolationThisTick = true;
    }

    /** Demo/stress hook — force emergency land (battery depletion scenario). */
    public void injectEmergencyLand() {
        mode = DroneMode.EMERGENCY_LAND;
        altAglM = 0;
        searchPhase = "emergency";
        batteryPct = 0;
    }

    public String targetFoundIdThisTick() {
        return targetFoundIdThisTick;
    }

    public boolean expandedSearchStartedThisTick() {
        return expandedSearchStartedThisTick;
    }

    public boolean transferredThisTick() {
        return transferredThisTick;
    }

    public String transferredFromTargetId() {
        return transferredFromTargetId;
    }

    public void reassignToSector(SectorAllocator.GridLaneSector sector) {
        assignedTargetId = sector.targetId();
        sectorId = sector.sectorId();
        searchPolygon = sector.searchPolygon();
        laneXMin = sector.laneXMin();
        laneXMax = sector.laneXMax();
        laneYMin = sector.laneYMin();
        laneYMax = sector.laneYMax();
        laneIndex = sector.laneIndex();
        waypoints.clear();
        waypoints.addAll(sector.waypoints());
        waypointIndex = 0;
        searchLaneComplete = false;
        supplementalPassCount = 0;
        detourInsertsForLeg = 0;
        blockedLegTicks = 0;
        if (!"rtb".equals(searchPhase)) {
            searchPhase = "primary";
        }
    }

    public void transferToSector(SectorAllocator.GridLaneSector sector, String fromTargetId) {
        reassignToSector(sector);
        targetFoundLatch = false;
        searchPhase = "transferred";
        transferredThisTick = true;
        transferredFromTargetId = fromTargetId;
    }

    public void setRtbPath(double baseXM, double baseYM) {
        waypoints.clear();
        waypoints.add(new SectorAllocator.Waypoint(baseXM, baseYM));
        waypointIndex = 0;
        mode = DroneMode.RTB;
        searchPhase = "rtb";
        detourInsertsForLeg = 0;
        blockedLegTicks = 0;
    }

    /** Hold position — used by safety/coordinator patch (Phase 2c). */
    public void hold() {
        if (mode != DroneMode.LANDED && mode != DroneMode.EMERGENCY_LAND) {
            mode = DroneMode.HOLD;
            searchPhase = "hold";
        }
    }

    public void resumeFromHold() {
        if (mode == DroneMode.HOLD) {
            mode = DroneMode.SEARCH;
            searchPhase = "primary";
        }
    }

    public void cancel() {
        if (mode != DroneMode.LANDED && mode != DroneMode.EMERGENCY_LAND) {
            mode = DroneMode.RTB;
            searchPhase = "canceled";
        }
    }

    public void tick(GridWorld world, Mission mission, double baseXM, double baseYM) {
        targetFoundIdThisTick = null;
        expandedSearchStartedThisTick = false;
        transferredThisTick = false;
        transferredFromTargetId = null;
        geofenceViolationThisTick = false;
        if ("transferred".equals(searchPhase)) {
            searchPhase = "primary";
        }
        if (mode == DroneMode.LANDED || mode == DroneMode.EMERGENCY_LAND) {
            return;
        }
        var rules = world.rules();
        if (mode == DroneMode.HOLD) {
            double drain = rules.batteryDrainSearchPctPerTick() * 0.25;
            batteryPct = Math.max(0, batteryPct - drain);
            return;
        }
        if (batteryPct <= 0) {
            mode = DroneMode.EMERGENCY_LAND;
            altAglM = 0;
            searchPhase = "emergency";
            return;
        }
        if (mode == DroneMode.SEARCH && batteryPct <= rules.rtbBatteryPct()) {
            setRtbPath(baseXM, baseYM);
        }
        if (mode == DroneMode.SEARCH && searchLaneComplete && !targetFoundLatch) {
            maybeStartExpandedSearch(world, mission, baseXM, baseYM);
        }

        advanceAlongPath(world, rules, baseXM, baseYM);

        if (world.isNoFly(xM, yM)) {
            double[] escape = DetourPlanner.nearestClearCell(xM / world.cellSizeM(), yM / world.cellSizeM(), world);
            xM = escape[0] * world.cellSizeM();
            yM = escape[1] * world.cellSizeM();
            if (world.isNoFly(xM, yM)) {
                geofenceViolations = true;
                geofenceViolationThisTick = true;
            }
        }

        altAglM = world.minAglAt(xM, yM, altAglM);
        altAglM = Math.min(altAglM, rules.maxAglM());

        int cx = world.cellFromMeters(xM);
        int cy = world.cellFromMeters(yM);
        world.markSearched(cx, cy);

        evaluateTargetDetection(world, mission);

        double drain = mode == DroneMode.RTB ? rules.batteryDrainRtbPctPerTick() : rules.batteryDrainSearchPctPerTick();
        batteryPct = Math.max(0, batteryPct - drain);
    }

    private void advanceAlongPath(GridWorld world, RulesSpec rules, double baseXM, double baseYM) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double targetXM = activeTargetXM(baseXM, baseYM);
            double targetYM = activeTargetYM(baseXM, baseYM);
            double dx = targetXM - xM;
            double dy = targetYM - yM;
            double dist = Math.hypot(dx, dy);
            if (dist <= 0.01) {
                if (advancePastCurrentWaypoint(baseXM, baseYM)) {
                    detourInsertsForLeg = 0;
                    blockedLegTicks = 0;
                    continue;
                }
                return;
            }
            double speed = rules.cruiseSpeedMs();
            double step = Math.min(speed, dist);
            double nextX = nextStepX(xM, yM, dx, dy, dist, step);
            double nextY = nextStepY(xM, yM, dx, dy, dist, step);
            if (!world.isNoFly(nextX, nextY)) {
                xM = nextX;
                yM = nextY;
                headingDeg = Math.toDegrees(Math.atan2(dy, dx));
                blockedLegTicks = 0;
                return;
            }
            if (detourInsertsForLeg < 12 && maybeInsertDetour(world, targetXM, targetYM)) {
                continue;
            }
            blockedLegTicks++;
            if (blockedLegTicks >= 3 || detourInsertsForLeg >= 12) {
                skipUnreachableWaypoint();
                detourInsertsForLeg = 0;
                blockedLegTicks = 0;
                continue;
            }
            return;
        }
    }

    private boolean advancePastCurrentWaypoint(double baseXM, double baseYM) {
        if (!waypoints.isEmpty() && waypointIndex < waypoints.size() - 1) {
            waypointIndex++;
            return true;
        }
        if (mode == DroneMode.SEARCH && !waypoints.isEmpty() && waypointIndex >= waypoints.size() - 1) {
            searchLaneComplete = true;
            return false;
        }
        if (mode == DroneMode.RTB) {
            double dist = Math.hypot(xM - baseXM, yM - baseYM);
            if (dist <= 1.0) {
                mode = DroneMode.LANDED;
                altAglM = 0;
                searchPhase = "landed";
            }
        }
        return false;
    }

    private boolean maybeInsertDetour(GridWorld world, double targetXM, double targetYM) {
        if (detourInsertsForLeg >= 12) {
            return false;
        }
        double cell = world.cellSizeM();
        double xCell = xM / cell;
        double yCell = yM / cell;
        double tXCell = targetXM / cell;
        double tYCell = targetYM / cell;
        double[] clearTarget = DetourPlanner.nearestClearCell(tXCell, tYCell, world);
        tXCell = clearTarget[0];
        tYCell = clearTarget[1];
        List<SectorAllocator.Waypoint> detour =
                DetourPlanner.planDetour(xCell, yCell, tXCell, tYCell, world);
        if (detour.isEmpty()) {
            return false;
        }
        for (int i = detour.size() - 1; i >= 0; i--) {
            waypoints.add(waypointIndex, detour.get(i));
        }
        detourInsertsForLeg += detour.size();
        return true;
    }

    private void skipUnreachableWaypoint() {
        if (!waypoints.isEmpty() && waypointIndex < waypoints.size() - 1) {
            waypointIndex++;
            detourInsertsForLeg = 0;
            blockedLegTicks = 0;
        } else if (mode == DroneMode.SEARCH) {
            searchLaneComplete = true;
        }
    }

    private double activeTargetXM(double baseXM, double baseYM) {
        if (!waypoints.isEmpty() && waypointIndex < waypoints.size()) {
            return waypoints.get(waypointIndex).xM();
        }
        if (mode == DroneMode.RTB) {
            return baseXM;
        }
        return xM;
    }

    private double activeTargetYM(double baseXM, double baseYM) {
        if (!waypoints.isEmpty() && waypointIndex < waypoints.size()) {
            return waypoints.get(waypointIndex).yM();
        }
        if (mode == DroneMode.RTB) {
            return baseYM;
        }
        return yM;
    }

    private static double nextStepX(double xM, double yM, double dx, double dy, double dist, double step) {
        return xM + dx / dist * step;
    }

    private static double nextStepY(double xM, double yM, double dx, double dy, double dist, double step) {
        return yM + dy / dist * step;
    }

    private void maybeStartExpandedSearch(GridWorld world, Mission mission, double baseXM, double baseYM) {
        if (!hasBatteryForExpandedSearch(world.rules(), baseXM, baseYM)) {
            return;
        }
        Target target = mission.targets().stream()
                .filter(t -> assignedTargetId.equals(t.id()))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return;
        }
        List<SectorAllocator.Waypoint> next = ExpandedSearchPlanner.nextPass(
                target,
                searchPolygon,
                world,
                id,
                supplementalPassCount,
                laneXMin,
                laneXMax,
                laneYMin,
                laneYMax,
                laneIndex,
                xM,
                yM);
        if (next.isEmpty()) {
            return;
        }
        waypoints.clear();
        waypoints.addAll(next);
        waypointIndex = 0;
        searchLaneComplete = false;
        supplementalPassCount++;
        searchPhase = "expanded";
        expandedSearchStartedThisTick = true;
    }

    private boolean hasBatteryForExpandedSearch(RulesSpec rules, double baseXM, double baseYM) {
        double dist = Math.hypot(xM - baseXM, yM - baseYM);
        double ticks = dist / Math.max(rules.cruiseSpeedMs(), 0.1);
        double rtbDrain = ticks * rules.batteryDrainRtbPctPerTick();
        return batteryPct > rules.rtbBatteryPct() + rtbDrain + 5.0;
    }

    private void evaluateTargetDetection(GridWorld world, Mission mission) {
        if (mission.targets() == null || mission.targets().isEmpty() || mode == DroneMode.EMERGENCY_LAND) {
            return;
        }
        Target target = mission.targets().stream()
                .filter(t -> assignedTargetId.equals(t.id()))
                .findFirst()
                .orElse(null);
        if (target == null || target.lastKnownCell() == null) {
            return;
        }
        double tX = target.lastKnownCell().x() * world.cellSizeM();
        double tY = target.lastKnownCell().y() * world.cellSizeM();
        double dist = Math.hypot(xM - tX, yM - tY);
        double cell = world.cellSizeM();
        boolean inRange = dist < cell * 2;
        if (inRange) {
            lastDetection = new Detection(target.id(), target.type(), 0.91, List.of(0.2, 0.3, 0.1, 0.2));
            if (!targetFoundLatch) {
                targetFoundLatch = true;
                targetFoundIdThisTick = target.id();
            }
        } else if (mission.simOrDefault().lowConfidenceBandEnabled() && dist < cell * 4.0) {
            lastDetection = new Detection(target.id(), target.type(), 0.58, List.of(0.2, 0.3, 0.1, 0.2));
        } else if (!targetFoundLatch) {
            lastDetection = null;
        }
    }

    public DroneTelemetry telemetry(long tick, String missionId) {
        return new DroneTelemetry(
                id,
                tick,
                new Position(xM, yM, altAglM),
                headingDeg,
                Math.round(batteryPct * 10) / 10.0,
                mode,
                sectorId,
                assignedTargetId,
                searchLaneComplete,
                searchPhase,
                supplementalPassCount,
                transferredThisTick ? transferredFromTargetId : null,
                lastDetection,
                lastDetection != null
                        ? "file://sim-frames/" + missionId + "/" + id + "/t" + tick + ".jpg"
                        : null,
                geofenceViolationThisTick ? true : null);
    }
}
