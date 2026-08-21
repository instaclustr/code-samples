package com.example.a2a.kafka;

import com.example.a2a.kafka.model.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.io.FileReader;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Demo: publish an Agent Card and a message/send request, then (optional) consume from updates.
 * Usage: ensure Kafka is running and topics a2a.requests, a2a.updates, a2a.agent-cards exist (or auto-create).
 *   java -cp ... com.example.a2a.kafka.Demo [path/to/producer.properties]
 */
public class Demo {

    public static void main(String[] args) throws Exception {
        String configPath = args.length > 0 ? args[0] : "producer.properties";
        Properties base = new Properties();
        try (FileReader r = new FileReader(configPath)) {
            base.load(r);
        }
        A2AKafkaConfig.baseProducerProps().forEach(base::putIfAbsent);

        A2AKafkaConfig config = new A2AKafkaConfig();
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(base)) {
            A2AKafkaProducer a2aProducer = new A2AKafkaProducer(producer, config);

            // 1. Publish an Agent Card
            AgentCard card = new AgentCard();
            card.setName("Demo Kafka Agent");
            card.setDescription("A2A agent that consumes from Kafka and produces status to Kafka.");
            card.setUrl("kafka://a2a");
            card.setVersion("1.0.0");
            card.setDefaultInputModes(List.of("application/json", "text/plain"));
            card.setDefaultOutputModes(List.of("application/json"));
            AgentCard.AgentCapabilities caps = new AgentCard.AgentCapabilities();
            caps.setStreaming(true);
            caps.setPushNotifications(false);
            card.setCapabilities(caps);
            a2aProducer.publishAgentCard(card, "demo-agent");
            System.out.println("Published Agent Card to " + config.getAgentCardsTopic());

            // 2. Send a message/send request
            Message msg = new Message();
            msg.setRole("user");
            msg.setMessageId(UUID.randomUUID().toString());
            msg.setParts(List.of(new TextPart("Hello from A2A Kafka demo")));
            MessageSendParams params = new MessageSendParams();
            params.setMessage(msg);
            String taskKey = "task-" + UUID.randomUUID();
            a2aProducer.sendMessage(params, taskKey);
            System.out.println("Sent message/send to " + config.getRequestsTopic() + " key=" + taskKey);

            // 3. Optionally publish a status update (as if agent responded)
            TaskStatus status = new TaskStatus();
            status.setState(TaskState.SUBMITTED);
            status.setTimestamp(Instant.now().toString());
            TaskStatusUpdateEvent evt = new TaskStatusUpdateEvent();
            evt.setTaskId(taskKey);
            evt.setContextId("ctx-demo");
            evt.setStatus(status);
            evt.setFinal(false);
            a2aProducer.sendStatusUpdate(evt);
            System.out.println("Sent status-update to " + config.getUpdatesTopic());

            a2aProducer.flush();
            a2aProducer.close();
        }
        System.out.println("Demo done.");
    }
}
