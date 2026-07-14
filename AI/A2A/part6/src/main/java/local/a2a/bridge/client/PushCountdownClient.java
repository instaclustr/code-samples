package local.a2a.bridge.client;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import local.a2a.bridge.push.PushNotificationReceiver;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfig;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;

/**
 * Phase C client: disconnected pattern — register push webhook, start countdown, receive updates via POST.
 */
public final class PushCountdownClient {

    public static void main(String[] args) throws Exception {
        String agentBaseUrl = System.getenv().getOrDefault("A2A_BRIDGE_BASE_URL", "http://localhost:8081");
        int webhookPort = Integer.parseInt(System.getenv().getOrDefault("A2A_BRIDGE_WEBHOOK_PORT", "8082"));
        int seconds = Integer.parseInt(System.getenv().getOrDefault("A2A_BRIDGE_COUNTDOWN_SECONDS", "60"));
        String webhookUrl = System.getenv().getOrDefault(
                "A2A_BRIDGE_WEBHOOK_URL", "http://localhost:" + webhookPort + "/a2a/webhook");

        boolean ownReceiver = System.getenv("A2A_BRIDGE_WEBHOOK_URL") == null;
        PushNotificationReceiver receiver = null;
        if (ownReceiver) {
            receiver = new PushNotificationReceiver();
            receiver.start(webhookPort);
            webhookUrl = receiver.webhookUrl(webhookPort);
        }

        try {
            AgentCard card = A2A.getAgentCard(agentBaseUrl);
            System.out.println("\n=== Agent Card ===");
            System.out.println("name: " + card.name());
            System.out.println("pushNotifications: " + card.capabilities().pushNotifications());

            if (!card.capabilities().pushNotifications()) {
                throw new IllegalStateException("Agent card does not advertise pushNotifications");
            }

            ClientConfig clientConfig = ClientConfig.builder()
                    .setStreaming(false)
                    .setPolling(false)
                    .setAcceptedOutputModes(List.of("text/plain"))
                    .build();

            TaskPushNotificationConfig pushConfig = TaskPushNotificationConfig.builder()
                    .id("push-" + UUID.randomUUID())
                    .url(webhookUrl)
                    .build();

            MessageSendConfiguration sendConfiguration = MessageSendConfiguration.builder()
                    .acceptedOutputModes(List.of("text/plain"))
                    .returnImmediately(true)
                    .taskPushNotificationConfig(pushConfig)
                    .build();

            AtomicReference<String> taskIdRef = new AtomicReference<>();
            BiConsumer<ClientEvent, AgentCard> taskCapture = (event, agentCard) -> {
                if (event instanceof TaskEvent taskEvent) {
                    Task task = taskEvent.getTask();
                    if (task != null && task.id() != null) {
                        taskIdRef.compareAndSet(null, task.id());
                    }
                }
            };

            try (Client client = Client.builder(card)
                    .clientConfig(clientConfig)
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                    .addConsumer(taskCapture)
                    .streamingErrorHandler(error -> { })
                    .build()) {

                System.out.println("\n=== Phase C: Async Countdown (push webhook) ===");
                System.out.println("Webhook URL: " + webhookUrl);
                System.out.println("(No SSE/poll — updates arrive via server POST to webhook)");

                MessageSendParams params = MessageSendParams.builder()
                        .message(A2A.toUserMessage("Count down " + seconds + " seconds"))
                        .configuration(sendConfiguration)
                        .metadata(Map.of("bridge.phase", "C"))
                        .build();

                client.sendMessage(params, List.of(taskCapture), error -> { }, null);

                String taskId = taskIdRef.get();
                if (taskId == null && receiver != null && !receiver.payloads().isEmpty()) {
                    taskId = extractTaskId(receiver.payloads().get(0));
                }
                if (taskId != null) {
                    System.out.println("Task created: " + taskId);
                }
            }

            if (receiver != null) {
                if (!receiver.awaitCompletion(seconds + 30L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting for terminal push notification (received "
                                    + receiver.notificationCount() + ")");
                }
                System.out.println("\n=== Push summary ===");
                System.out.println("notifications received: " + receiver.notificationCount());
            } else {
                System.out.println("\nExternal webhook URL configured — check receiver logs for notifications.");
            }
        } finally {
            if (receiver != null) {
                receiver.stop();
            }
        }
    }

    private static String extractTaskId(String payload) {
        int marker = payload.indexOf("\"taskId\":\"");
        if (marker < 0) {
            return null;
        }
        int start = marker + "\"taskId\":\"".length();
        int end = payload.indexOf('"', start);
        return end > start ? payload.substring(start, end) : null;
    }

    private PushCountdownClient() {}
}
