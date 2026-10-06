package local.a2a.scenarios.dronesar.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Significant SAR mission event for narrator input (design §18.3). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SarMissionEvent(
        String type,
        long tick,
        String missionId,
        String targetId,
        String droneId,
        String detail,
        String fromTargetId) {

    public static SarMissionEvent of(
            String type, long tick, String missionId, String targetId, String droneId, String detail) {
        return new SarMissionEvent(type, tick, missionId, targetId, droneId, detail, null);
    }

    public static SarMissionEvent transfer(
            long tick, String missionId, String targetId, String droneId, String fromTargetId) {
        return new SarMissionEvent(
                "DRONE_TRANSFER",
                tick,
                missionId,
                targetId,
                droneId,
                "reassigned from " + fromTargetId,
                fromTargetId);
    }
}
