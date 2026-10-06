package local.a2a.scenarios.dronesar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SimConfig(int tickHz, int gridCells, double cellSizeM, Boolean lowConfidenceBand) {

    public SimConfig(int tickHz, int gridCells, double cellSizeM) {
        this(tickHz, gridCells, cellSizeM, null);
    }

    public boolean lowConfidenceBandEnabled() {
        return Boolean.TRUE.equals(lowConfidenceBand);
    }
}
