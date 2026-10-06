package local.a2a.scenarios.dronesar.a2a;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SarNarratorServiceTest {

    @Test
    void detectsLlmSummaryThatMisreadsSuccessAsMissingTargets() {
        SarJson.MissionSummary summary = new SarJson.MissionSummary(
                74, 117, false, true, true, true, 59L, List.of("t1", "t2"), false);
        String bad =
                "Mission did not meet success criteria due to missing some targets despite RTB at tick 74.";
        assertTrue(SarNarratorService.contradictsAllTargetsFound(bad, summary));
    }

    @Test
    void acceptsSummaryThatCitesViolationsWhenAllTargetsFound() {
        SarJson.MissionSummary summary = new SarJson.MissionSummary(
                74, 117, false, true, true, true, 59L, List.of("t1", "t2"), false);
        String good =
                "All targets t1 and t2 were found. Mission failed success criteria due to geofence violations and d-03 emergency land.";
        assertFalse(SarNarratorService.contradictsAllTargetsFound(good, summary));
    }

    @Test
    void summaryPromptExplainsSuccessVsTargetsFound() throws Exception {
        SarJson.MissionSummary summary = new SarJson.MissionSummary(
                74, 117, false, true, true, true, 59L, List.of("t1", "t2"), false);
        String prompt = SarNarratorPrompts.userPromptForSummary("demo", summary);
        assertTrue(prompt.contains("allTargetsFound=true"));
        assertTrue(prompt.contains("never say targets were missed"));
    }
}
