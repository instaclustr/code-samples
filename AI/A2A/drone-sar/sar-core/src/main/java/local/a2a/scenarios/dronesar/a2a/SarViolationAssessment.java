package local.a2a.scenarios.dronesar.a2a;

/** LLM/template safety assessment published to Kafka. */
public record SarViolationAssessment(
        String missionId,
        String severity,
        String ruleId,
        String summary,
        String recommendedAction,
        String sourceEventType,
        long tick,
        String droneId) {}
