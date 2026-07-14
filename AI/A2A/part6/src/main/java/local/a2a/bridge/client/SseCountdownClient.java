package local.a2a.bridge.client;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfig;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.UpdateEvent;

/**
 * Phase B client: official a2a-java SDK with SSE streaming (no {@code GetTask} poll loop).
 */
public final class SseCountdownClient {

    public static void main(String[] args) throws Exception {
        String baseUrl = System.getenv().getOrDefault("A2A_BRIDGE_BASE_URL", "http://localhost:8081");
        int seconds = Integer.parseInt(System.getenv().getOrDefault("A2A_BRIDGE_COUNTDOWN_SECONDS", "60"));

        AgentCard card = A2A.getAgentCard(baseUrl);
        System.out.println("\n=== Agent Card ===");
        System.out.println("name: " + card.name());
        System.out.println("streaming: " + card.capabilities().streaming());

        ClientConfig clientConfig = ClientConfig.builder()
                .setStreaming(true)
                .setPolling(false)
                .setAcceptedOutputModes(List.of("text/plain"))
                .build();

        AtomicReference<String> taskIdRef = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(1);
        Consumer<Throwable> streamingErrorHandler = error -> {
            if (error == null || error instanceof CancellationException) {
                return;
            }
            System.err.println("Streaming error: " + error.getMessage());
            if (completed.compareAndSet(false, true)) {
                done.countDown();
            }
        };

        try (Client client = Client.builder(card)
                .clientConfig(clientConfig)
                .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                .addConsumer((event, agentCard) ->
                        handleEvent(event, taskIdRef, completed, done))
                .streamingErrorHandler(streamingErrorHandler)
                .build()) {

            System.out.println("\n=== Phase B: Async Countdown (SSE stream) ===");
            client.sendMessage(A2A.toUserMessage("Count down " + seconds + " seconds"));

            if (!done.await(seconds + 60L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for streamed countdown completion");
            }
        }
    }

    private static void handleEvent(
            ClientEvent event,
            AtomicReference<String> taskIdRef,
            AtomicBoolean completed,
            CountDownLatch done) {
        if (event instanceof TaskEvent taskEvent) {
            Task task = taskEvent.getTask();
            if (task == null) {
                return;
            }
            if (task.id() != null) {
                taskIdRef.compareAndSet(null, task.id());
                System.out.println("Task created: " + task.id());
            }
            printStatus("event=Task", task.status().state().name(), extractText(task.status().message()));
            if (task.status().state().isFinal() && completed.compareAndSet(false, true)) {
                printArtifacts(task);
                done.countDown();
            }
            return;
        }

        if (event instanceof TaskUpdateEvent taskUpdateEvent) {
            UpdateEvent update = taskUpdateEvent.getUpdateEvent();
            if (update instanceof TaskStatusUpdateEvent statusUpdate) {
                printStatus(
                        "event=statusUpdate",
                        statusUpdate.status().state().name(),
                        extractText(statusUpdate.status().message()));
                if (statusUpdate.isFinal() && completed.compareAndSet(false, true)) {
                    Task task = taskUpdateEvent.getTask();
                    if (task != null) {
                        printArtifacts(task);
                    }
                    done.countDown();
                }
            } else if (update instanceof TaskArtifactUpdateEvent artifactUpdate) {
                System.out.println("event=artifactUpdate | " + extractArtifactText(artifactUpdate.artifact()));
            }
        }
    }

    private static void printStatus(String label, String state, String text) {
        System.out.println(label + " | state=" + state + " | " + text);
    }

    private static void printArtifacts(Task task) {
        if (task.artifacts() == null || task.artifacts().isEmpty()) {
            return;
        }
        System.out.println("Final artifact: " + extractArtifactText(task.artifacts().get(0)));
    }

    private static String extractText(Message message) {
        if (message == null || message.parts() == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (Part<?> part : message.parts()) {
            if (part instanceof TextPart textPart) {
                text.append(textPart.text());
            }
        }
        return text.toString();
    }

    private static String extractArtifactText(Artifact artifact) {
        if (artifact.parts() == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (Part<?> part : artifact.parts()) {
            if (part instanceof TextPart textPart) {
                text.append(textPart.text());
            }
        }
        return text.toString();
    }

    private SseCountdownClient() {}
}
