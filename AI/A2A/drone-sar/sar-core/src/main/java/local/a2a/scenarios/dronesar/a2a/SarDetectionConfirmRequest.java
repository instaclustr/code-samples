package local.a2a.scenarios.dronesar.a2a;

import java.util.Locale;

/** Parses operator confirm/reject for low-confidence detections (Phase 2d). */
public record SarDetectionConfirmRequest(boolean confirmed, String targetId) {

    public static final String CONFIRM_PREFIX = "detection:confirm";
    public static final String REJECT_PREFIX = "detection:reject";

    public static SarDetectionConfirmRequest parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Empty detection confirm request");
        }
        String normalized = text.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        boolean confirmed;
        String body;
        if (lower.startsWith(CONFIRM_PREFIX)) {
            confirmed = true;
            body = normalized.substring(CONFIRM_PREFIX.length()).trim();
        } else if (lower.startsWith(REJECT_PREFIX)) {
            confirmed = false;
            body = normalized.substring(REJECT_PREFIX.length()).trim();
        } else if (lower.equals("confirm") || lower.startsWith("confirm ")) {
            confirmed = true;
            body = normalized.length() > 7 ? normalized.substring(7).trim() : "";
        } else if (lower.equals("reject") || lower.startsWith("reject ")) {
            confirmed = false;
            body = normalized.length() > 6 ? normalized.substring(6).trim() : "";
        } else {
            throw new IllegalArgumentException("Expected detection:confirm or detection:reject");
        }
        String targetId = body.isBlank() ? null : body.split("\\s+")[0];
        return new SarDetectionConfirmRequest(confirmed, targetId);
    }

    public String formatForClient() {
        return confirmed ? CONFIRM_PREFIX : REJECT_PREFIX;
    }
}
