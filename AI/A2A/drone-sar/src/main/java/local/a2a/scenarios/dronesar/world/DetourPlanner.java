package local.a2a.scenarios.dronesar.world;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import local.a2a.scenarios.dronesar.flock.SectorAllocator;

/** Simple bbox-corner detours around no-fly polygons (cell coordinates). */
public final class DetourPlanner {
    private static final double SAMPLE_STEP_CELLS = 0.5;
    private static final double BBOX_PAD_CELLS = 2.0;

    private DetourPlanner() {}

    public static boolean segmentBlocked(double x0Cell, double y0Cell, double x1Cell, double y1Cell, GridWorld world) {
        if (world.noFlyPolygons().isEmpty()) {
            return false;
        }
        double len = Math.hypot(x1Cell - x0Cell, y1Cell - y0Cell);
        int steps = Math.max(1, (int) Math.ceil(len / SAMPLE_STEP_CELLS));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double xCell = x0Cell + (x1Cell - x0Cell) * t;
            double yCell = y0Cell + (y1Cell - y0Cell) * t;
            if (isBlockedCell(xCell, yCell, world)) {
                return true;
            }
        }
        return false;
    }

    /** Snap an in-zone point to the nearest clear cell (spiral search). */
    public static double[] nearestClearCell(double xCell, double yCell, GridWorld world) {
        if (!isBlockedCell(xCell, yCell, world)) {
            return new double[] {xCell, yCell};
        }
        for (double r = 0.5; r <= 24; r += 0.5) {
            for (int i = 0; i < 16; i++) {
                double angle = i * Math.PI / 8.0;
                double px = xCell + r * Math.cos(angle);
                double py = yCell + r * Math.sin(angle);
                if (!isBlockedCell(px, py, world)) {
                    return new double[] {px, py};
                }
            }
        }
        return new double[] {xCell, yCell};
    }

    /** Returns intermediate waypoints (meters) to insert before the direct leg, or empty if direct path is clear. */
    public static List<SectorAllocator.Waypoint> planDetour(
            double x0Cell, double y0Cell, double x1Cell, double y1Cell, GridWorld world) {
        double origX1 = x1Cell;
        double origY1 = y1Cell;
        double[] clearTarget = nearestClearCell(x1Cell, y1Cell, world);
        x1Cell = clearTarget[0];
        y1Cell = clearTarget[1];
        if (!segmentBlocked(x0Cell, y0Cell, x1Cell, y1Cell, world)) {
            if (Math.hypot(clearTarget[0] - origX1, clearTarget[1] - origY1) > 1e-6) {
                return List.of(toWaypoint(clearTarget[0], clearTarget[1], world.cellSizeM()));
            }
            return List.of();
        }
        PolygonUtil.Bounds union = unionBounds(findBlockingPolygons(x0Cell, y0Cell, x1Cell, y1Cell, world));
        if (union == null) {
            return List.of();
        }
        List<double[]> candidates = bboxCandidates(union);
        double bestCost = Double.MAX_VALUE;
        double[] bestVia = null;
        for (double[] via : candidates) {
            if (segmentBlocked(x0Cell, y0Cell, via[0], via[1], world)
                    || segmentBlocked(via[0], via[1], x1Cell, y1Cell, world)) {
                continue;
            }
            double cost = Math.hypot(via[0] - x0Cell, via[1] - y0Cell)
                    + Math.hypot(x1Cell - via[0], y1Cell - via[1]);
            if (cost < bestCost) {
                bestCost = cost;
                bestVia = via;
            }
        }
        if (bestVia != null) {
            return List.of(toWaypoint(bestVia[0], bestVia[1], world.cellSizeM()));
        }

        List<SectorAllocator.Waypoint> bestTwoHop = null;
        for (double[] via1 : candidates) {
            for (double[] via2 : candidates) {
                if (samePoint(via1, via2)) {
                    continue;
                }
                if (segmentBlocked(x0Cell, y0Cell, via1[0], via1[1], world)
                        || segmentBlocked(via1[0], via1[1], via2[0], via2[1], world)
                        || segmentBlocked(via2[0], via2[1], x1Cell, y1Cell, world)) {
                    continue;
                }
                double cost = Math.hypot(via1[0] - x0Cell, via1[1] - y0Cell)
                        + Math.hypot(via2[0] - via1[0], via2[1] - via1[1])
                        + Math.hypot(x1Cell - via2[0], y1Cell - via2[1]);
                if (cost < bestCost) {
                    bestCost = cost;
                    bestTwoHop = List.of(
                            toWaypoint(via1[0], via1[1], world.cellSizeM()),
                            toWaypoint(via2[0], via2[1], world.cellSizeM()));
                }
            }
        }
        return bestTwoHop != null ? bestTwoHop : List.of();
    }

    private static List<double[]> bboxCandidates(PolygonUtil.Bounds b) {
        double minX = b.minX() - BBOX_PAD_CELLS;
        double maxX = b.maxX() + BBOX_PAD_CELLS;
        double minY = b.minY() - BBOX_PAD_CELLS;
        double maxY = b.maxY() + BBOX_PAD_CELLS;
        double midX = (minX + maxX) / 2.0;
        double midY = (minY + maxY) / 2.0;
        double outer = BBOX_PAD_CELLS * 2.0;
        List<double[]> points = new ArrayList<>();
        double[][] core = {
            {minX, minY},
            {maxX, minY},
            {maxX, maxY},
            {minX, maxY},
            {midX, minY},
            {maxX, midY},
            {midX, maxY},
            {minX, midY},
            {minX - outer, minY},
            {maxX + outer, minY},
            {maxX + outer, maxY},
            {minX - outer, maxY},
            {minX, minY - outer},
            {maxX, minY - outer},
            {maxX, maxY + outer},
            {minX, maxY + outer},
        };
        for (double[] p : core) {
            points.add(p);
        }
        return points;
    }

    private static PolygonUtil.Bounds unionBounds(List<List<List<Double>>> polys) {
        if (polys.isEmpty()) {
            return null;
        }
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (List<List<Double>> poly : polys) {
            PolygonUtil.Bounds b = PolygonUtil.bounds(poly);
            minX = Math.min(minX, b.minX());
            minY = Math.min(minY, b.minY());
            maxX = Math.max(maxX, b.maxX());
            maxY = Math.max(maxY, b.maxY());
        }
        return new PolygonUtil.Bounds(minX, minY, maxX, maxY);
    }

    private static List<List<List<Double>>> findBlockingPolygons(
            double x0Cell, double y0Cell, double x1Cell, double y1Cell, GridWorld world) {
        Set<List<List<Double>>> blockers = new LinkedHashSet<>();
        double len = Math.hypot(x1Cell - x0Cell, y1Cell - y0Cell);
        int steps = Math.max(1, (int) Math.ceil(len / SAMPLE_STEP_CELLS));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double xCell = x0Cell + (x1Cell - x0Cell) * t;
            double yCell = y0Cell + (y1Cell - y0Cell) * t;
            for (List<List<Double>> poly : world.noFlyPolygons()) {
                if (PolygonUtil.contains(poly, xCell, yCell)) {
                    blockers.add(poly);
                }
            }
        }
        return new ArrayList<>(blockers);
    }

    private static boolean isBlockedCell(double xCell, double yCell, GridWorld world) {
        return world.isNoFly(xCell * world.cellSizeM(), yCell * world.cellSizeM());
    }

    private static SectorAllocator.Waypoint toWaypoint(double xCell, double yCell, double cellSizeM) {
        return new SectorAllocator.Waypoint(xCell * cellSizeM, yCell * cellSizeM);
    }

    private static boolean samePoint(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]) < 1e-6;
    }
}
