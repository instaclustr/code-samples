package local.a2a.scenarios.dronesar.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PolygonUtilTest {

    @Test
    void containsPointInsideSquare() {
        List<List<Double>> square = List.of(
                List.of(0.0, 0.0), List.of(10.0, 0.0), List.of(10.0, 10.0), List.of(0.0, 10.0));
        assertTrue(PolygonUtil.contains(square, 5, 5));
        assertFalse(PolygonUtil.contains(square, 15, 5));
    }

    @Test
    void boundsComputesExtents() {
        List<List<Double>> poly = List.of(List.of(20.0, 30.0), List.of(100.0, 30.0), List.of(100.0, 90.0));
        PolygonUtil.Bounds b = PolygonUtil.bounds(poly);
        assertTrue(b.minX() == 20);
        assertTrue(b.maxX() == 100);
        assertTrue(b.minY() == 30);
        assertTrue(b.maxY() == 90);
    }
}
