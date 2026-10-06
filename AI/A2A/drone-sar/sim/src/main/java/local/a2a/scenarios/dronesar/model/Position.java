package local.a2a.scenarios.dronesar.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Position(double x, double y, double altAglM) {}
