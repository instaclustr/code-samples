package local.a2a.scenarios.dronesar.viz;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import local.a2a.scenarios.dronesar.model.DroneTelemetry;
import local.a2a.scenarios.dronesar.model.Mission;
import local.a2a.scenarios.dronesar.model.Target;
import local.a2a.scenarios.dronesar.world.GridWorld;
import local.a2a.scenarios.dronesar.world.PolygonUtil;

/** Static map snapshot: search heatmap + drone paths (SVG and PNG). */
public final class MapSnapshotRenderer {
    private static final int CANVAS = 900;
    private static final int MARGIN = 40;
    private static final String[] DRONE_COLORS = {"#2563eb", "#dc2626", "#16a34a", "#9333ea", "#ea580c"};
    private static final String[] TARGET_STROKES = {"#22c55e", "#06b6d4", "#a855f7", "#f97316"};
    private static final String[] TARGET_MARKERS = {"#fb923c", "#f472b6", "#facc15", "#38bdf8"};

    private MapSnapshotRenderer() {}

    public static void write(
            Path outDir, Mission mission, GridWorld world, List<DroneTelemetry> telemetry, MissionReplay replay)
            throws IOException {
        Files.createDirectories(outDir);
        MapLayout layout = from(mission, world);
        Map<String, List<double[]>> paths = pathsByDrone(telemetry, world.cellSizeM());

        Path svgPath = outDir.resolve("snapshot.svg");
        Path pngPath = outDir.resolve("snapshot.png");
        Files.writeString(svgPath, renderSvg(mission, world, layout, paths, replay));
        ImageIO.write(renderPng(mission, world, layout, paths, replay), "png", pngPath.toFile());
    }

    /** @deprecated use {@link #write(Path, Mission, GridWorld, List, MissionReplay)} */
    public static void write(Path outDir, Mission mission, GridWorld world, List<DroneTelemetry> telemetry)
            throws IOException {
        write(outDir, mission, world, telemetry, null);
    }

    static MapLayout from(Mission mission, GridWorld world) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (Target target : mission.targets()) {
            PolygonUtil.Bounds b = PolygonUtil.bounds(target.searchArea().polygon());
            minX = Math.min(minX, b.minX());
            minY = Math.min(minY, b.minY());
            maxX = Math.max(maxX, b.maxX());
            maxY = Math.max(maxY, b.maxY());
        }
        double pad = 4;
        return new MapLayout(minX - pad, minY - pad, maxX + pad, maxY + pad, world.cellSizeM());
    }

    static Map<String, List<double[]>> pathsByDrone(List<DroneTelemetry> telemetry, double cellSizeM) {
        Map<String, List<double[]>> paths = new LinkedHashMap<>();
        for (DroneTelemetry tel : telemetry) {
            paths.computeIfAbsent(tel.droneId(), k -> new ArrayList<>())
                    .add(new double[] {tel.position().x() / cellSizeM, tel.position().y() / cellSizeM});
        }
        return paths;
    }

    static String renderSvg(
            Mission mission, GridWorld world, MapLayout layout, Map<String, List<double[]>> paths, MissionReplay replay) {
        double scale = layout.scale(CANVAS, MARGIN);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
                .append(CANVAS)
                .append("\" height=\"")
                .append(CANVAS)
                .append("\" viewBox=\"0 0 ")
                .append(CANVAS)
                .append(" ")
                .append(CANVAS)
                .append("\">\n");
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#0f172a\"/>\n");
        sb.append(title(mission.missionId()));

        appendAllSearchAreas(sb, mission, layout, scale);
        appendNoFlyZones(sb, world, layout, scale);
        appendHeatmap(sb, world, layout, scale);

        int colorIdx = 0;
        for (Map.Entry<String, List<double[]>> entry : paths.entrySet()) {
            String color = DRONE_COLORS[colorIdx % DRONE_COLORS.length];
            appendPath(sb, entry.getValue(), layout, scale, color, 2.5, 0.85);
            List<double[]> pts = entry.getValue();
            double[] last = pts.get(pts.size() - 1);
            appendCircle(sb, layout.toPx(last[0], last[1], scale), 5, color);
            colorIdx++;
        }

        appendBase(sb, mission, layout, scale);
        appendAllTargets(sb, mission, layout, scale, replay);
        sb.append(legend(paths.keySet()));
        appendMissionStatus(sb, replay);
        sb.append(geofenceLegend(replay));
        sb.append("</svg>\n");
        return sb.toString();
    }

    static BufferedImage renderPng(
            Mission mission, GridWorld world, MapLayout layout, Map<String, List<double[]>> paths, MissionReplay replay) {
        BufferedImage img = new BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(15, 23, 42));
        g.fillRect(0, 0, CANVAS, CANVAS);

        double scale = layout.scale(CANVAS, MARGIN);
        drawAllSearchAreas(g, mission, layout, scale);
        drawNoFly(g, world, layout, scale);
        drawHeatmap(g, world, layout, scale);

        int colorIdx = 0;
        for (Map.Entry<String, List<double[]>> entry : paths.entrySet()) {
            Color color = Color.decode(DRONE_COLORS[colorIdx % DRONE_COLORS.length]);
            drawPath(g, entry.getValue(), layout, scale, color, 2.5f);
            List<double[]> pts = entry.getValue();
            double[] last = pts.get(pts.size() - 1);
            int[] px = layout.toPx(last[0], last[1], scale);
            g.setColor(color);
            g.fillOval(px[0] - 5, px[1] - 5, 10, 10);
            colorIdx++;
        }

        drawBase(g, mission, layout, scale);
        drawAllTargets(g, mission, layout, scale, replay);
        drawMissionStatus(g, replay);
        drawGeofenceLegend(g, replay);
        g.dispose();
        return img;
    }

    private static String title(String missionId) {
        return "<text x=\"20\" y=\"28\" fill=\"#e2e8f0\" font-family=\"system-ui,sans-serif\" font-size=\"16\">"
                + escape(missionId)
                + " — search heatmap + paths</text>\n";
    }

    private static String legend(Iterable<String> droneIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("<g font-family=\"system-ui,sans-serif\" font-size=\"12\" fill=\"#cbd5e1\">\n");
        int i = 0;
        int y = CANVAS - 16;
        for (String id : droneIds) {
            String color = DRONE_COLORS[i % DRONE_COLORS.length];
            sb.append("<rect x=\"20\" y=\"")
                    .append(y - 10)
                    .append("\" width=\"12\" height=\"3\" fill=\"")
                    .append(color)
                    .append("\"/>\n");
            sb.append("<text x=\"38\" y=\"")
                    .append(y)
                    .append("\">")
                    .append(escape(id))
                    .append("</text>\n");
            y -= 18;
            i++;
        }
        sb.append("</g>\n");
        return sb.toString();
    }

    private static void appendHeatmap(StringBuilder sb, GridWorld world, MapLayout layout, double scale) {
        for (int[] cell : world.searchedCellCoords()) {
            int[] tl = layout.toPx(cell[0], cell[1] + 1, scale);
            int[] br = layout.toPx(cell[0] + 1, cell[1], scale);
            sb.append("<rect x=\"")
                    .append(tl[0])
                    .append("\" y=\"")
                    .append(tl[1])
                    .append("\" width=\"")
                    .append(Math.max(1, br[0] - tl[0]))
                    .append("\" height=\"")
                    .append(Math.max(1, br[1] - tl[1]))
                    .append("\" fill=\"#3b82f6\" fill-opacity=\"0.35\"/>\n");
        }
    }

    private static void drawHeatmap(Graphics2D g, GridWorld world, MapLayout layout, double scale) {
        g.setColor(new Color(59, 130, 246, 90));
        for (int[] cell : world.searchedCellCoords()) {
            int[] tl = layout.toPx(cell[0], cell[1] + 1, scale);
            int[] br = layout.toPx(cell[0] + 1, cell[1], scale);
            g.fillRect(tl[0], tl[1], Math.max(1, br[0] - tl[0]), Math.max(1, br[1] - tl[1]));
        }
    }

    private static void appendNoFlyZones(StringBuilder sb, GridWorld world, MapLayout layout, double scale) {
        for (List<List<Double>> poly : world.noFlyPolygons()) {
            appendPolygon(sb, poly, layout, scale, "#ef444433", "#ef4444", 1.5, null);
        }
    }

    private static void drawNoFly(Graphics2D g, GridWorld world, MapLayout layout, double scale) {
        for (List<List<Double>> poly : world.noFlyPolygons()) {
            drawPolygon(g, poly, layout, scale, new Color(239, 68, 68, 50), new Color(239, 68, 68), 1.5f);
        }
    }

    private static void appendPolygon(
            StringBuilder sb,
            List<List<Double>> poly,
            MapLayout layout,
            double scale,
            String fill,
            String stroke,
            double strokeWidth,
            String dash) {
        sb.append("<polygon points=\"");
        for (List<Double> p : poly) {
            int[] px = layout.toPx(p.get(0), p.get(1), scale);
            sb.append(px[0]).append(",").append(px[1]).append(" ");
        }
        sb.append("\" fill=\"").append(fill == null ? "none" : fill).append("\" stroke=\"")
                .append(stroke)
                .append("\" stroke-width=\"")
                .append(strokeWidth)
                .append("\"");
        if (dash != null) {
            sb.append(" stroke-dasharray=\"").append(dash).append("\"");
        }
        sb.append("/>\n");
    }

    private static void drawPolygon(
            Graphics2D g,
            List<List<Double>> poly,
            MapLayout layout,
            double scale,
            Color fill,
            Color stroke,
            float strokeWidth) {
        int n = poly.size();
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            int[] px = layout.toPx(poly.get(i).get(0), poly.get(i).get(1), scale);
            xs[i] = px[0];
            ys[i] = px[1];
        }
        if (fill != null) {
            g.setColor(fill);
            g.fillPolygon(xs, ys, n);
        }
        g.setColor(stroke);
        g.setStroke(new BasicStroke(strokeWidth));
        g.drawPolygon(xs, ys, n);
    }

    private static void appendPath(
            StringBuilder sb, List<double[]> path, MapLayout layout, double scale, String color, double width, double opacity) {
        if (path.size() < 2) {
            return;
        }
        sb.append("<polyline fill=\"none\" stroke=\"")
                .append(color)
                .append("\" stroke-width=\"")
                .append(width)
                .append("\" stroke-opacity=\"")
                .append(opacity)
                .append("\" points=\"");
        for (double[] p : path) {
            int[] px = layout.toPx(p[0], p[1], scale);
            sb.append(px[0]).append(",").append(px[1]).append(" ");
        }
        sb.append("\"/>\n");
    }

    private static void drawPath(Graphics2D g, List<double[]> path, MapLayout layout, double scale, Color color, float width) {
        if (path.size() < 2) {
            return;
        }
        g.setColor(color);
        g.setStroke(new BasicStroke(width));
        for (int i = 1; i < path.size(); i++) {
            int[] a = layout.toPx(path.get(i - 1)[0], path.get(i - 1)[1], scale);
            int[] b = layout.toPx(path.get(i)[0], path.get(i)[1], scale);
            g.drawLine(a[0], a[1], b[0], b[1]);
        }
    }

    private static void appendCircle(StringBuilder sb, int[] center, int r, String color) {
        sb.append("<circle cx=\"")
                .append(center[0])
                .append("\" cy=\"")
                .append(center[1])
                .append("\" r=\"")
                .append(r)
                .append("\" fill=\"")
                .append(color)
                .append("\"/>\n");
    }

    private static void appendBase(StringBuilder sb, Mission mission, MapLayout layout, double scale) {
        int[] px = layout.toPx(mission.base().cellX() + 0.5, mission.base().cellY() + 0.5, scale);
        sb.append("<rect x=\"")
                .append(px[0] - 6)
                .append("\" y=\"")
                .append(px[1] - 6)
                .append("\" width=\"12\" height=\"12\" fill=\"#eab308\" stroke=\"#fef08a\" stroke-width=\"1\"/>\n");
        sb.append("<text x=\"")
                .append(px[0] + 10)
                .append("\" y=\"")
                .append(px[1] + 4)
                .append("\" fill=\"#fef08a\" font-size=\"11\">base</text>\n");
    }

    private static void drawBase(Graphics2D g, Mission mission, MapLayout layout, double scale) {
        int[] px = layout.toPx(mission.base().cellX() + 0.5, mission.base().cellY() + 0.5, scale);
        g.setColor(new Color(234, 179, 8));
        g.fillRect(px[0] - 6, px[1] - 6, 12, 12);
        g.setColor(new Color(254, 240, 138));
        g.drawRect(px[0] - 6, px[1] - 6, 12, 12);
    }

    private static void appendAllSearchAreas(StringBuilder sb, Mission mission, MapLayout layout, double scale) {
        List<Target> targets = mission.targets();
        for (int i = 0; i < targets.size(); i++) {
            String stroke = TARGET_STROKES[i % TARGET_STROKES.length];
            appendPolygon(
                    sb, targets.get(i).searchArea().polygon(), layout, scale, "none", stroke, 2, "8,4");
        }
    }

    private static void drawAllSearchAreas(Graphics2D g, Mission mission, MapLayout layout, double scale) {
        List<Target> targets = mission.targets();
        for (int i = 0; i < targets.size(); i++) {
            Color stroke = Color.decode(TARGET_STROKES[i % TARGET_STROKES.length]);
            drawPolygon(g, targets.get(i).searchArea().polygon(), layout, scale, null, stroke, 2);
        }
    }

    private static void appendAllTargets(
            StringBuilder sb, Mission mission, MapLayout layout, double scale, MissionReplay replay) {
        List<Target> targets = mission.targets();
        for (int i = 0; i < targets.size(); i++) {
            Target t = targets.get(i);
            if (t.lastKnownCell() == null) {
                continue;
            }
            String marker = TARGET_MARKERS[i % TARGET_MARKERS.length];
            Long foundAt = targetFoundAt(replay, t.id());
            int[] px = layout.toPx(t.lastKnownCell().x() + 0.5, t.lastKnownCell().y() + 0.5, scale);
            sb.append("<circle cx=\"")
                    .append(px[0])
                    .append("\" cy=\"")
                    .append(px[1])
                    .append("\" r=\"7\" fill=\"none\" stroke=\"")
                    .append(marker)
                    .append("\" stroke-width=\"2\"/>\n");
            sb.append("<circle cx=\"")
                    .append(px[0])
                    .append("\" cy=\"")
                    .append(px[1])
                    .append("\" r=\"3\" fill=\"")
                    .append(marker)
                    .append("\"/>\n");
            sb.append("<text x=\"")
                    .append(px[0] + 10)
                    .append("\" y=\"")
                    .append(px[1] + 4)
                    .append("\" fill=\"")
                    .append(marker)
                    .append("\" font-size=\"10\">")
                    .append(escape(t.id()))
                    .append(" · ")
                    .append(foundAt != null ? "FOUND t" + foundAt : "NOT FOUND")
                    .append("</text>\n");
        }
    }

    private static void drawAllTargets(
            Graphics2D g, Mission mission, MapLayout layout, double scale, MissionReplay replay) {
        List<Target> targets = mission.targets();
        for (int i = 0; i < targets.size(); i++) {
            Target t = targets.get(i);
            if (t.lastKnownCell() == null) {
                continue;
            }
            Color marker = Color.decode(TARGET_MARKERS[i % TARGET_MARKERS.length]);
            int[] px = layout.toPx(t.lastKnownCell().x() + 0.5, t.lastKnownCell().y() + 0.5, scale);
            g.setColor(marker);
            g.drawOval(px[0] - 7, px[1] - 7, 14, 14);
            g.fillOval(px[0] - 3, px[1] - 3, 6, 6);
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10));
            Long foundAt = targetFoundAt(replay, t.id());
            String label = t.id() + " · " + (foundAt != null ? "FOUND t" + foundAt : "NOT FOUND");
            g.drawString(label, px[0] + 10, px[1] + 4);
        }
    }

    private static Long targetFoundAt(MissionReplay replay, String targetId) {
        if (replay == null || replay.targets() == null) {
            return null;
        }
        for (MissionReplay.TargetReplay t : replay.targets()) {
            if (targetId.equals(t.id())) {
                return t.foundAtTick();
            }
        }
        return null;
    }

    private static void appendMissionStatus(StringBuilder sb, MissionReplay replay) {
        if (replay == null || replay.summary() == null) {
            return;
        }
        MissionReplay.SimulationSummary s = replay.summary();
        int x = CANVAS - 280;
        int y = CANVAS - 120;
        sb.append("<rect x=\"")
                .append(x - 12)
                .append("\" y=\"")
                .append(y - 18)
                .append("\" width=\"268\" height=\"108\" rx=\"8\" fill=\"#1e293b\" fill-opacity=\"0.92\" stroke=\"#475569\"/>\n");
        sb.append("<text x=\"")
                .append(x)
                .append("\" y=\"")
                .append(y)
                .append("\" fill=\"#e2e8f0\" font-family=\"system-ui,sans-serif\" font-size=\"13\" font-weight=\"600\">Mission status</text>\n");
        y += 18;
        sb.append("<text x=\"")
                .append(x)
                .append("\" y=\"")
                .append(y)
                .append("\" fill=\"")
                .append(s.allTargetsFound() ? "#4ade80" : "#f87171")
                .append("\" font-family=\"system-ui,sans-serif\" font-size=\"12\">")
                .append(s.allTargetsFound() ? "All targets found" : "Targets missed: "
                        + (s.missedTargetIds() == null ? "?" : String.join(", ", s.missedTargetIds())))
                .append("</text>\n");
        y += 16;
        sb.append("<text x=\"")
                .append(x)
                .append("\" y=\"")
                .append(y)
                .append("\" fill=\"#94a3b8\" font-family=\"system-ui,sans-serif\" font-size=\"11\">Ticks: ")
                .append(s.ticksRun())
                .append(" · searched cells: ")
                .append(s.searchedCellCount())
                .append(" · landed: ")
                .append(s.allRtbLanded())
                .append("</text>\n");
        y += 16;
        if (replay.targets() != null) {
            for (MissionReplay.TargetReplay t : replay.targets()) {
                String line = t.id()
                        + " ("
                        + t.type()
                        + "): "
                        + (t.foundAtTick() != null
                                ? "found tick " + t.foundAtTick()
                                : "not found — end of search");
                sb.append("<text x=\"")
                        .append(x)
                        .append("\" y=\"")
                        .append(y)
                        .append("\" fill=\"#cbd5e1\" font-family=\"system-ui,sans-serif\" font-size=\"11\">")
                        .append(escape(line))
                        .append("</text>\n");
                y += 14;
            }
        }
    }

    private static void drawMissionStatus(Graphics2D g, MissionReplay replay) {
        if (replay == null || replay.summary() == null) {
            return;
        }
        MissionReplay.SimulationSummary s = replay.summary();
        int x = CANVAS - 280;
        int y = CANVAS - 120;
        g.setColor(new Color(30, 41, 59, 235));
        g.fillRoundRect(x - 12, y - 18, 268, 108, 8, 8);
        g.setColor(new Color(71, 85, 105));
        g.drawRoundRect(x - 12, y - 18, 268, 108, 8, 8);
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 13));
        g.setColor(new Color(226, 232, 240));
        g.drawString("Mission status", x, y);
        y += 18;
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12));
        g.setColor(s.allTargetsFound() ? new Color(74, 222, 128) : new Color(248, 113, 113));
        g.drawString(
                s.allTargetsFound()
                        ? "All targets found"
                        : "Targets missed: "
                                + (s.missedTargetIds() == null ? "?" : String.join(", ", s.missedTargetIds())),
                x,
                y);
        y += 16;
        g.setColor(new Color(148, 163, 184));
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11));
        g.drawString(
                "Ticks: "
                        + s.ticksRun()
                        + " · searched cells: "
                        + s.searchedCellCount()
                        + " · landed: "
                        + s.allRtbLanded(),
                x,
                y);
        y += 16;
        if (replay.targets() != null) {
            g.setColor(new Color(203, 213, 225));
            for (MissionReplay.TargetReplay t : replay.targets()) {
                String line = t.id()
                        + " ("
                        + t.type()
                        + "): "
                        + (t.foundAtTick() != null
                                ? "found tick " + t.foundAtTick()
                                : "not found — end of search");
                g.drawString(line, x, y);
                y += 14;
            }
        }
    }

    private static String geofenceLegend(MissionReplay replay) {
        if (replay == null || replay.noFlyZones() == null || replay.noFlyZones().isEmpty()) {
            return "";
        }
        boolean violation = replay.summary() != null && replay.summary().anyGeofenceViolation();
        return "<text x=\"20\" y=\""
                + (CANVAS - 100)
                + "\" fill=\"#f87171\" font-family=\"system-ui,sans-serif\" font-size=\"11\">No-fly zones: "
                + replay.noFlyZones().size()
                + (violation ? " · VIOLATION" : "")
                + "</text>\n";
    }

    private static void drawGeofenceLegend(Graphics2D g, MissionReplay replay) {
        if (replay == null || replay.noFlyZones() == null || replay.noFlyZones().isEmpty()) {
            return;
        }
        boolean violation = replay.summary() != null && replay.summary().anyGeofenceViolation();
        g.setColor(violation ? new Color(248, 113, 113) : new Color(148, 163, 184));
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11));
        g.drawString(
                "No-fly zones: " + replay.noFlyZones().size() + (violation ? " · VIOLATION" : ""),
                20,
                CANVAS - 100);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    record MapLayout(double minX, double minY, double maxX, double maxY, double cellSizeM) {
        double scale(int canvas, int margin) {
            double w = maxX - minX;
            double h = maxY - minY;
            return Math.min((canvas - 2.0 * margin) / w, (canvas - 2.0 * margin) / h);
        }

        int[] toPx(double cellX, double cellY, double scale) {
            int x = (int) Math.round(margin(CANVAS) + (cellX - minX) * scale);
            int y = (int) Math.round(CANVAS - margin(CANVAS) - (cellY - minY) * scale);
            return new int[] {x, y};
        }

        private static int margin(int canvas) {
            return MARGIN;
        }
    }
}
