package local.a2a.scenarios.dronesar.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;

/** Parses A2A user messages for mission narrator tasks. */
public record SarNarratorRequest(Kind kind, SarMissionEvent event, JsonNode summaryPayload) {

    public enum Kind {
        EVENT,
        SUMMARY
    }

    public static final String PREFIX_EVENT = "narrate:event";
    public static final String PREFIX_SUMMARY = "narrate:summary";

    public static SarNarratorRequest parse(String text) throws Exception {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Empty narrator request");
        }
        String normalized = text.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith(PREFIX_EVENT)) {
            String json = normalized.substring(PREFIX_EVENT.length()).trim();
            SarMissionEvent event = SarJson.mapper().readValue(json, SarMissionEvent.class);
            if (event.type() == null || event.type().isBlank()) {
                throw new IllegalArgumentException("Event type is required");
            }
            return new SarNarratorRequest(Kind.EVENT, event, null);
        }
        if (lower.startsWith(PREFIX_SUMMARY)) {
            String json = normalized.substring(PREFIX_SUMMARY.length()).trim();
            JsonNode payload = SarJson.mapper().readTree(json);
            return new SarNarratorRequest(Kind.SUMMARY, null, payload);
        }
        throw new IllegalArgumentException("Expected " + PREFIX_EVENT + " or " + PREFIX_SUMMARY);
    }

    public static String formatEvent(SarMissionEvent event) throws Exception {
        return PREFIX_EVENT + "\n" + SarJson.mapper().writeValueAsString(event);
    }

    public static String formatEventSafe(SarMissionEvent event) {
        try {
            return formatEvent(event);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not format narrator event", ex);
        }
    }

    public static String formatSummary(String missionId, SarJson.MissionSummary summary) throws Exception {
        String json = SarJson.mapper()
                .createObjectNode()
                .put("missionId", missionId)
                .set("summary", SarJson.mapper().valueToTree(summary))
                .toString();
        return PREFIX_SUMMARY + "\n" + json;
    }
}
