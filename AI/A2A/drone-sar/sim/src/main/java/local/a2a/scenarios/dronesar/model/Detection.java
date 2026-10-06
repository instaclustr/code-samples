package local.a2a.scenarios.dronesar.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Detection(String targetId, String targetType, double confidence, List<Double> bboxNorm) {}
