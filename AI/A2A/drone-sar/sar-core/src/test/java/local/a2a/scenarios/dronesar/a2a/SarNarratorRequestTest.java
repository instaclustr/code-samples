package local.a2a.scenarios.dronesar.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SarNarratorRequestTest {

    @Test
    void parsesEventRequest() throws Exception {
        SarNarratorRequest request = SarNarratorRequest.parse(
                """
                narrate:event
                {"type":"TARGET_FOUND","tick":20,"missionId":"sar-multi-001","targetId":"t1","droneId":"d-02"}
                """);
        assertEquals(SarNarratorRequest.Kind.EVENT, request.kind());
        assertEquals("TARGET_FOUND", request.event().type());
        assertEquals(20, request.event().tick());
    }

    @Test
    void formatsAndParsesSummaryRequest() throws Exception {
        SarJson.MissionSummary summary = new SarJson.MissionSummary(
                64, 143, true, false, false, true, 48L, java.util.List.of("t1", "t2"), true);
        String text = SarNarratorRequest.formatSummary("sar-multi-001", summary);
        SarNarratorRequest request = SarNarratorRequest.parse(text);
        assertEquals(SarNarratorRequest.Kind.SUMMARY, request.kind());
        assertEquals("sar-multi-001", request.summaryPayload().path("missionId").asText());
    }
}
