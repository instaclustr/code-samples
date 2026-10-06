package local.a2a.scenarios.dronesar.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;

/** Shared narrator logic — templates plus optional Ollama polish. */
public final class SarNarratorService {

    private final OllamaClient ollama;
    private final boolean ollamaEnabled;

    public SarNarratorService(OllamaClient ollama, boolean ollamaEnabled) {
        this.ollama = ollama;
        this.ollamaEnabled = ollamaEnabled;
    }

    public static SarNarratorService fromEnv() {
        String ollamaUrl = System.getenv().getOrDefault("SAR_OLLAMA_URL", "http://localhost:11434");
        String model = System.getenv().getOrDefault("SAR_OLLAMA_MODEL", "llama3:latest");
        boolean enabled = !isDisabled(System.getenv("SAR_OLLAMA_ENABLED"));
        return new SarNarratorService(new OllamaClient(ollamaUrl, model), enabled);
    }

    public String narrateEvent(SarMissionEvent event) {
        String fallback = SarEventTemplates.narrate(event);
        if (!ollamaEnabled || ollama == null) {
            return fallback;
        }
        try {
            return ollama.generate(SarNarratorPrompts.SYSTEM_PROMPT, SarNarratorPrompts.userPromptForEvent(event));
        } catch (Exception ex) {
            return fallback + " [template fallback: " + ex.getMessage() + "]";
        }
    }

    public String narrateSummary(JsonNode payload) {
        String missionId = payload.path("missionId").asText("unknown");
        SarJson.MissionSummary summary;
        try {
            summary = SarJson.mapper().treeToValue(payload.path("summary"), SarJson.MissionSummary.class);
        } catch (Exception ex) {
            return "Mission summary received but could not be parsed.";
        }
        String fallback = SarEventTemplates.narrateSummary(missionId, summary);
        if (!ollamaEnabled || ollama == null) {
            return fallback;
        }
        try {
            String llm = ollama.generate(
                    SarNarratorPrompts.SYSTEM_PROMPT, SarNarratorPrompts.userPromptForSummary(missionId, summary));
            if (contradictsAllTargetsFound(llm, summary)) {
                return fallback + " [narrator corrected: LLM misread success flag]";
            }
            return llm;
        } catch (Exception ex) {
            return fallback + " [template fallback: " + ex.getMessage() + "]";
        }
    }

    /** Reject common LLM mistake: successCriteriaMet=false interpreted as missing targets. */
    static boolean contradictsAllTargetsFound(String text, SarJson.MissionSummary summary) {
        if (!summary.allTargetsFound() || text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("missing target")
                || lower.contains("missing some target")
                || lower.contains("targets missed")
                || lower.contains("target missed")
                || lower.contains("did not find")
                || lower.contains("failed to find")
                || lower.contains("not find all")
                || lower.contains("without finding");
    }

    private static boolean isDisabled(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String v = raw.trim().toLowerCase();
        return "false".equals(v) || "off".equals(v) || "none".equals(v) || "0".equals(v);
    }
}
