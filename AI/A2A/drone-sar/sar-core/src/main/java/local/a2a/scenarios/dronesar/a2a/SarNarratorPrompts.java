package local.a2a.scenarios.dronesar.a2a;

/** LLM prompt assembly for the mission narrator agent. */
public final class SarNarratorPrompts {

    public static final String SYSTEM_PROMPT =
            """
            You are a search-and-rescue mission control narrator.
            Write one or two concise, factual sentences for operators.
            Use past tense for completed events. No speculation or recommendations.
            Do not invent targets, drones, or ticks not present in the input.
            Reply with ONLY the briefing text — no labels, quotes, or preamble.""";

    private SarNarratorPrompts() {}

    public static String userPromptForEvent(SarMissionEvent event) throws Exception {
        return "Turn this SAR event into an operator briefing line:\n"
                + SarJson.mapper().writeValueAsString(event);
    }

    public static String userPromptForSummary(String missionId, SarJson.MissionSummary summary) throws Exception {
        return """
                Write a short after-action summary (2-3 sentences) for mission %s.

                Field semantics (do not invert these):
                - allTargetsFound=true: every search target was located; never say targets were missed or missing.
                - successCriteriaMet=false: mission failed clean completion — usually geofence violations, emergency land, or not all drones landed (see anyGeofenceViolation, anyEmergencyLand, allRtbLanded).
                - When allTargetsFound is true but successCriteriaMet is false, report targets found AND cite the actual failure reasons from the JSON.

                Mission summary JSON:
                %s"""
                .formatted(missionId, SarJson.mapper().writeValueAsString(summary));
    }
}
