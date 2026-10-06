package local.a2a.scenarios.dronesar.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Template + optional Ollama safety assessments for Kafka violation pipeline. */
public final class SarSafetyAnalystService {

    private static final ObjectMapper MAPPER = SarJson.mapper();

    private final OllamaClient ollama;
    private final boolean ollamaEnabled;

    public SarSafetyAnalystService(OllamaClient ollama, boolean ollamaEnabled) {
        this.ollama = ollama;
        this.ollamaEnabled = ollamaEnabled;
    }

    public static SarSafetyAnalystService fromEnv() {
        String ollamaUrl = System.getenv().getOrDefault("SAR_OLLAMA_URL", "http://localhost:11434");
        String model = System.getenv().getOrDefault("SAR_OLLAMA_MODEL", "llama3:latest");
        boolean enabled = !isDisabled(System.getenv("SAR_OLLAMA_ENABLED"));
        return new SarSafetyAnalystService(new OllamaClient(ollamaUrl, model), enabled);
    }

    public SarViolationAssessment assessEvent(String rulesetId, SarMissionEvent event) {
        SarViolationAssessment fallback = templateForEvent(rulesetId, event);
        if (!ollamaEnabled || ollama == null) {
            return fallback;
        }
        try {
            String raw = ollama.generate(
                    SarSafetyPrompts.SYSTEM_PROMPT, SarSafetyPrompts.userPromptForEvent(rulesetId, event));
            return parseAssessment(raw, event.missionId(), event.type(), event.tick(), event.droneId(), fallback);
        } catch (Exception ex) {
            return withNote(fallback, ex.getMessage());
        }
    }

    public SarViolationAssessment assessSummary(String rulesetId, String missionId, SarJson.MissionSummary summary) {
        SarViolationAssessment fallback = templateForSummary(rulesetId, missionId, summary);
        if (!ollamaEnabled || ollama == null) {
            return fallback;
        }
        try {
            String raw = ollama.generate(
                    SarSafetyPrompts.SYSTEM_PROMPT,
                    SarSafetyPrompts.userPromptForSummary(rulesetId, missionId, summary));
            return parseAssessment(raw, missionId, "MISSION_SAFETY_SUMMARY", summary.ticksRun(), null, fallback);
        } catch (Exception ex) {
            return withNote(fallback, ex.getMessage());
        }
    }

    static SarViolationAssessment templateForEvent(String rulesetId, SarMissionEvent event) {
        String prefix = rulesetId != null && !rulesetId.isBlank() ? rulesetId : "airspace-v1";
        return switch (event.type()) {
            case "GEOFENCE_VIOLATION" -> new SarViolationAssessment(
                    event.missionId(),
                    "HIGH",
                    prefix + ".geofence",
                    "Drone "
                            + event.droneId()
                            + " breached a no-fly zone at tick "
                            + event.tick()
                            + ".",
                    "FREEZE_SECTOR",
                    event.type(),
                    event.tick(),
                    event.droneId());
            case "EMERGENCY_LAND" -> new SarViolationAssessment(
                    event.missionId(),
                    "HIGH",
                    prefix + ".battery.emergency",
                    "Drone "
                            + event.droneId()
                            + " executed emergency land at tick "
                            + event.tick()
                            + " due to battery depletion.",
                    "HALT_MISSION_REVIEW",
                    event.type(),
                    event.tick(),
                    event.droneId());
            case "LOW_BATTERY" -> new SarViolationAssessment(
                    event.missionId(),
                    "MEDIUM",
                    prefix + ".battery.rtb",
                    "Drone "
                            + event.droneId()
                            + " reached RTB battery threshold while still searching at tick "
                            + event.tick()
                            + ".",
                    "ORDER_RTB",
                    event.type(),
                    event.tick(),
                    event.droneId());
            default -> new SarViolationAssessment(
                    event.missionId(),
                    "INFO",
                    prefix + ".general",
                    SarEventTemplates.narrate(event),
                    "MONITOR",
                    event.type(),
                    event.tick(),
                    event.droneId());
        };
    }

    static SarViolationAssessment templateForSummary(
            String rulesetId, String missionId, SarJson.MissionSummary summary) {
        String prefix = rulesetId != null && !rulesetId.isBlank() ? rulesetId : "airspace-v1";
        String severity = summary.anyEmergencyLand() || summary.anyGeofenceViolation() ? "HIGH" : "INFO";
        String action = summary.successCriteriaMet() ? "ARCHIVE_MISSION" : "REVIEW_VIOLATIONS";
        return new SarViolationAssessment(
                missionId,
                severity,
                prefix + ".mission.summary",
                SarEventTemplates.narrateSummary(missionId, summary),
                action,
                "MISSION_SAFETY_SUMMARY",
                summary.ticksRun(),
                null);
    }

    private static SarViolationAssessment parseAssessment(
            String raw,
            String missionId,
            String sourceType,
            long tick,
            String droneId,
            SarViolationAssessment fallback) {
        try {
            String json = extractJsonObject(raw);
            JsonNode node = MAPPER.readTree(json);
            return new SarViolationAssessment(
                    missionId,
                    node.path("severity").asText(fallback.severity()),
                    node.path("ruleId").asText(fallback.ruleId()),
                    node.path("summary").asText(fallback.summary()),
                    node.path("recommendedAction").asText(fallback.recommendedAction()),
                    sourceType,
                    tick,
                    droneId);
        } catch (Exception ex) {
            return withNote(fallback, ex.getMessage());
        }
    }

    private static SarViolationAssessment withNote(SarViolationAssessment fallback, String note) {
        return new SarViolationAssessment(
                fallback.missionId(),
                fallback.severity(),
                fallback.ruleId(),
                fallback.summary() + " [template fallback: " + note + "]",
                fallback.recommendedAction(),
                fallback.sourceEventType(),
                fallback.tick(),
                fallback.droneId());
    }

    static String extractJsonObject(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    private static boolean isDisabled(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String v = raw.trim().toLowerCase();
        return "false".equals(v) || "off".equals(v) || "none".equals(v) || "0".equals(v);
    }
}
