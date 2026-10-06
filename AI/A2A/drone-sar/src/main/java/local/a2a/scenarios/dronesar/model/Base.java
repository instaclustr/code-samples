package local.a2a.scenarios.dronesar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Base(int cellX, int cellY, double altM, boolean mobile) {}
