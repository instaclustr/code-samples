package local.a2a.scenarios.dronesar.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SarMissionRequestTest {

    @Test
    void parsesMultilineMissionRequest() {
        SarMissionRequest req = SarMissionRequest.parse(
                """
                mission:search-rescue
                ../missions/test-mission-fast.json
                maxTicks=120
                """);
        assertTrue(req.missionPath().toString().endsWith("test-mission-fast.json"));
        assertEquals(120, req.maxTicks());
    }

    @Test
    void rejectsMissingPath() {
        assertThrows(IllegalArgumentException.class, () -> SarMissionRequest.parse("mission:search-rescue"));
    }
}
