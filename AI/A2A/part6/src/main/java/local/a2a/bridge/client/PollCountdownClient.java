package local.a2a.bridge.client;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfig;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;

/**
 * Phase A client: official a2a-java SDK with {@code GetTask} polling (parity with Clockwork Ex 2).
 */
public final class PollCountdownClient {

    public static void main(String[] args) throws Exception {
        String baseUrl = System.getenv().getOrDefault("A2A_BRIDGE_BASE_URL", "http://localhost:8081");
        int seconds = Integer.parseInt(System.getenv().getOrDefault("A2A_BRIDGE_COUNTDOWN_SECONDS", "60"));

        AgentCard card = A2A.getAgentCard(baseUrl);
        System.out.println("\n=== Agent Card ===");
        System.out.println("name: " + card.name());
        System.out.println("skills: " + card.skills().size());

        ClientConfig clientConfig = ClientConfig.builder()
                .setStreaming(false)
                .setPolling(true)
                .setAcceptedOutputModes(List.of("text/plain"))
                .build();

        AtomicReference<String> taskIdRef = new AtomicReference<>();
        CountDownLatch taskCreated = new CountDownLatch(1);

        try (Client client = Client.builder(card)
                .clientConfig(clientConfig)
                .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                .addConsumer((event, agentCard) -> {
                    if (event instanceof TaskEvent taskEvent) {
                        Task task = taskEvent.getTask();
                        if (task != null && task.id() != null) {
                            taskIdRef.compareAndSet(null, task.id());
                            taskCreated.countDown();
                        }
                    }
                })
                .streamingErrorHandler(error -> { })
                .build()) {

            System.out.println("\n=== Phase A: Async Countdown (poll via GetTask) ===");
            client.sendMessage(A2A.toUserMessage("Count down " + seconds + " seconds"));

            if (!taskCreated.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for task from SendMessage");
            }

            String taskId = taskIdRef.get();
            System.out.println("Task created: " + taskId);

            while (true) {
                Thread.sleep(5000);
                Task task = client.getTask(new TaskQueryParams(taskId));
                TaskState state = task.status().state();
                String statusText = extractText(task.status().message());
                System.out.println("state=" + state + " | " + statusText);

                if (state.isFinal()) {
                    List<Artifact> artifacts = task.artifacts() == null
                            ? Collections.emptyList()
                            : task.artifacts();
                    if (!artifacts.isEmpty()) {
                        System.out.println("Final artifact: " + extractArtifactText(artifacts.get(0)));
                    }
                    break;
                }
            }
        }
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

    private PollCountdownClient() {}
}
