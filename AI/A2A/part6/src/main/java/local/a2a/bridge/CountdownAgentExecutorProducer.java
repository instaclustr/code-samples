package local.a2a.bridge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskNotCancelableError;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;

@ApplicationScoped
public class CountdownAgentExecutorProducer {

    private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    @Produces
    public AgentExecutor agentExecutor() {
        return new CountdownAgentExecutor(cancelFlags);
    }

    static final class CountdownAgentExecutor implements AgentExecutor {

        private static final DateTimeFormatter TIME_FORMAT =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

        private final Map<String, AtomicBoolean> cancelFlags;

        CountdownAgentExecutor(Map<String, AtomicBoolean> cancelFlags) {
            this.cancelFlags = cancelFlags;
        }

        @Override
        public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
            String text = context.getUserInput();
            int seconds = parseCountdownSeconds(text == null ? "" : text.toLowerCase());
            if (seconds <= 0) {
                emitter.sendMessage(
                        "Send a countdown request, for example: Count down 60 seconds");
                return;
            }

            String taskId = emitter.getTaskId();
            AtomicBoolean canceled = new AtomicBoolean(false);
            cancelFlags.put(taskId, canceled);

            try {
                int remaining = seconds;
                emitter.startWork(agentStatusMessage("Countdown started: " + remaining + "s remaining."));

                while (remaining > 0 && !canceled.get()) {
                    Thread.sleep(10_000);
                    if (canceled.get()) {
                        break;
                    }
                    remaining -= 10;
                    if (remaining > 0) {
                        emitter.updateStatus(
                                TaskState.TASK_STATE_WORKING,
                                agentStatusMessage("Countdown update: " + remaining + "s remaining."));
                    }
                }

                if (canceled.get()) {
                    emitter.cancel(agentStatusMessage("Countdown canceled."));
                    return;
                }

                String completion = "Countdown completed at " + TIME_FORMAT.format(Instant.now()) + ".";
                emitter.addArtifact(List.of(new TextPart(completion)), "countdown-result", null, null);
                emitter.complete(agentStatusMessage(completion));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                emitter.fail(agentStatusMessage("Countdown interrupted."));
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
            emitter.cancel(agentStatusMessage("Countdown canceled."));
        }

        private static org.a2aproject.sdk.spec.Message agentStatusMessage(String text) {
            return org.a2aproject.sdk.spec.Message.builder()
                    .role(org.a2aproject.sdk.spec.Message.Role.ROLE_AGENT)
                    .parts(List.of(new TextPart(text)))
                    .build();
        }

        static int parseCountdownSeconds(String normalized) {
            String[] tokens = normalized.replaceAll("[^a-z0-9 ]", " ").split("\\s+");
            for (int i = 0; i < tokens.length; i++) {
                if (tokens[i].matches("\\d+")) {
                    int value = Integer.parseInt(tokens[i]);
                    if (i + 1 < tokens.length && tokens[i + 1].startsWith("min")) {
                        return value * 60;
                    }
                    return value;
                }
            }
            return 0;
        }
    }
}
