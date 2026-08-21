package com.example.a2a.kafka;

import com.example.a2a.kafka.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Produces A2A protocol messages to Kafka topics.
 * Methods map to A2A JSON-RPC methods; payloads are sent as A2AEnvelope (JSON).
 */
public class A2AKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(A2AKafkaProducer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules();

    private final KafkaProducer<String, String> producer;
    private final A2AKafkaConfig config;

    public A2AKafkaProducer(KafkaProducer<String, String> producer, A2AKafkaConfig config) {
        this.producer = producer;
        this.config = config;
    }

    /**
     * Send a message/send request (client → agent). Key = taskId or sessionId for ordering.
     */
    public void sendMessage(MessageSendParams params, String partitionKey) {
        String id = UUID.randomUUID().toString();
        A2AEnvelope envelope = A2AEnvelope.request("message/send", params, id);
        send(config.getRequestsTopic(), partitionKey, envelope);
    }

    public String getUpdatesTopic() {
        return config.getUpdatesTopic();
    }

    /**
     * Publish a task status update (agent → client). Key = taskId.
     */
    public void sendStatusUpdate(TaskStatusUpdateEvent event) {
        sendStatusUpdate(event, config.getUpdatesTopic());
    }

    public void sendStatusUpdate(TaskStatusUpdateEvent event, String updatesTopic) {
        String id = UUID.randomUUID().toString();
        A2AEnvelope envelope = A2AEnvelope.result("status-update", event, id);
        send(updatesTopic, event.getTaskId(), envelope);
    }

    /**
     * Publish an artifact update (agent → client). Key = taskId.
     */
    public void sendArtifactUpdate(TaskArtifactUpdateEvent event) {
        sendArtifactUpdate(event, config.getUpdatesTopic());
    }

    public void sendArtifactUpdate(TaskArtifactUpdateEvent event, String updatesTopic) {
        String id = UUID.randomUUID().toString();
        A2AEnvelope envelope = A2AEnvelope.result("artifact-update", event, id);
        send(updatesTopic, event.getTaskId(), envelope);
    }

    /**
     * Publish a full Task as result (e.g. after message/send completion). Key = task.id.
     */
    public void sendTaskResult(Task task, String envelopeId) {
        sendTaskResult(task, envelopeId, config.getUpdatesTopic());
    }

    public void sendTaskResult(Task task, String envelopeId, String updatesTopic) {
        A2AEnvelope envelope = A2AEnvelope.result("task", task, envelopeId != null ? envelopeId : UUID.randomUUID().toString());
        send(updatesTopic, task.getId(), envelope);
    }

    /**
     * Publish an Agent Card for discovery. Key = agent URL or id.
     */
    public void publishAgentCard(AgentCard card, String partitionKey) {
        String key = partitionKey != null ? partitionKey : (card.getUrl() != null ? card.getUrl() : card.getName());
        String id = UUID.randomUUID().toString();
        A2AEnvelope envelope = A2AEnvelope.result("agent-card", card, id);
        send(config.getAgentCardsTopic(), key, envelope);
    }

    public void send(String topic, String key, A2AEnvelope envelope) {
        try {
            String value = MAPPER.writeValueAsString(envelope);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
            producer.send(record, (meta, ex) -> {
                if (ex != null) {
                    log.warn("Send failed for {}: {}", topic, ex.getMessage());
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Serialization error", e);
            throw new RuntimeException(e);
        }
    }

    public void flush() {
        producer.flush();
    }

    /** Flush pending sends. Does not close the underlying KafkaProducer (caller owns it). */
    public void close() {
        producer.flush();
    }
}
