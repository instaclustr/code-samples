package com.example.a2a.kafka;

import com.example.a2a.kafka.model.*;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.io.FileReader;
import java.util.Properties;

/**
 * Consumer that subscribes to all A2A topics and prints received messages.
 * Use with Demo for end-to-end flow: start this first, then run Demo (or run Demo then this with auto.offset.reset=earliest).
 * <p>
 * Usage: java -cp ... com.example.a2a.kafka.DemoConsumer [path/to/producer.properties]
 * Press Ctrl+C to stop. Optional: run with "once" as second arg to consume one round then exit.
 */
public class DemoConsumer {

    public static void main(String[] args) throws Exception {
        String configPath = args.length > 0 ? args[0] : "producer.properties";
        boolean runOnce = args.length > 1 && "once".equalsIgnoreCase(args[1]);

        Properties base = new Properties();
        try (FileReader r = new FileReader(configPath)) {
            base.load(r);
        }
        // Remove producer-only keys, add consumer settings
        base.remove("key.serializer");
        base.remove("value.serializer");
        A2AKafkaConfig.baseConsumerProps("a2a-demo-consumer").forEach(base::putIfAbsent);

        A2AKafkaConfig config = new A2AKafkaConfig();

        A2AKafkaConsumer.A2AMessageHandler handler = new A2AKafkaConsumer.A2AMessageHandler() {
            @Override
            public void onAgentCard(AgentCard card) {
                System.out.println("--- [AGENT CARD] ---");
                System.out.println("  Name: " + card.getName());
                System.out.println("  Description: " + card.getDescription());
                System.out.println("  URL: " + card.getUrl());
                System.out.println("  Version: " + card.getVersion());
                if (card.getCapabilities() != null) {
                    System.out.println("  Streaming: " + card.getCapabilities().getStreaming());
                }
                System.out.println();
            }

            @Override
            public void onMessageSend(String partitionKey, MessageSendParams params) {
                System.out.println("--- [MESSAGE/SEND] key=" + partitionKey + " ---");
                if (params != null && params.getMessage() != null) {
                    Message msg = params.getMessage();
                    System.out.println("  Role: " + msg.getRole());
                    System.out.println("  MessageId: " + msg.getMessageId());
                    if (msg.getParts() != null) {
                        for (Part p : msg.getParts()) {
                            if (p instanceof TextPart tp) {
                                System.out.println("  Text: " + tp.getText());
                            } else if (p instanceof DataPart dp) {
                                System.out.println("  Data: " + dp.getData());
                            } else {
                                System.out.println("  Part: " + p.getKind());
                            }
                        }
                    }
                }
                System.out.println();
            }

            @Override
            public void onStatusUpdate(TaskStatusUpdateEvent event) {
                System.out.println("--- [STATUS UPDATE] taskId=" + event.getTaskId() + " ---");
                if (event.getStatus() != null) {
                    TaskStatus status = event.getStatus();
                    System.out.println("  State: " + (status.getState() != null ? status.getState().getValue() : "?"));
                    System.out.println("  Timestamp: " + status.getTimestamp());
                    if (status.getMessage() != null && status.getMessage().getParts() != null) {
                        for (Part p : status.getMessage().getParts()) {
                            if (p instanceof TextPart tp) {
                                System.out.println("  Status message: " + tp.getText());
                            }
                        }
                    }
                }
                System.out.println("  Final: " + event.getFinal());
                System.out.println();
            }

            @Override
            public void onArtifactUpdate(TaskArtifactUpdateEvent event) {
                System.out.println("--- [ARTIFACT UPDATE] taskId=" + event.getTaskId() + " ---");
                if (event.getArtifact() != null) {
                    Artifact a = event.getArtifact();
                    System.out.println("  ArtifactId: " + a.getArtifactId());
                    System.out.println("  Name: " + a.getName());
                }
                System.out.println();
            }

            @Override
            public void onTaskResult(Task task) {
                System.out.println("--- [TASK RESULT] id=" + task.getId() + " ---");
                if (task.getStatus() != null) {
                    System.out.println("  State: " + task.getStatus().getState());
                }
                System.out.println();
            }
        };

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(base);
        A2AKafkaConsumer a2aConsumer = new A2AKafkaConsumer(consumer, config, handler);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down...");
            a2aConsumer.stop();
        }));

        System.out.println("A2A Demo Consumer – subscribed to " + config.getRequestsTopic() + ", " + config.getUpdatesTopic() + ", " + config.getAgentCardsTopic());
        System.out.println("Run the Demo (producer) in another terminal to see messages. Ctrl+C to stop.");
        System.out.println();

        if (runOnce) {
            Thread runner = new Thread(() -> a2aConsumer.runAllTopicsLoop());
            runner.setDaemon(true);
            runner.start();
            // Run for 30 seconds to catch demo messages, then exit
            Thread.sleep(30_000);
            a2aConsumer.stop();
            Thread.sleep(1500);
            a2aConsumer.close();
            System.out.println("Done (run-once).");
        } else {
            try {
                a2aConsumer.runAllTopicsLoop();
            } finally {
                a2aConsumer.close();
            }
        }
    }
}
