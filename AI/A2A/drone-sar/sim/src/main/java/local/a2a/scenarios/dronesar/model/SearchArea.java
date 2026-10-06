package local.a2a.scenarios.dronesar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchArea(List<List<Double>> polygon) {}
