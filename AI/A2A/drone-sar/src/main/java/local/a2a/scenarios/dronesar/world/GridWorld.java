package local.a2a.scenarios.dronesar.world;

import java.util.ArrayList;
import java.util.List;
import local.a2a.scenarios.dronesar.model.GeofenceSpec;
import local.a2a.scenarios.dronesar.model.Mission;
import local.a2a.scenarios.dronesar.model.RulesSpec;
import local.a2a.scenarios.dronesar.model.SearchArea;

public final class GridWorld {
    private final int gridCells;
    private final double cellSizeM;
    private final double[] groundHeightM;
    private final double[] foliageHeightM;
    private final List<List<List<Double>>> noFlyPolygons;
    private final RulesSpec rules;
    private final boolean[][] searched;

    public GridWorld(Mission mission) {
        var sim = mission.simOrDefault();
        this.gridCells = sim.gridCells();
        this.cellSizeM = sim.cellSizeM();
        this.rules = mission.rulesOrDefault();
        this.groundHeightM = new double[gridCells * gridCells];
        this.foliageHeightM = new double[gridCells * gridCells];
        this.searched = new boolean[gridCells][gridCells];
        this.noFlyPolygons = new ArrayList<>();
        GeofenceSpec gf = mission.geofenceOrDefault();
        if (gf.noFlyZones() != null) {
            for (SearchArea zone : gf.noFlyZones()) {
                if (zone.polygon() != null) {
                    noFlyPolygons.add(zone.polygon());
                }
            }
        }
        generateTerrain();
    }

    private void generateTerrain() {
        for (int y = 0; y < gridCells; y++) {
            for (int x = 0; x < gridCells; x++) {
                int idx = index(x, y);
                groundHeightM[idx] = 5 + 3 * Math.sin(x * 0.08) * Math.cos(y * 0.06);
                foliageHeightM[idx] = (x + y) % 17 == 0 ? 8 : 0;
            }
        }
    }

    public int gridCells() {
        return gridCells;
    }

    public double cellSizeM() {
        return cellSizeM;
    }

    public RulesSpec rules() {
        return rules;
    }

    public double metersX(int cellX) {
        return cellX * cellSizeM;
    }

    public double metersY(int cellY) {
        return cellY * cellSizeM;
    }

    public int cellFromMeters(double m) {
        return (int) Math.floor(m / cellSizeM);
    }

    public double groundAtCell(int cx, int cy) {
        if (!inGrid(cx, cy)) {
            return 0;
        }
        return groundHeightM[index(cx, cy)];
    }

    public double minAglAt(double xM, double yM, double altAglM) {
        int cx = cellFromMeters(xM);
        int cy = cellFromMeters(yM);
        double ground = groundAtCell(cx, cy);
        double foliage = inGrid(cx, cy) ? foliageHeightM[index(cx, cy)] : 0;
        double required = ground + foliage + rules.minAglM();
        return Math.max(altAglM, required);
    }

    public boolean isNoFly(double xM, double yM) {
        double xCell = xM / cellSizeM;
        double yCell = yM / cellSizeM;
        for (List<List<Double>> poly : noFlyPolygons) {
            if (PolygonUtil.contains(poly, xCell, yCell)) {
                return true;
            }
        }
        return false;
    }

    public boolean isInSearchArea(List<List<Double>> polygon, double xM, double yM) {
        double xCell = xM / cellSizeM;
        double yCell = yM / cellSizeM;
        return PolygonUtil.contains(polygon, xCell, yCell)
                || PolygonUtil.contains(polygon, xM, yM);
    }

    public boolean isSearched(int cx, int cy) {
        return inGrid(cx, cy) && searched[cx][cy];
    }

    public void markSearched(int cx, int cy) {
        if (inGrid(cx, cy)) {
            searched[cx][cy] = true;
        }
    }

    public int searchedCellCount() {
        int count = 0;
        for (int y = 0; y < gridCells; y++) {
            for (int x = 0; x < gridCells; x++) {
                if (searched[x][y]) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Copy searched-cell flags for Phase 2b coordinator ↔ drone worker sync. */
    public boolean[][] copySearchedMatrix() {
        boolean[][] copy = new boolean[gridCells][gridCells];
        for (int x = 0; x < gridCells; x++) {
            System.arraycopy(searched[x], 0, copy[x], 0, gridCells);
        }
        return copy;
    }

    /** Apply coordinator-owned searched state before a remote drone tick. */
    public void applySearchedMatrix(boolean[][] source) {
        if (source == null || source.length != gridCells) {
            return;
        }
        for (int x = 0; x < gridCells; x++) {
            if (source[x].length != gridCells) {
                continue;
            }
            System.arraycopy(source[x], 0, searched[x], 0, gridCells);
        }
    }

    /** Cell coordinates [x,y] of all searched cells (for heatmap export). */
    public List<int[]> searchedCellCoords() {
        List<int[]> coords = new ArrayList<>();
        for (int y = 0; y < gridCells; y++) {
            for (int x = 0; x < gridCells; x++) {
                if (searched[x][y]) {
                    coords.add(new int[] {x, y});
                }
            }
        }
        return coords;
    }

    public List<List<List<Double>>> noFlyPolygons() {
        return noFlyPolygons;
    }

    public boolean inGrid(int cx, int cy) {
        return cx >= 0 && cy >= 0 && cx < gridCells && cy < gridCells;
    }

    private int index(int x, int y) {
        return y * gridCells + x;
    }
}
