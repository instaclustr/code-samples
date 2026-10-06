package local.a2a.scenarios.dronesar.a2a.agent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskNotCancelableError;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;
import local.a2a.scenarios.dronesar.a2a.SarJson;
import local.a2a.scenarios.dronesar.a2a.SarMissionRequest;
import local.a2a.scenarios.dronesar.a2a.SarSimulationRunner;
import local.a2a.scenarios.dronesar.model.DroneTelemetry;
import local.a2a.scenarios.dronesar.sim.SimulationResult;

@ApplicationScoped
public class SarFlockAgentExecutorProducer {

    private static final String TELEMETRY_ARTIFACT = "sar-telemetry";
    private static final String SUMMARY_ARTIFACT = "sar-mission-summary";

    private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    @Produces
    public AgentExecutor agentExecutor() {
        return new SarFlockAgentExecutor(cancelFlags);
    }

    static final class SarFlockAgentExecutor implements AgentExecutor {

        private final Map<String, AtomicBoolean> cancelFlags;

        SarFlockAgentExecutor(Map<String, AtomicBoolean> cancelFlags) {
            this.cancelFlags = cancelFlags;
        }

        @Override
        public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
            String text = context.getUserInput();
            if (text == null || text.isBlank()) {
                emitter.sendMessage(agentMessage(
                        "Send a mission request, e.g.:\n"
                                + SarMissionRequest.PREFIX
                                + "\n../missions/test-mission-fast.json\nmaxTicks=500"));
                return;
            }

            SarMissionRequest request;
            try {
                request = SarMissionRequest.parse(text);
            } catch (IllegalArgumentException ex) {
                emitter.sendMessage(agentMessage("Invalid mission request: " + ex.getMessage()));
                return;
            }

            String taskId = emitter.getTaskId();
            AtomicBoolean canceled = new AtomicBoolean(false);
            cancelFlags.put(taskId, canceled);
            AtomicInteger artifactSeq = new AtomicInteger();

            try {
                emitter.startWork(agentMessage(
                        "SAR mission started: " + request.missionPath().getFileName()
                                + " (maxTicks=" + request.maxTicks() + ")"));

                SimulationResult result = SarSimulationRunner.run(request, new SarSimulationRunner.TickListener() {
                    @Override
                    public void onTick(long tick, DroneTelemetry telemetry) throws Exception {
                        if (canceled.get()) {
                            throw new SarSimulationRunner.RunCanceled();
                        }
                        int seq = artifactSeq.incrementAndGet();
                        emitter.addArtifact(
                                List.of(new TextPart(SarJson.telemetryArtifact(telemetry))),
                                TELEMETRY_ARTIFACT,
                                null,
                                null,
                                seq > 1,
                                false);
                        if (tick % 10 == 0) {
                            emitter.updateStatus(
                                    TaskState.TASK_STATE_WORKING,
                                    agentMessage("tick " + tick + " · drone " + telemetry.droneId()
                                            + " · battery " + String.format("%.1f", telemetry.batteryPct()) + "%"));
                        }
                    }

                    @Override
                    public void onComplete(SimulationResult summary) throws Exception {
                        emitter.addArtifact(
                                List.of(new TextPart(SarJson.summaryArtifact(summary))),
                                SUMMARY_ARTIFACT,
                                null,
                                null);
                    }
                });

                if (canceled.get()) {
                    emitter.cancel(agentMessage("SAR mission canceled."));
                    return;
                }

                String completion = "SAR mission complete — ticks=" + result.ticksRun()
                        + " targetsFound=" + result.allTargetsFound()
                        + " success=" + result.successCriteriaMet();
                emitter.complete(agentMessage(completion));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                emitter.cancel(agentMessage("SAR mission canceled."));
            } catch (Exception e) {
                emitter.fail(agentMessage("SAR mission failed: " + e.getMessage()));
            } finally {
                cancelFlags.remove(taskId);
            }
        }

        @Override
        public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
            Task task = context.getTask();
            if (task != null && task.status().state().isFinal()) {
                throw new TaskNotCancelableError();
            }
            AtomicBoolean flag = cancelFlags.get(emitter.getTaskId());
            if (flag != null) {
                flag.set(true);
            }
            emitter.cancel(agentMessage("SAR mission cancel requested."));
        }

        private static Message agentMessage(String text) {
            return Message.builder()
                    .role(Message.Role.ROLE_AGENT)
                    .parts(List.of(new TextPart(text)))
                    .build();
        }
    }
}
