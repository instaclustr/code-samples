package local.a2a.scenarios.dronesar.a2a;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import local.a2a.scenarios.dronesar.model.DroneTelemetry;
import local.a2a.scenarios.dronesar.model.Mission;
import local.a2a.scenarios.dronesar.sim.MissionLoader;
import local.a2a.scenarios.dronesar.sim.SimulationEngine;
import local.a2a.scenarios.dronesar.sim.SimulationResult;

public final class SarSimulationRunner {

    public interface TickListener {
        void onTick(long tick, DroneTelemetry telemetry) throws Exception;

        void onComplete(SimulationResult result) throws Exception;
    }

    private SarSimulationRunner() {}

    public static SimulationResult run(SarMissionRequest request, TickListener listener) throws Exception {
        Mission mission = MissionLoader.load(request.missionPath());
        SimulationEngine engine = new SimulationEngine(mission);
        long[] lastTick = {0};
        Consumer<DroneTelemetry> onTelemetry = telemetry -> {
            try {
                if (telemetry.tick() != lastTick[0]) {
                    if (lastTick[0] > 0 && request.realtime()) {
                        Thread.sleep(1_000);
                    }
                    lastTick[0] = telemetry.tick();
                }
                if (listener != null) {
                    listener.onTick(telemetry.tick(), telemetry);
                }
            } catch (RunCanceled e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RunCanceled();
            } catch (Exception e) {
                throw new IllegalStateException("Tick listener failed", e);
            }
        };
        try {
            SimulationResult result = engine.run(request.maxTicks(), listener == null ? null : onTelemetry);
            if (listener != null) {
                listener.onComplete(result);
            }
            return result;
        } catch (RunCanceled e) {
            throw new InterruptedException("SAR simulation canceled");
        }
    }

    public static final class RunCanceled extends RuntimeException {}
}
