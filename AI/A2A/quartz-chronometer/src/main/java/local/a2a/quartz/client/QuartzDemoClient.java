package local.a2a.quartz.client;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfig;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.UpdateEvent;

/**
 * Runs all three Clockwork examples against the Quartz Chronometer Agent:
 *
 * <ul>
 *   <li>Ex 1 — immediate {@link Message} (no streaming)
 *   <li>Ex 2 — async countdown via SSE
 *   <li>Ex 3 — input-required confirm, then SSE countdown on the same task
 * </ul>
 */
public final class QuartzDemoClient {

    public static void main(String[] args) throws Exception {
        String baseUrl = System.getenv().getOrDefault("QUARTZ_CHRONOMETER_URL", "http://localhost:8085");
        int countdownSeconds =
                Integer.parseInt(System.getenv().getOrDefault("QUARTZ_COUNTDOWN_SECONDS", "60"));
        int confirmSeconds =
                Integer.parseInt(System.getenv().getOrDefault("QUARTZ_CONFIRM_COUNTDOWN_SECONDS", "20"));

        AgentCard card = A2A.getAgentCard(baseUrl);
        System.out.println("\n=== Agent Card ===");
        System.out.println("name: " + card.name());
        System.out.println("streaming: " + card.capabilities().streaming());
        System.out.println("skills: " + card.skills().size());

        runSynchronousTimeExample(card);
        runAsyncCountdownExample(card, countdownSeconds);
        runInputRequiredCountdownExample(card, confirmSeconds);

        System.out.println("\n=== Quartz Chronometer demo complete ===");
    }

    private static void runSynchronousTimeExample(AgentCard card) throws Exception {
        System.out.println("\n=== Example 1: Synchronous Time (immediate Message) ===");
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> response = new AtomicReference<>("");

        try (Client client = client(card, false)) {
            client.sendMessage(
                    A2A.toUserMessage("What is the current time?"),
                    List.of((event, agentCard) -> {
                        if (event instanceof MessageEvent messageEvent) {
                            response.set(extractText(messageEvent.getMessage()));
                            done.countDown();
                        }
                    }),
                    error -> done.countDown(),
                    null);
            if (!done.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for synchronous time response");
            }
        }
        System.out.println("Response: " + response.get());
    }

    private static void runAsyncCountdownExample(AgentCard card, int seconds) throws Exception {
        System.out.println("\n=== Example 2: Async Countdown (SSE stream) ===");
        streamCountdownUntilComplete(
                card,
                A2A.toUserMessage("Count down " + seconds + " seconds"),
                seconds + 90L);
    }

    private static void runInputRequiredCountdownExample(AgentCard card, int seconds) throws Exception {
        System.out.println("\n=== Example 3: Input-Required Countdown (SSE stream) ===");
        AtomicReference<String> taskIdRef = new AtomicReference<>();
        CountDownLatch inputRequired = new CountDownLatch(1);

        try (Client client = client(card, true)) {
            client.sendMessage(
                    A2A.toUserMessage("Count down " + seconds + " seconds with confirm"),
                    List.of((event, agentCard) -> captureInputRequired(event, taskIdRef, inputRequired)),
                    error -> inputRequired.countDown(),
                    null);

            if (!inputRequired.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for input-required task");
            }

            String taskId = taskIdRef.get();
            System.out.println("Sending confirm on task " + taskId);

            Message confirm = Message.builder()
                    .role(Message.Role.ROLE_USER)
                    .taskId(taskId)
                    .parts(List.of(new TextPart("confirm")))
                    .build();

            CountDownLatch completed = new CountDownLatch(1);
            client.sendMessage(
                    MessageSendParams.builder().message(confirm).build(),
                    List.of((event, agentCard) -> handleStreamEvent(event, completed, "Example 3")),
                    error -> completed.countDown(),
                    null);

            if (!completed.await(seconds + 90L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for confirmed countdown completion");
            }
        }
    }

    private static void streamCountdownUntilComplete(AgentCard card, Message message, long timeoutSeconds)
            throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        try (Client client = client(card, true)) {
            client.sendMessage(
                    message,
                    List.of((event, agentCard) -> handleStreamEvent(event, completed, "Example 2")),
                    error -> completed.countDown(),
                    null);
            if (!completed.await(timeoutSeconds, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for streamed countdown completion");
            }
        }
    }

    private static void captureInputRequired(
            ClientEvent event, AtomicReference<String> taskIdRef, CountDownLatch inputRequired) {
        if (event instanceof TaskEvent taskEvent) {
            Task task = taskEvent.getTask();
            if (task != null) {
                noteTask(task, taskIdRef, inputRequired, "event=Task");
            }
            return;
        }
        if (event instanceof TaskUpdateEvent taskUpdateEvent) {
            UpdateEvent update = taskUpdateEvent.getUpdateEvent();
            if (update instanceof TaskStatusUpdateEvent statusUpdate) {
                printStatus("event=statusUpdate", statusUpdate.status().state(), extractText(statusUpdate.status().message()));
                if (statusUpdate.status().state() == TaskState.TASK_STATE_INPUT_REQUIRED) {
                    Task task = taskUpdateEvent.getTask();
                    if (task != null && task.id() != null) {
                        taskIdRef.compareAndSet(null, task.id());
                    }
                    inputRequired.countDown();
                }
            }
        }
    }

    private static void handleStreamEvent(ClientEvent event, CountDownLatch completed, String label) {
        if (event instanceof TaskEvent taskEvent) {
            Task task = taskEvent.getTask();
            if (task == null) {
                return;
            }
            printStatus(label + " Task", task.status().state(), extractText(task.status().message()));
            if (task.status().state().isFinal()) {
                printArtifacts(task);
                completed.countDown();
            }
            return;
        }
        if (event instanceof TaskUpdateEvent taskUpdateEvent) {
            UpdateEvent update = taskUpdateEvent.getUpdateEvent();
            if (update instanceof TaskStatusUpdateEvent statusUpdate) {
                printStatus(
                        label + " statusUpdate",
                        statusUpdate.status().state(),
                        extractText(statusUpdate.status().message()));
                if (statusUpdate.isFinal()) {
                    Task task = taskUpdateEvent.getTask();
                    if (task != null) {
                        printArtifacts(task);
                    }
                    completed.countDown();
                }
            } else if (update instanceof TaskArtifactUpdateEvent artifactUpdate) {
                System.out.println(label + " artifactUpdate | " + extractArtifactText(artifactUpdate.artifact()));
            }
        }
    }

    private static void noteTask(
            Task task, AtomicReference<String> taskIdRef, CountDownLatch latch, String label) {
        if (task.id() != null) {
            taskIdRef.compareAndSet(null, task.id());
            System.out.println("Task created: " + task.id());
        }
        printStatus(label, task.status().state(), extractText(task.status().message()));
        if (task.status().state() == TaskState.TASK_STATE_INPUT_REQUIRED) {
            latch.countDown();
        }
    }

    private static Client client(AgentCard card, boolean streaming) {
        ClientConfig config = ClientConfig.builder()
                .setStreaming(streaming)
                .setPolling(false)
                .setAcceptedOutputModes(List.of("text/plain"))
                .build();
        return Client.builder(card)
                .clientConfig(config)
                .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                .streamingErrorHandler(error -> { })
                .build();
    }

    private static void printStatus(String label, TaskState state, String text) {
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
        if (artifact == null || artifact.parts() == null) {
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

    private QuartzDemoClient() {}
}
