package local.a2a.scenarios.dronesar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Mission(
        String missionId,
        String rulesetId,
        Base base,
        List<Target> targets,
        String searchPattern,
        List<DroneSpec> drones,
        SimConfig sim,
        GeofenceSpec geofence,
        RulesSpec rules,
        Boolean demoViolations) {

    public SimConfig simOrDefault() {
        return sim != null ? sim : new SimConfig(1, 128, 2);
    }

    public RulesSpec rulesOrDefault() {
        return rules != null ? rules : RulesSpec.defaults();
    }

    public GeofenceSpec geofenceOrDefault() {
        return geofence != null ? geofence : GeofenceSpec.empty();
    }

    public boolean demoViolationsEnabled() {
        return Boolean.TRUE.equals(demoViolations);
    }
}
