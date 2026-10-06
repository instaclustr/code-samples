package local.a2a.scenarios.dronesar.a2a;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import local.a2a.scenarios.dronesar.viz.MissionReplay;

/** Collects narrator + safety-analyst outputs for replay GUI (Tier 2 copilot bundle). */
public final class SarCopilotRecorder {

    private final List<MissionReplay.CopilotNarration> narrations = Collections.synchronizedList(new ArrayList<>());
    private final List<MissionReplay.CopilotViolation> violations = Collections.synchronizedList(new ArrayList<>());

    public static boolean enabled() {
        String raw = System.getenv("SAR_COPILOT");
        if (raw == null || raw.isBlank()) {
            return true;
        }
        String v = raw.trim().toLowerCase();
        return !("false".equals(v) || "off".equals(v) || "none".equals(v) || "0".equals(v));
    }

    public void addNarration(long tick, String type, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        narrations.add(new MissionReplay.CopilotNarration(tick, type, text.trim(), "narrator"));
    }

    public void addNarrations(List<SarNarrationEntry> entries) {
        if (entries == null) {
            return;
        }
        for (SarNarrationEntry entry : entries) {
            addNarration(entry.tick(), entry.type(), entry.text());
        }
    }

    public void addViolation(SarViolationAssessment assessment) {
        if (assessment == null) {
            return;
        }
        violations.add(new MissionReplay.CopilotViolation(
                assessment.tick(),
                assessment.severity(),
                assessment.ruleId(),
                assessment.summary(),
                assessment.recommendedAction(),
                assessment.sourceEventType(),
                assessment.droneId()));
    }

    public MissionReplay.CopilotReplay toReplayCopilot() {
        if (narrations.isEmpty() && violations.isEmpty()) {
            return null;
        }
        return new MissionReplay.CopilotReplay(List.copyOf(narrations), List.copyOf(violations));
    }

    public boolean isEmpty() {
        return narrations.isEmpty() && violations.isEmpty();
    }
}
