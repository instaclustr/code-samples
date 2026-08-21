package com.example.a2a.kafka.countdown;

import com.example.a2a.kafka.A2AKafkaConfig;
import com.example.a2a.kafka.A2AKafkaConsumer;
import com.example.a2a.kafka.A2AKafkaProducer;
import com.example.a2a.kafka.model.Artifact;
import com.example.a2a.kafka.model.MessageSendParams;
import com.example.a2a.kafka.model.Task;
import com.example.a2a.kafka.model.TaskArtifactUpdateEvent;
import com.example.a2a.kafka.model.TaskState;
import com.example.a2a.kafka.model.TaskStatus;
import com.example.a2a.kafka.model.TaskStatusUpdateEvent;
import com.example.a2a.kafka.model.TextPart;
import com.example.a2a.kafka.util.KafkaPropertiesUtil;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Countdown agent: consumes {@code message/send} from {@code a2a.requests}, publishes lifecycle
 * events to {@code a2a.updates}. Same 10-second tick story as the Part 6 bridge agent.
 */
public final class CountdownKafkaAgent {

    private static final Logger log = LoggerFactory.getLogger(CountdownKafkaAgent.class);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());
    private static final int TICK_SECONDS = 10;

    private final A2AKafkaProducer producer;
    private final ExecutorService workers = Executors.newCachedThreadPool();

    public CountdownKafkaAgent(A2AKafkaProducer producer) {
        this.producer = producer;
    }

    public void onMessageSend(String taskId, MessageSendParams params) {
        String userText = CountdownParser.extractUserText(params);
        int seconds = CountdownParser.parseCountdownSeconds(userText);
        if (seconds <= 0) {
            publishStatus(taskId, TaskState.REJECTED,
                    "Send a countdown request, for example: Count down 60 seconds", true);
            return;
        }
        workers.submit(() -> runCountdown(taskId, seconds));
    }

    private void runCountdown(String taskId, int seconds) {
        Thread.currentThread().setName("countdown-" + taskId);
        try {
            int remaining = seconds;
            publishStatus(taskId, TaskState.WORKING,
                    "Countdown started: " + remaining + "s remaining.", false);

            while (remaining > 0) {
                Thread.sleep(TICK_SECONDS * 1000L);
                remaining -= TICK_SECONDS;
                if (remaining > 0) {
                    publishStatus(taskId, TaskState.WORKING,
                            "Countdown update: " + remaining + "s remaining.", false);
                }
            }

            String completion = "Countdown completed at " + TIME_FORMAT.format(Instant.now()) + ".";
            publishArtifact(taskId, completion);
            publishStatus(taskId, TaskState.COMPLETED, completion, true);
            publishTaskResult(taskId, completion);
            log.info("Task {} completed", taskId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            publishStatus(taskId, TaskState.FAILED, "Countdown interrupted.", true);
        }
    }

    private void publishStatus(String taskId, TaskState state, String text, boolean terminal) {
        TaskStatus status = new TaskStatus();
        status.setState(state);
        status.setMessage(CountdownParser.agentMessage(text));
        status.setTimestamp(Instant.now().toString());

        TaskStatusUpdateEvent event = new TaskStatusUpdateEvent();
        event.setTaskId(taskId);
        event.setContextId(taskId);
        event.setStatus(status);
        event.setFinal(terminal);
        producer.sendStatusUpdate(event);
        producer.flush();
        System.out.println("[agent] task=" + taskId + " state=" + state.getValue()
                + (terminal ? " (final)" : "") + " — " + text);
    }

    private void publishArtifact(String taskId, String completionText) {
        Artifact artifact = new Artifact();
        artifact.setArtifactId("countdown-result");
        artifact.setName("countdown-result");
        artifact.setParts(List.of(new TextPart(completionText)));

        TaskArtifactUpdateEvent event = new TaskArtifactUpdateEvent();
        event.setTaskId(taskId);
        event.setContextId(taskId);
        event.setArtifact(artifact);
        producer.sendArtifactUpdate(event);
        producer.flush();
        System.out.println("[agent] task=" + taskId + " artifact published");
    }

    private void publishTaskResult(String taskId, String completionText) {
        TaskStatus status = new TaskStatus();
        status.setState(TaskState.COMPLETED);
        status.setMessage(CountdownParser.agentMessage(completionText));
        status.setTimestamp(Instant.now().toString());

        Artifact artifact = new Artifact();
        artifact.setArtifactId("countdown-result");
        artifact.setName("countdown-result");
        artifact.setParts(List.of(new TextPart(completionText)));

        Task task = new Task();
        task.setId(taskId);
        task.setContextId(taskId);
        task.setStatus(status);
        task.setArtifacts(List.of(artifact));
        producer.sendTaskResult(task, UUID.randomUUID().toString());
        producer.flush();
    }

    public void shutdown() {
        workers.shutdownNow();
    }

    public static void main(String[] args) throws Exception {
        String configPath = args.length > 0 ? args[0] : "producer.properties";
        A2AKafkaConfig config = new A2AKafkaConfig();

        try (KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(KafkaPropertiesUtil.producerProps(configPath));
             KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(
                     KafkaPropertiesUtil.consumerProps(configPath, "a2a-countdown-agent"))) {

            A2AKafkaProducer a2aProducer = new A2AKafkaProducer(kafkaProducer, config);
            CountdownKafkaAgent agent = new CountdownKafkaAgent(a2aProducer);

            A2AKafkaConsumer a2aConsumer = new A2AKafkaConsumer(kafkaConsumer, config, new A2AKafkaConsumer.A2AMessageHandler() {
                @Override
                public void onMessageSend(String partitionKey, MessageSendParams params) {
                    String taskId = partitionKey != null && !partitionKey.isBlank()
                            ? partitionKey
                            : "task-" + UUID.randomUUID();
                    System.out.println("[agent] message/send taskId=" + taskId
                            + " text=" + CountdownParser.extractUserText(params));
                    agent.onMessageSend(taskId, params);
                }
            });

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down agent...");
                a2aConsumer.stop();
                agent.shutdown();
            }));

            System.out.println("Countdown Kafka Agent — consuming " + config.getRequestsTopic()
                    + ", publishing " + config.getUpdatesTopic());
            System.out.println("Ctrl+C to stop.");
            a2aConsumer.runRequestsLoop();
        }
    }
}
