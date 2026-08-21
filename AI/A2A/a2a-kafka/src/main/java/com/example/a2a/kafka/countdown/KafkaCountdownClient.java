package com.example.a2a.kafka.countdown;

import com.example.a2a.kafka.A2AKafkaConfig;
import com.example.a2a.kafka.A2AKafkaConsumer;
import com.example.a2a.kafka.A2AKafkaProducer;
import com.example.a2a.kafka.model.Message;
import com.example.a2a.kafka.model.MessageSendParams;
import com.example.a2a.kafka.model.Part;
import com.example.a2a.kafka.model.Task;
import com.example.a2a.kafka.model.TaskArtifactUpdateEvent;
import com.example.a2a.kafka.model.TaskState;
import com.example.a2a.kafka.model.TaskStatus;
import com.example.a2a.kafka.model.TaskStatusUpdateEvent;
import com.example.a2a.kafka.model.TextPart;
import com.example.a2a.kafka.util.KafkaPropertiesUtil;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client: produce {@code message/send} to {@code a2a.requests}, consume matching events from
 * {@code a2a.updates} until the task reaches a terminal state.
 */
public final class KafkaCountdownClient {

    public static void main(String[] args) throws Exception {
        String configPath = args.length > 0 ? args[0] : "producer.properties";
        int seconds = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        String requestText = "Count down " + seconds + " seconds";

        A2AKafkaConfig config = new A2AKafkaConfig();
        String taskId = "task-" + UUID.randomUUID();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<TaskState> terminalState = new AtomicReference<>();

        try (KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(KafkaPropertiesUtil.producerProps(configPath));
             KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(
                     KafkaPropertiesUtil.consumerProps(configPath, "a2a-countdown-client"))) {

            A2AKafkaProducer a2aProducer = new A2AKafkaProducer(kafkaProducer, config);

            A2AKafkaConsumer a2aConsumer = new A2AKafkaConsumer(kafkaConsumer, config, new A2AKafkaConsumer.A2AMessageHandler() {
                @Override
                public void onStatusUpdate(TaskStatusUpdateEvent event) {
                    if (event == null || !taskId.equals(event.getTaskId())) {
                        return;
                    }
                    TaskStatus status = event.getStatus();
                    TaskState state = status != null ? status.getState() : null;
                    String text = statusText(status);
                    System.out.println("[client] status-update state="
                            + (state != null ? state.getValue() : "?")
                            + " final=" + event.getFinal()
                            + (text.isEmpty() ? "" : " — " + text));
                    if (Boolean.TRUE.equals(event.getFinal()) && state != null && state.isTerminal()) {
                        terminalState.set(state);
                        done.countDown();
                    }
                }

                @Override
                public void onArtifactUpdate(TaskArtifactUpdateEvent event) {
                    if (event != null && taskId.equals(event.getTaskId())) {
                        System.out.println("[client] artifact-update id="
                                + (event.getArtifact() != null ? event.getArtifact().getArtifactId() : "?"));
                    }
                }

                @Override
                public void onTaskResult(Task task) {
                    if (task != null && taskId.equals(task.getId())) {
                        System.out.println("[client] task result state="
                                + (task.getStatus() != null && task.getStatus().getState() != null
                                ? task.getStatus().getState().getValue() : "?"));
                        if (task.getStatus() != null && task.getStatus().getState() != null
                                && task.getStatus().getState().isTerminal()) {
                            terminalState.compareAndSet(null, task.getStatus().getState());
                            done.countDown();
                        }
                    }
                }
            });

            Thread consumerThread = new Thread(() -> a2aConsumer.runUpdatesLoop(), "client-updates");
            consumerThread.setDaemon(true);
            consumerThread.start();

            // Allow consumer to join group before producing (avoids missing fast tasks on local Kafka).
            Thread.sleep(2000);

            Message message = new Message();
            message.setRole("user");
            message.setMessageId(UUID.randomUUID().toString());
            message.setParts(List.of(new TextPart(requestText)));

            MessageSendParams params = new MessageSendParams();
            params.setMessage(message);

            System.out.println("[client] sending message/send taskId=" + taskId + " text=\"" + requestText + "\"");
            a2aProducer.sendMessage(params, taskId);
            a2aProducer.flush();

            long timeoutSeconds = Math.max(seconds + 30L, 60L);
            boolean finished = done.await(timeoutSeconds, TimeUnit.SECONDS);
            a2aConsumer.stop();
            consumerThread.join(3000);

            if (!finished) {
                System.err.println("[client] timed out after " + timeoutSeconds + "s waiting for terminal state");
                System.exit(1);
            }
            System.out.println("[client] done — terminal state: "
                    + (terminalState.get() != null ? terminalState.get().getValue() : "unknown"));
        }
    }

    private static String statusText(TaskStatus status) {
        if (status == null || status.getMessage() == null || status.getMessage().getParts() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Part part : status.getMessage().getParts()) {
            if (part instanceof TextPart textPart && textPart.getText() != null) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(textPart.getText());
            }
        }
        return sb.toString();
    }
}
