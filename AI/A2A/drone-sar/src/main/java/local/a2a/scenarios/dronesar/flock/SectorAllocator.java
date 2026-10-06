package local.a2a.scenarios.dronesar.flock;

import java.util.ArrayList;
import java.util.List;
import local.a2a.scenarios.dronesar.model.DroneSpec;
import local.a2a.scenarios.dronesar.model.Mission;
import local.a2a.scenarios.dronesar.model.Target;
import local.a2a.scenarios.dronesar.world.GridWorld;
import local.a2a.scenarios.dronesar.world.PolygonUtil;

/** Greedy grid-lane sector assignment per target (design §15.3, §15.5). */
public final class SectorAllocator {
    private SectorAllocator() {}

    public static List<GridLaneSector> assign(Mission mission, GridWorld world) {
        List<Target> targets = mission.targets();
        List<DroneSpec> drones = mission.drones();
        int[] dronesPerTarget = splitCounts(drones.size(), targets.size());
        List<GridLaneSector> sectors = new ArrayList<>();
        int droneIndex = 0;
        for (int t = 0; t < targets.size(); t++) {
            Target target = targets.get(t);
            int assigned = dronesPerTarget[t];
            if (assigned == 0) {
                continue;
            }
            List<String> droneIds = new ArrayList<>();
            for (int i = 0; i < assigned; i++) {
                droneIds.add(drones.get(droneIndex++).id());
            }
            sectors.addAll(assignForTarget(mission, world, target.id(), droneIds));
        }
        return sectors;
    }

    /** Assign N drones to parallel lanes within one target's search polygon. */
    public static List<GridLaneSector> assignForTarget(
            Mission mission, GridWorld world, String targetId, List<String> droneIds) {
        if (droneIds.isEmpty()) {
            return List.of();
        }
        Target target = mission.targets().stream()
                .filter(t -> targetId.equals(t.id()))
                .findFirst()
                .orElseThrow();
        List<List<Double>> poly = target.searchArea().polygon();
        PolygonUtil.Bounds b = PolygonUtil.bounds(poly);
        List<String> ordered = droneIds.stream().sorted().toList();
        int laneCount = ordered.size();
        double laneWidth = b.width() / laneCount;
        List<GridLaneSector> sectors = new ArrayList<>();
        for (int lane = 0; lane < laneCount; lane++) {
            double xMin = b.minX() + lane * laneWidth;
            double xMax = b.minX() + (lane + 1) * laneWidth;
            List<Waypoint> waypoints = buildZigzagWaypoints(world, xMin, xMax, b.minY(), b.maxY(), lane);
            sectors.add(new GridLaneSector(
                    target.id() + "-lane-" + (lane + 1),
                    ordered.get(lane),
                    target.id(),
                    poly,
                    waypoints,
                    xMin,
                    xMax,
                    b.minY(),
                    b.maxY(),
                    lane));
        }
        return sectors;
    }

    static int[] splitCounts(int total, int groups) {
        int[] counts = new int[groups];
        int base = total / groups;
        int remainder = total % groups;
        for (int i = 0; i < groups; i++) {
            counts[i] = base + (i < remainder ? 1 : 0);
        }
        return counts;
    }

    private static List<Waypoint> buildZigzagWaypoints(
            GridWorld world, double xMin, double xMax, double yMin, double yMax, int laneIndex) {
        List<Waypoint> points = new ArrayList<>();
        double cellSize = world.cellSizeM();
        double step = 4.0;
        boolean forward = laneIndex % 2 == 0;
        for (double y = yMin; y <= yMax; y += step) {
            double xStart = forward ? xMin : xMax;
            double xEnd = forward ? xMax : xMin;
            points.add(new Waypoint(xStart * cellSize, y * cellSize));
            points.add(new Waypoint(xEnd * cellSize, y * cellSize));
            forward = !forward;
        }
        if (points.isEmpty()) {
            points.add(new Waypoint(xMin * cellSize, yMin * cellSize));
        }
        return points;
    }

    public record GridLaneSector(
            String sectorId,
            String droneId,
            String targetId,
            List<List<Double>> searchPolygon,
            List<Waypoint> waypoints,
            double laneXMin,
            double laneXMax,
            double laneYMin,
            double laneYMax,
            int laneIndex) {}

    public record Waypoint(double xM, double yM) {}
}
