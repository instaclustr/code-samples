package local.a2a.scenarios.dronesar.sim;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import local.a2a.scenarios.dronesar.model.Mission;
import org.junit.jupiter.api.Test;

class MissionValidatorTest {

    @Test
    void geofenceMissionTargetsAreOutsideNoFlyZones() throws Exception {
        Path missionPath =
                Path.of("../missions/test-mission-three-target-geofence.json").toAbsolutePath().normalize();
        Mission mission = MissionLoader.load(missionPath);
        assertDoesNotThrow(() -> MissionValidator.validate(mission));
    }

    @Test
    void rejectsTargetLastKnownCellInsideNoFlyZone() throws Exception {
        String json =
                """
                {
                  "missionId": "invalid-lkp",
                  "targets": [
                    {
                      "id": "t1",
                      "type": "person",
                      "priority": 1,
                      "lastKnownCell": { "x": 22, "y": 22 }
                    }
                  ],
                  "geofence": {
                    "noFlyZones": [
                      { "polygon": [[20, 18], [24, 18], [24, 26], [20, 26]] }
                    ]
                  }
                }
                """;
        Mission mission = MissionLoader.mapper().readValue(json, Mission.class);
        MissionValidationException ex =
                assertThrows(MissionValidationException.class, () -> MissionValidator.validate(mission));
        assertTrue(ex.getMessage().contains("target t1 lastKnownCell"));
        assertTrue(ex.getMessage().contains("no-fly zone"));
    }
}
