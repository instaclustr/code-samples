package local.a2a.quartz;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;

/** Runs the Clockwork async countdown loop via {@link AgentEmitter} + SSE. */
public final class QuartzCountdownRunner {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    private QuartzCountdownRunner() {}

    public static void run(AgentEmitter emitter, int seconds, AtomicBoolean canceled) throws InterruptedException {
        int remaining = seconds;
        emitter.startWork(agentMessage("Countdown started: " + remaining + "s remaining."));

        while (remaining > 0 && !canceled.get()) {
            Thread.sleep(10_000);
            if (canceled.get()) {
                break;
            }
            remaining -= 10;
            if (remaining > 0) {
                emitter.updateStatus(
                        TaskState.TASK_STATE_WORKING,
                        agentMessage("Countdown update: " + remaining + "s remaining."));
            }
        }

        if (canceled.get()) {
            emitter.cancel(agentMessage("Countdown canceled."));
            return;
        }

        String completion = "Countdown completed at " + TIME_FORMAT.format(Instant.now()) + ".";
        emitter.addArtifact(List.of(new TextPart(completion)), "countdown-result", null, null);
        emitter.complete(agentMessage(completion));
    }

    static Message agentMessage(String text) {
        return Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart(text)))
                .build();
    }
}
