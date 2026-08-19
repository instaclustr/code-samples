package local.a2a.quartz;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

@ApplicationScoped
public class QuartzAgentExecutorProducer {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    private static final String HELP =
            "Try 'what time is it?', 'count down 60 seconds', or 'count down 20 seconds with confirm'.";

    private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    @Inject
    PendingConfirmRegistry pendingConfirmRegistry;

    @Produces
    public AgentExecutor agentExecutor() {
        return new QuartzAgentExecutor(cancelFlags, pendingConfirmRegistry);
    }

    final class QuartzAgentExecutor implements AgentExecutor {

        private final Map<String, AtomicBoolean> cancelFlags;
        private final PendingConfirmRegistry pendingConfirmRegistry;

        QuartzAgentExecutor(
                Map<String, AtomicBoolean> cancelFlags, PendingConfirmRegistry pendingConfirmRegistry) {
            this.cancelFlags = cancelFlags;
            this.pendingConfirmRegistry = pendingConfirmRegistry;
        }

        @Override
        public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
            String text = context.getUserInput();
            String normalized = QuartzMessageParser.normalize(text);
            Task existing = context.getTask();

            if (existing != null && existing.status().state() == TaskState.TASK_STATE_INPUT_REQUIRED) {
                handleConfirmContinuation(normalized, emitter);
                return;
            }

            if (QuartzMessageParser.isTimeQuery(normalized)) {
                emitter.sendMessage("The current time is " + TIME_FORMAT.format(Instant.now()) + ".");
                return;
            }

            int seconds = QuartzMessageParser.parseCountdownSeconds(normalized);
            if (seconds <= 0) {
                emitter.sendMessage(HELP);
                return;
            }

            if (QuartzMessageParser.requiresConfirmation(normalized)) {
                pendingConfirmRegistry.put(emitter.getTaskId(), seconds);
                emitter.requiresInput(
                        QuartzCountdownRunner.agentMessage(
                                "Please confirm countdown start for " + seconds + " seconds."));
                return;
            }

            runCountdown(emitter, seconds);
        }

        private void handleConfirmContinuation(String normalized, AgentEmitter emitter) throws A2AError {
            if (!normalized.contains("confirm")) {
                emitter.requiresInput(
                        QuartzCountdownRunner.agentMessage("Please reply confirm to start the countdown."));
                return;
            }
            Integer seconds = pendingConfirmRegistry.take(emitter.getTaskId());
            if (seconds == null || seconds <= 0) {
                emitter.fail(QuartzCountdownRunner.agentMessage("Countdown duration missing for this task."));
                return;
            }
            runCountdown(emitter, seconds);
        }

        private void runCountdown(AgentEmitter emitter, int seconds) throws A2AError {
            String taskId = emitter.getTaskId();
            AtomicBoolean canceled = new AtomicBoolean(false);
            cancelFlags.put(taskId, canceled);
            try {
                QuartzCountdownRunner.run(emitter, seconds, canceled);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                emitter.fail(QuartzCountdownRunner.agentMessage("Countdown interrupted."));
            } finally {
                cancelFlags.remove(taskId);
                pendingConfirmRegistry.remove(taskId);
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
            pendingConfirmRegistry.remove(emitter.getTaskId());
            emitter.cancel(QuartzCountdownRunner.agentMessage("Countdown canceled."));
        }
    }
}
