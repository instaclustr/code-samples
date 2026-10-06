package local.a2a.scenarios.dronesar.a2a;

import java.nio.file.Path;
import java.util.Locale;

/** Parses A2A user messages for {@code mission:search-rescue} tasks. */
public record SarMissionRequest(Path missionPath, int maxTicks, boolean realtime) {

    public static final String PREFIX = "mission:search-rescue";

    public static SarMissionRequest parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Empty mission request");
        }
        String normalized = text.trim();
        if (!normalized.toLowerCase(Locale.ROOT).startsWith(PREFIX)) {
            throw new IllegalArgumentException(
                    "Expected message to start with " + PREFIX + " (see Phase 2 README)");
        }
        String body = normalized.substring(PREFIX.length()).trim();
        Path missionPath = null;
        int maxTicks = 600;
        boolean realtime = false;
        for (String line : body.split("\\R")) {
            String token = line.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (token.toLowerCase(Locale.ROOT).startsWith("maxticks=")) {
                maxTicks = Integer.parseInt(token.substring("maxticks=".length()).trim());
                continue;
            }
            if (token.toLowerCase(Locale.ROOT).startsWith("realtime=")) {
                realtime = Boolean.parseBoolean(token.substring("realtime=".length()).trim());
                continue;
            }
            if (token.toLowerCase(Locale.ROOT).startsWith("path=")) {
                missionPath = Path.of(token.substring("path=".length()).trim());
                continue;
            }
            if (missionPath == null && !token.contains("=")) {
                missionPath = Path.of(token);
            }
        }
        if (missionPath == null) {
            throw new IllegalArgumentException("Mission path missing — add a file path after " + PREFIX);
        }
        if (maxTicks <= 0) {
            throw new IllegalArgumentException("maxTicks must be positive");
        }
        return new SarMissionRequest(missionPath.toAbsolutePath().normalize(), maxTicks, realtime);
    }

    public String formatForClient() {
        return PREFIX + "\n" + missionPath + "\nmaxTicks=" + maxTicks + (realtime ? "\nrealtime=true" : "");
    }
}
