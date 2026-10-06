package local.a2a.scenarios.dronesar.flock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import local.a2a.scenarios.dronesar.model.Target;
import local.a2a.scenarios.dronesar.world.GridWorld;
import local.a2a.scenarios.dronesar.world.PolygonUtil;

/** Supplemental search passes after the primary grid lane is exhausted. */
public final class ExpandedSearchPlanner {
    private ExpandedSearchPlanner() {}

    public static List<SectorAllocator.Waypoint> nextPass(
            Target target,
            List<List<Double>> searchPolygon,
            GridWorld world,
            String droneId,
            int passNumber,
            double laneXMin,
            double laneXMax,
            double laneYMin,
            double laneYMax,
            int laneIndex,
            double droneXM,
            double droneYM) {
        if (target == null || searchPolygon == null) {
            return List.of();
        }
        return switch (passNumber % 3) {
            case 0 -> offsetZigzag(
                    world, laneXMin, laneXMax, laneYMin, laneYMax, laneIndex, passNumber / 3 + 1);
            case 1 -> lkpSpiral(world, target, searchPolygon, laneXMin, laneXMax, laneYMin, laneYMax);
            case 2 -> unsweptLanePass(world, searchPolygon, laneXMin, laneXMax, laneYMin, laneYMax);
            default -> List.of();
        };
    }

    /** Finer zigzag with a cell offset to cover gaps left by the primary 4-cell step. */
    static List<SectorAllocator.Waypoint> offsetZigzag(
            GridWorld world,
            double xMin,
            double xMax,
            double yMin,
            double yMax,
            int laneIndex,
            int offsetPass) {
        double cellSize = world.cellSizeM();
        double step = 2.0;
        double yStart = yMin + (offsetPass % 2) * (step / 2.0);
        boolean forward = laneIndex % 2 == 0;
        List<SectorAllocator.Waypoint> points = new ArrayList<>();
        for (double y = yStart; y <= yMax + 1e-6; y += step) {
            double xStart = forward ? xMin : xMax;
            double xEnd = forward ? xMax : xMin;
            points.add(new SectorAllocator.Waypoint(xStart * cellSize, y * cellSize));
            points.add(new SectorAllocator.Waypoint(xEnd * cellSize, y * cellSize));
            forward = !forward;
        }
        return points.size() >= 2 ? points : List.of();
    }

    /** Expanding square rings around last-known position, clipped to lane bounds. */
    static List<SectorAllocator.Waypoint> lkpSpiral(
            GridWorld world,
            Target target,
            List<List<Double>> searchPolygon,
            double laneXMin,
            double laneXMax,
            double laneYMin,
            double laneYMax) {
        if (target.lastKnownCell() == null) {
            return List.of();
        }
        double cellSize = world.cellSizeM();
        double cx = target.lastKnownCell().x() + 0.5;
        double cy = target.lastKnownCell().y() + 0.5;
        List<SectorAllocator.Waypoint> points = new ArrayList<>();
        for (int ring = 1; ring <= 8; ring++) {
            appendRing(points, cx, cy, ring, laneXMin, laneXMax, laneYMin, laneYMax, searchPolygon, cellSize);
        }
        return points.size() >= 2 ? points : List.of();
    }

    private static void appendRing(
            List<SectorAllocator.Waypoint> points,
            double cx,
            double cy,
            int ring,
            double laneXMin,
            double laneXMax,
            double laneYMin,
            double laneYMax,
            List<List<Double>> searchPolygon,
            double cellSize) {
        double minX = Math.max(laneXMin, cx - ring);
        double maxX = Math.min(laneXMax, cx + ring);
        double minY = Math.max(laneYMin, cy - ring);
        double maxY = Math.min(laneYMax, cy + ring);
        addIfValid(points, minX, minY, searchPolygon, cellSize);
        for (double x = minX + 1; x <= maxX; x += 1) {
            addIfValid(points, x, minY, searchPolygon, cellSize);
        }
        addIfValid(points, maxX, minY, searchPolygon, cellSize);
        for (double y = minY + 1; y <= maxY; y += 1) {
            addIfValid(points, maxX, y, searchPolygon, cellSize);
        }
        addIfValid(points, maxX, maxY, searchPolygon, cellSize);
        for (double x = maxX - 1; x >= minX; x -= 1) {
            addIfValid(points, x, maxY, searchPolygon, cellSize);
        }
        addIfValid(points, minX, maxY, searchPolygon, cellSize);
        for (double y = maxY - 1; y >= minY; y -= 1) {
            addIfValid(points, minX, y, searchPolygon, cellSize);
        }
    }

    private static void addIfValid(
            List<SectorAllocator.Waypoint> points,
            double cellX,
            double cellY,
            List<List<Double>> searchPolygon,
            double cellSize) {
        if (PolygonUtil.contains(searchPolygon, cellX, cellY)) {
            points.add(new SectorAllocator.Waypoint(cellX * cellSize, cellY * cellSize));
        }
    }

    /** Visit cell centres in the lane that have not been searched yet. */
    static List<SectorAllocator.Waypoint> unsweptLanePass(
            GridWorld world,
            List<List<Double>> searchPolygon,
            double laneXMin,
            double laneXMax,
            double laneYMin,
            double laneYMax) {
        double cellSize = world.cellSizeM();
        List<int[]> unswept = new ArrayList<>();
        int minX = (int) Math.floor(laneXMin);
        int maxX = (int) Math.ceil(laneXMax);
        int minY = (int) Math.floor(laneYMin);
        int maxY = (int) Math.ceil(laneYMax);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (!PolygonUtil.contains(searchPolygon, x + 0.5, y + 0.5)) {
                    continue;
                }
                if (!world.isSearched(x, y)) {
                    unswept.add(new int[] {x, y});
                }
            }
        }
        if (unswept.isEmpty()) {
            return seededZigzag(world, searchPolygon, laneXMin, laneXMax, laneYMin, laneYMax);
        }
        unswept.sort(Comparator.comparingInt((int[] c) -> c[1]).thenComparingInt(c -> c[0]));
        List<SectorAllocator.Waypoint> points = new ArrayList<>();
        boolean forward = true;
        int row = unswept.get(0)[1];
        List<int[]> rowCells = new ArrayList<>();
        for (int[] cell : unswept) {
            if (cell[1] != row) {
                appendRow(points, rowCells, forward, cellSize);
                forward = !forward;
                rowCells.clear();
                row = cell[1];
            }
            rowCells.add(cell);
        }
        appendRow(points, rowCells, forward, cellSize);
        return points.size() >= 2 ? points : List.of();
    }

    private static void appendRow(
            List<SectorAllocator.Waypoint> points, List<int[]> rowCells, boolean forward, double cellSize) {
        if (rowCells.isEmpty()) {
            return;
        }
        rowCells.sort(Comparator.comparingInt(c -> c[0]));
        if (!forward) {
            rowCells = new ArrayList<>(rowCells);
            java.util.Collections.reverse(rowCells);
        }
        for (int[] cell : rowCells) {
            points.add(new SectorAllocator.Waypoint((cell[0] + 0.5) * cellSize, (cell[1] + 0.5) * cellSize));
        }
    }

    /** Deterministic pseudo-random start within lane bounds, then a short zigzag. */
    static List<SectorAllocator.Waypoint> seededZigzag(
            GridWorld world,
            List<List<Double>> searchPolygon,
            double laneXMin,
            double laneXMax,
            double laneYMin,
            double laneYMax) {
        PolygonUtil.Bounds b = new PolygonUtil.Bounds(laneXMin, laneYMin, laneXMax, laneYMax);
        Random random = new Random(31);
        double seedX = laneXMin;
        double seedY = laneYMin;
        for (int i = 0; i < 40; i++) {
            double x = b.minX() + random.nextDouble() * b.width();
            double y = b.minY() + random.nextDouble() * b.height();
            if (PolygonUtil.contains(searchPolygon, x, y)) {
                seedX = x;
                seedY = y;
                break;
            }
        }
        double cellSize = world.cellSizeM();
        double step = 2.0;
        List<SectorAllocator.Waypoint> points = new ArrayList<>();
        points.add(new SectorAllocator.Waypoint(seedX * cellSize, seedY * cellSize));
        boolean forward = true;
        for (double y = seedY; y <= laneYMax; y += step) {
            double xStart = forward ? laneXMin : laneXMax;
            double xEnd = forward ? laneXMax : laneXMin;
            points.add(new SectorAllocator.Waypoint(xStart * cellSize, y * cellSize));
            points.add(new SectorAllocator.Waypoint(xEnd * cellSize, y * cellSize));
            forward = !forward;
        }
        return points.size() >= 2 ? points : List.of();
    }
}
