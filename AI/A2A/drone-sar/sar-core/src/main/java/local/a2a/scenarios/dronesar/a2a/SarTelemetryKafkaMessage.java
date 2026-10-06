package local.a2a.scenarios.dronesar.a2a;

/** Kafka envelope for significant mission / safety events. */
public record SarTelemetryKafkaMessage(
        String rulesetId,
        SarMissionEvent event,
        SarJson.MissionSummary summary) {

    public static SarTelemetryKafkaMessage ofEvent(String rulesetId, SarMissionEvent event) {
        return new SarTelemetryKafkaMessage(rulesetId, event, null);
    }

    public static SarTelemetryKafkaMessage ofSummary(String rulesetId, SarJson.MissionSummary summary) {
        SarMissionEvent marker = SarMissionEvent.of(
                "MISSION_SAFETY_SUMMARY", summary.ticksRun(), "unknown", null, null, null);
        return new SarTelemetryKafkaMessage(rulesetId, marker, summary);
    }
}
