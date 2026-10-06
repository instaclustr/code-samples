package local.a2a.scenarios.dronesar.a2a;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;

/** Parses {@code mission:patch} messages for mid-mission updates (Phase 2c). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SarMissionPatchRequest(
        int version,
        String type,
        Double baseXM,
        Double baseYM,
        String droneId,
        String reason) {

    public static final String PREFIX = "mission:patch";

    public static SarMissionPatchRequest parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Empty patch request");
        }
        String normalized = text.trim();
        if (!normalized.toLowerCase(Locale.ROOT).startsWith(PREFIX)) {
            throw new IllegalArgumentException("Expected message to start with " + PREFIX);
        }
        String body = normalized.substring(PREFIX.length()).trim();
        if (body.startsWith("{")) {
            try {
                return SarJson.mapper().readValue(body, SarMissionPatchRequest.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid patch JSON: " + e.getMessage(), e);
            }
        }
        throw new IllegalArgumentException("Patch body must be JSON after " + PREFIX);
    }

    public String formatForClient() throws Exception {
        ObjectMapper mapper = SarJson.mapper();
        return PREFIX + "\n" + mapper.writeValueAsString(this);
    }
}
