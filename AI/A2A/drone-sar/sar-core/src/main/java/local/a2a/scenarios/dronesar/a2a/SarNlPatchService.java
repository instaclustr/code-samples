package local.a2a.scenarios.dronesar.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts operator NL text to validated {@link SarMissionPatchRequest} (Phase 2d). */
public final class SarNlPatchService {

    private static final Pattern HOLD =
            Pattern.compile("hold\\s+(?:drone\\s+)?(d-\\d{2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern RESUME =
            Pattern.compile("resume\\s+(?:drone\\s+)?(d-\\d{2})", Pattern.CASE_INSENSITIVE);

    private final OllamaClient ollama;
    private final boolean ollamaEnabled;

    public SarNlPatchService(OllamaClient ollama, boolean ollamaEnabled) {
        this.ollama = ollama;
        this.ollamaEnabled = ollamaEnabled;
    }

    public static SarNlPatchService fromEnv() {
        String ollamaUrl = System.getenv().getOrDefault("SAR_OLLAMA_URL", "http://localhost:11434");
        String model = System.getenv().getOrDefault("SAR_OLLAMA_MODEL", "llama3:latest");
        boolean enabled = !isDisabled(System.getenv("SAR_OLLAMA_ENABLED"));
        return new SarNlPatchService(new OllamaClient(ollamaUrl, model), enabled);
    }

    public SarMissionPatchRequest toPatch(String operatorText, int nextVersion) {
        if (operatorText == null || operatorText.isBlank()) {
            throw new IllegalArgumentException("Empty operator text");
        }
        if (ollamaEnabled && ollama != null) {
            try {
                String raw = ollama.generate(
                        SarNlPatchPrompts.SYSTEM_PROMPT,
                        SarNlPatchPrompts.userPrompt(operatorText.trim(), nextVersion));
                return parseJsonPatch(stripMarkdown(raw), nextVersion);
            } catch (Exception ignored) {
                // fall through to template
            }
        }
        return templatePatch(operatorText.trim(), nextVersion);
    }

    static SarMissionPatchRequest templatePatch(String text, int nextVersion) {
        String lower = text.toLowerCase(Locale.ROOT);
        Matcher hold = HOLD.matcher(lower);
        if (hold.find()) {
            return new SarMissionPatchRequest(
                    nextVersion, "hold_drone", null, null, hold.group(1), text);
        }
        Matcher resume = RESUME.matcher(lower);
        if (resume.find()) {
            return new SarMissionPatchRequest(
                    nextVersion, "resume_drone", null, null, resume.group(1), text);
        }
        if (lower.contains("abort")) {
            return new SarMissionPatchRequest(nextVersion, "abort", null, null, null, text);
        }
        throw new IllegalArgumentException("Could not map NL to patch: " + text);
    }

    private static SarMissionPatchRequest parseJsonPatch(String raw, int nextVersion) throws Exception {
        JsonNode node = SarJson.mapper().readTree(raw);
        int version = node.path("version").asInt(nextVersion);
        if (version <= 0) {
            version = nextVersion;
        }
        return new SarMissionPatchRequest(
                version,
                node.path("type").asText("hold_drone"),
                node.path("baseXM").isNull() ? null : node.path("baseXM").asDouble(),
                node.path("baseYM").isNull() ? null : node.path("baseYM").asDouble(),
                node.path("droneId").isNull() ? null : node.path("droneId").asText(),
                node.path("reason").asText("nl-patch"));
    }

    private static String stripMarkdown(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }
        return trimmed;
    }

    private static boolean isDisabled(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return "false".equals(v) || "off".equals(v) || "none".equals(v) || "0".equals(v);
    }
}
