package local.a2a.scenarios.dronesar.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SarCopilotRecorderTest {

    @Test
    void emptyRecorderReturnsNullCopilot() {
        SarCopilotRecorder recorder = new SarCopilotRecorder();
        assertTrue(recorder.isEmpty());
        assertNull(recorder.toReplayCopilot());
    }

    @Test
    void collectsNarrationsAndViolations() {
        SarCopilotRecorder recorder = new SarCopilotRecorder();
        recorder.addNarration(42, "GEOFENCE_VIOLATION", "Drone d-01 entered restricted airspace.");
        recorder.addNarrations(List.of(new SarNarrationEntry(100, "MISSION_SUMMARY", "Mission complete.")));

        SarViolationAssessment assessment = new SarViolationAssessment(
                "m1",
                "HIGH",
                "airspace-v1.geofence",
                "No-fly breach",
                "FREEZE_SECTOR",
                "GEOFENCE_VIOLATION",
                42,
                "d-01");
        recorder.addViolation(assessment);

        assertFalse(recorder.isEmpty());
        var copilot = recorder.toReplayCopilot();
        assertNotNull(copilot);
        assertEquals(2, copilot.narrations().size());
        assertEquals(1, copilot.violations().size());
        assertEquals("narrator", copilot.narrations().get(0).source());
        assertEquals("HIGH", copilot.violations().get(0).severity());
    }
}
