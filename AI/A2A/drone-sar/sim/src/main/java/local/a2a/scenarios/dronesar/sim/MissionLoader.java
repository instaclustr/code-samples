package local.a2a.scenarios.dronesar.sim;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Path;
import local.a2a.scenarios.dronesar.model.Mission;

public final class MissionLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MissionLoader() {}

    public static Mission load(Path path) throws IOException {
        Mission mission = MAPPER.readValue(path.toFile(), Mission.class);
        MissionValidator.validate(mission);
        return mission;
    }

    public static ObjectMapper mapper() {
        ObjectMapper m = new ObjectMapper();
        m.enable(SerializationFeature.INDENT_OUTPUT);
        return m;
    }
}
