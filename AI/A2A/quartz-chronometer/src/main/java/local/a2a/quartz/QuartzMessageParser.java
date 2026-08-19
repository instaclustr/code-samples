package local.a2a.quartz;

import java.util.Locale;

/** Parses Clockwork-style user prompts (same semantics as Part 5). */
public final class QuartzMessageParser {

    private QuartzMessageParser() {}

    public static boolean isTimeQuery(String normalized) {
        return normalized.contains("time");
    }

    public static boolean requiresConfirmation(String normalized) {
        return normalized.contains("confirm");
    }

    public static int parseCountdownSeconds(String normalized) {
        String[] tokens = normalized.replaceAll("[^a-z0-9 ]", " ").split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].matches("\\d+")) {
                int value = Integer.parseInt(tokens[i]);
                if (i + 1 < tokens.length && tokens[i + 1].startsWith("min")) {
                    return value * 60;
                }
                return value;
            }
        }
        return 0;
    }

    public static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}
