package local.a2a.scenarios.dronesar.sim;

import java.util.ArrayList;
import java.util.List;
import local.a2a.scenarios.dronesar.model.Cell;
import local.a2a.scenarios.dronesar.model.GeofenceSpec;
import local.a2a.scenarios.dronesar.model.Mission;
import local.a2a.scenarios.dronesar.model.SearchArea;
import local.a2a.scenarios.dronesar.model.Target;
import local.a2a.scenarios.dronesar.world.PolygonUtil;

public final class MissionValidator {
    private MissionValidator() {}

    public static void validate(Mission mission) {
        List<List<List<Double>>> zones = noFlyPolygons(mission.geofenceOrDefault());
        if (zones.isEmpty()) {
            return;
        }
        if (mission.base() != null) {
            assertClearCell("base", mission.base().cellX(), mission.base().cellY(), zones);
        }
        if (mission.targets() == null) {
            return;
        }
        for (Target target : mission.targets()) {
            Cell lkp = target.lastKnownCell();
            if (lkp == null) {
                continue;
            }
            assertClearCell("target " + target.id() + " lastKnownCell", lkp.x(), lkp.y(), zones);
        }
    }

    private static void assertClearCell(String label, double xCell, double yCell, List<List<List<Double>>> zones) {
        for (int i = 0; i < zones.size(); i++) {
            if (PolygonUtil.contains(zones.get(i), xCell, yCell)) {
                throw new MissionValidationException(
                        label + " (" + fmt(xCell) + "," + fmt(yCell) + ") is inside no-fly zone " + (i + 1));
            }
        }
    }

    private static List<List<List<Double>>> noFlyPolygons(GeofenceSpec geofence) {
        List<List<List<Double>>> zones = new ArrayList<>();
        if (geofence.noFlyZones() == null) {
            return zones;
        }
        for (SearchArea zone : geofence.noFlyZones()) {
            if (zone.polygon() != null && zone.polygon().size() >= 3) {
                zones.add(zone.polygon());
            }
        }
        return zones;
    }

    private static String fmt(double v) {
        return v == (long) v ? Long.toString((long) v) : Double.toString(v);
    }
}
