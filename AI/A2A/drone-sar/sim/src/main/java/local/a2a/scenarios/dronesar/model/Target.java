package local.a2a.scenarios.dronesar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Target(
        String id,
        String type,
        int priority,
        SearchArea searchArea,
        Cell lastKnownCell) {}
