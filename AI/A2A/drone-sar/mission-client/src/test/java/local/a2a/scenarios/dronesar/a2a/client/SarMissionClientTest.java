package local.a2a.scenarios.dronesar.a2a.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SarMissionClientTest {

    @Test
    void classifiesSummaryArtifactJson() throws Exception {
        String summaryJson =
                """
                {
                  "ticksRun": 42,
                  "searchedCells": 100,
                  "allRtbLanded": true,
                  "anyEmergencyLand": false,
                  "anyGeofenceViolation": false,
                  "allTargetsFound": true,
                  "fleetRtbTick": 40,
                  "foundTargetIds": ["t1"],
                  "successCriteriaMet": true
                }
                """;
        var node = local.a2a.scenarios.dronesar.a2a.SarJson.mapper().readTree(summaryJson);
        assertEquals(42, node.path("ticksRun").asInt());
        assertTrue(node.path("successCriteriaMet").asBoolean());
    }
}
