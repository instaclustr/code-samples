package local.a2a.scenarios.dronesar.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DroneMode {
    SEARCH,
    RTB,
    LANDED,
    HOLD,
    EMERGENCY_LAND;

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }
}
