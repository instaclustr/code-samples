package local.a2a.scenarios.dronesar.a2a;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SarSimulationRunnerTest {

    @Test
    void runsFastMissionViaCoreWrapper() throws Exception {
        Path missionPath = Path.of("../../missions/test-mission-fast.json").toAbsolutePath().normalize();
        SarMissionRequest request = new SarMissionRequest(missionPath, 500, false);
        AtomicInteger telemetryEvents = new AtomicInteger();
        SimulationResultHolder holder = new SimulationResultHolder();
        SarSimulationRunner.run(request, new SarSimulationRunner.TickListener() {
            @Override
            public void onTick(long tick, local.a2a.scenarios.dronesar.model.DroneTelemetry telemetry) {
                telemetryEvents.incrementAndGet();
            }

            @Override
            public void onComplete(local.a2a.scenarios.dronesar.sim.SimulationResult result) {
                holder.result = result;
            }
        });
        assertTrue(telemetryEvents.get() > 0, "should stream telemetry");
        assertTrue(holder.result.allTargetsFound(), "fast mission should find target");
        assertTrue(holder.result.successCriteriaMet());
    }

    private static final class SimulationResultHolder {
        local.a2a.scenarios.dronesar.sim.SimulationResult result;
    }
}
