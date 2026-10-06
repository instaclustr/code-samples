package local.a2a.scenarios.dronesar.world;

import java.util.List;

public final class PolygonUtil {
    private PolygonUtil() {}

    /** Ray-casting point-in-polygon; polygon vertices as [x,y] in same units as point. */
    public static boolean contains(List<List<Double>> polygon, double x, double y) {
        if (polygon == null || polygon.size() < 3) {
            return false;
        }
        boolean inside = false;
        int n = polygon.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = polygon.get(i).get(0);
            double yi = polygon.get(i).get(1);
            double xj = polygon.get(j).get(0);
            double yj = polygon.get(j).get(1);
            boolean intersect = ((yi > y) != (yj > y))
                    && (x < (xj - xi) * (y - yi) / (yj - yi + 1e-12) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    public static Bounds bounds(List<List<Double>> polygon) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (List<Double> p : polygon) {
            minX = Math.min(minX, p.get(0));
            minY = Math.min(minY, p.get(1));
            maxX = Math.max(maxX, p.get(0));
            maxY = Math.max(maxY, p.get(1));
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    public record Bounds(double minX, double minY, double maxX, double maxY) {
        public double width() {
            return maxX - minX;
        }

        public double height() {
            return maxY - minY;
        }
    }
}
