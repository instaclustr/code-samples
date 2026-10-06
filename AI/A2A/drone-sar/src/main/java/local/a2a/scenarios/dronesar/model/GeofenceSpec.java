package local.a2a.scenarios.dronesar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeofenceSpec(List<SearchArea> noFlyZones) {
    public static GeofenceSpec empty() {
        return new GeofenceSpec(Collections.emptyList());
    }
}
