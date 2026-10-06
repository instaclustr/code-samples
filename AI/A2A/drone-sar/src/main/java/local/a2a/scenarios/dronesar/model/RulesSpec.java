package local.a2a.scenarios.dronesar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RulesSpec(
        double minAglM,
        double maxAglM,
        double cruiseSpeedMs,
        double rtbBatteryPct,
        double batteryDrainSearchPctPerTick,
        double batteryDrainRtbPctPerTick) {

    public static RulesSpec defaults() {
        return new RulesSpec(15, 120, 8, 30, 0.12, 0.18);
    }
}
