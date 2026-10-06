package local.a2a.scenarios.dronesar.a2a;

public final class SarSafetyPrompts {

    static final String SYSTEM_PROMPT =
            """
            You are a safety compliance analyst for autonomous drone search-and-rescue missions.
            Respond with a single JSON object only (no markdown fences) using keys:
            severity (HIGH|MEDIUM|INFO), ruleId (e.g. airspace-v1.geofence), summary (one sentence),
            recommendedAction (short imperative, e.g. FREEZE_SECTOR or ORDER_RTB).
            Base severity on: GEOFENCE_VIOLATION and EMERGENCY_LAND are HIGH; LOW_BATTERY is MEDIUM;
            MISSION_SAFETY_SUMMARY reflects overall compliance.
            """;

    private SarSafetyPrompts() {}

    static String userPromptForEvent(String rulesetId, SarMissionEvent event) {
        return "Ruleset: "
                + rulesetId
                + "\nEvent type: "
                + event.type()
                + "\nMission: "
                + event.missionId()
                + "\nTick: "
                + event.tick()
                + "\nDrone: "
                + event.droneId()
                + "\nDetail: "
                + event.detail()
                + "\nProduce the JSON assessment.";
    }

    static String userPromptForSummary(String rulesetId, String missionId, SarJson.MissionSummary summary) {
        return "Ruleset: "
                + rulesetId
                + "\nMission: "
                + missionId
                + "\nEvent type: MISSION_SAFETY_SUMMARY"
                + "\nTicks run: "
                + summary.ticksRun()
                + "\nGeofence violation: "
                + summary.anyGeofenceViolation()
                + "\nEmergency land: "
                + summary.anyEmergencyLand()
                + "\nAll targets found: "
                + summary.allTargetsFound()
                + "\nFound target ids: "
                + summary.foundTargetIds()
                + "\nSuccess criteria met: "
                + summary.successCriteriaMet()
                + "\nProduce the JSON compliance assessment.";
    }
}
