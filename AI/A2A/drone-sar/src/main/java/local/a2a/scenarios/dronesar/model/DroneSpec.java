package local.a2a.scenarios.dronesar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DroneSpec(String id, List<String> sensors, Double initialBatteryPct) {

    public DroneSpec(String id, List<String> sensors) {
        this(id, sensors, null);
    }
}
