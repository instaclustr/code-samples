package com.example.a2a.kafka.countdown;

import com.example.a2a.kafka.A2AKafkaConfig;
import com.example.a2a.kafka.A2AKafkaConsumer;
import com.example.a2a.kafka.model.Part;
import com.example.a2a.kafka.model.Task;
import com.example.a2a.kafka.model.TaskStatus;
import com.example.a2a.kafka.model.TaskStatusUpdateEvent;
import com.example.a2a.kafka.model.TextPart;
import com.example.a2a.kafka.util.KafkaPropertiesUtil;
import org.apache.kafka.clients.consumer.KafkaConsumer;

/**
 * Fan-out demo: separate consumer group on {@code a2a.updates} logs every lifecycle event (audit path).
 */
public final class AuditEventConsumer {

    public static void main(String[] args) throws Exception {
        String configPath = args.length > 0 ? args[0] : "producer.properties";
        A2AKafkaConfig config = new A2AKafkaConfig();

        try (KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(
                KafkaPropertiesUtil.consumerProps(configPath, "a2a-audit"))) {

            A2AKafkaConsumer a2aConsumer = new A2AKafkaConsumer(kafkaConsumer, config, new A2AKafkaConsumer.A2AMessageHandler() {
                @Override
                public void onStatusUpdate(TaskStatusUpdateEvent event) {
                    if (event == null) {
                        return;
                    }
                    TaskStatus status = event.getStatus();
                    String state = status != null && status.getState() != null ? status.getState().getValue() : "?";
                    System.out.println("[audit] status task=" + event.getTaskId()
                            + " state=" + state
                            + " final=" + event.getFinal()
                            + statusLine(status));
                }

                @Override
                public void onArtifactUpdate(com.example.a2a.kafka.model.TaskArtifactUpdateEvent event) {
                    if (event != null) {
                        System.out.println("[audit] artifact task=" + event.getTaskId()
                                + " id=" + (event.getArtifact() != null ? event.getArtifact().getArtifactId() : "?"));
                    }
                }

                @Override
                public void onTaskResult(Task task) {
                    if (task != null) {
                        String state = task.getStatus() != null && task.getStatus().getState() != null
                                ? task.getStatus().getState().getValue() : "?";
                        System.out.println("[audit] task-result id=" + task.getId() + " state=" + state);
                    }
                }
            });

            Runtime.getRuntime().addShutdownHook(new Thread(a2aConsumer::stop));

            System.out.println("[audit] consuming " + config.getUpdatesTopic() + " (group=a2a-audit)");
            System.out.println("[audit] Ctrl+C to stop.");
            a2aConsumer.runUpdatesLoop();
        }
    }

    private static String statusLine(TaskStatus status) {
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
        return sb.isEmpty() ? "" : " — " + sb;
    }
}
