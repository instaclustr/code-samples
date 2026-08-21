package com.example.a2a.kafka;

import com.example.a2a.kafka.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumes A2A protocol messages from Kafka and dispatches to handlers.
 */
public class A2AKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(A2AKafkaConsumer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules();

    private final KafkaConsumer<String, String> consumer;
    private final A2AKafkaConfig config;
    private final A2AMessageHandler handler;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public A2AKafkaConsumer(KafkaConsumer<String, String> consumer, A2AKafkaConfig config, A2AMessageHandler handler) {
        this.consumer = consumer;
        this.config = config;
        this.handler = handler;
    }

    /**
     * Subscribe to requests topic and start polling. Runs until {@link #stop()} is called.
     */
    public void runRequestsLoop() {
        consumer.subscribe(Collections.singletonList(config.getRequestsTopic()));
        log.info("Subscribed to {}", config.getRequestsTopic());
        while (running.get()) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            for (ConsumerRecord<String, String> record : records) {
                handleEnvelope(record.topic(), record.key(), record.value());
            }
        }
    }

    /**
     * Subscribe to updates topic and start polling.
     */
    public void runUpdatesLoop() {
        runUpdatesLoop(config.getUpdatesTopic());
    }

    public void runUpdatesLoop(String updatesTopic) {
        runUpdatesLoop(updatesTopic, null);
    }

    /**
     * Subscribe to a specific updates/reply topic. {@code onReady} runs once after the first
     * poll completes (consumer group joined and partitions assigned).
     */
    public void runUpdatesLoop(String updatesTopic, Runnable onReady) {
        consumer.subscribe(Collections.singletonList(updatesTopic));
        log.info("Subscribed to {}", updatesTopic);
        // Join group, then skip retained history on per-client reply topics (benchmark clients).
        for (int i = 0; i < 60 && consumer.assignment().isEmpty() && running.get(); i++) {
            consumer.poll(Duration.ofMillis(500));
        }
        var assigned = consumer.assignment();
        if (!assigned.isEmpty()) {
            consumer.seekToEnd(assigned);
        }
        // Position after seek; discard any records from the pre-seek poll window.
        consumer.poll(Duration.ofMillis(100));
        if (onReady != null) {
            onReady.run();
        }
        while (running.get()) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            for (ConsumerRecord<String, String> record : records) {
                handleEnvelope(record.topic(), record.key(), record.value());
            }
        }
    }

    /**
     * Subscribe to both requests and updates (e.g. for an agent that consumes requests and produces updates).
     */
    public void runAllLoop() {
        consumer.subscribe(java.util.List.of(config.getRequestsTopic(), config.getUpdatesTopic()));
        log.info("Subscribed to {} and {}", config.getRequestsTopic(), config.getUpdatesTopic());
        while (running.get()) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            for (ConsumerRecord<String, String> record : records) {
                handleEnvelope(record.topic(), record.key(), record.value());
            }
        }
    }

    /**
     * Subscribe to all A2A topics (requests, updates, agent-cards) for end-to-end demo.
     */
    public void runAllTopicsLoop() {
        consumer.subscribe(java.util.List.of(
                config.getRequestsTopic(),
                config.getUpdatesTopic(),
                config.getAgentCardsTopic()));
        log.info("Subscribed to {}, {}, {}", config.getRequestsTopic(), config.getUpdatesTopic(), config.getAgentCardsTopic());
        while (running.get()) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            for (ConsumerRecord<String, String> record : records) {
                handleEnvelope(record.topic(), record.key(), record.value());
            }
        }
    }

    public void stop() {
        running.set(false);
    }

    private void handleEnvelope(String topic, String key, String value) {
        try {
            A2AEnvelope envelope = MAPPER.readValue(value, A2AEnvelope.class);
            String method = envelope.getMethod();
            if (method == null) {
                log.warn("Envelope without method, key={}", key);
                return;
            }
            switch (method) {
                case "message/send" -> {
                    MessageSendParams params = MAPPER.convertValue(envelope.getParams(), MessageSendParams.class);
                    handler.onMessageSend(key, params);
                }
                case "status-update" -> {
                    TaskStatusUpdateEvent event = MAPPER.convertValue(envelope.getResult(), TaskStatusUpdateEvent.class);
                    handler.onStatusUpdate(event);
                }
                case "artifact-update" -> {
                    TaskArtifactUpdateEvent event = MAPPER.convertValue(envelope.getResult(), TaskArtifactUpdateEvent.class);
                    handler.onArtifactUpdate(event);
                }
                case "task" -> {
                    Task task = MAPPER.convertValue(envelope.getResult(), Task.class);
                    handler.onTaskResult(task);
                }
                case "agent-card" -> {
                    AgentCard card = MAPPER.convertValue(envelope.getResult(), AgentCard.class);
                    handler.onAgentCard(card);
                }
                default -> handler.onUnknown(key, envelope);
            }
        } catch (Exception e) {
            log.error("Failed to handle record key={}", key, e);
        }
    }

    public void close() {
        consumer.close();
    }

    /**
     * Implement to handle A2A messages received from Kafka.
     */
    public interface A2AMessageHandler {
        default void onMessageSend(String partitionKey, MessageSendParams params) {}
        default void onStatusUpdate(TaskStatusUpdateEvent event) {}
        default void onArtifactUpdate(TaskArtifactUpdateEvent event) {}
        default void onTaskResult(Task task) {}
        default void onAgentCard(AgentCard card) {}
        default void onUnknown(String key, A2AEnvelope envelope) {
            log.debug("Unknown method: {} key={}", envelope.getMethod(), key);
        }
    }
}
