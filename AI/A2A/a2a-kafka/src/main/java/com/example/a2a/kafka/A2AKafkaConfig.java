package com.example.a2a.kafka;

import java.util.Properties;

/**
 * Topic names and Kafka config for A2A over Kafka.
 * <p>
 * Topic layout:
 * <ul>
 *   <li>{@code a2a.requests} – Client → Agent: message/send, tasks/cancel, etc. Key: taskId or sessionId.</li>
 *   <li>{@code a2a.updates} – Agent → Client: status updates, messages, artifact events. Key: taskId.</li>
 *   <li>{@code a2a.agent-cards} – Agent Card announcements for discovery. Key: agent URL or id.</li>
 * </ul>
 */
public class A2AKafkaConfig {

    public static final String DEFAULT_TOPIC_PREFIX = "a2a";
    public static final String TOPIC_REQUESTS = "requests";
    public static final String TOPIC_UPDATES = "updates";
    public static final String TOPIC_AGENT_CARDS = "agent-cards";

    private final String topicPrefix;

    public A2AKafkaConfig() {
        this(DEFAULT_TOPIC_PREFIX);
    }

    public A2AKafkaConfig(String topicPrefix) {
        this.topicPrefix = topicPrefix == null || topicPrefix.isBlank() ? DEFAULT_TOPIC_PREFIX : topicPrefix;
    }

    public String getRequestsTopic() {
        return topicPrefix + "." + TOPIC_REQUESTS;
    }

    public String getUpdatesTopic() {
        return topicPrefix + "." + TOPIC_UPDATES;
    }

    public String getAgentCardsTopic() {
        return topicPrefix + "." + TOPIC_AGENT_CARDS;
    }

    /** Build producer properties; caller must set bootstrap.servers and serializers. */
    public static Properties baseProducerProps() {
        Properties p = new Properties();
        p.setProperty("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        p.setProperty("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        p.setProperty("acks", "1");
        return p;
    }

    /** Build consumer properties; caller must set bootstrap.servers, group.id, deserializers. */
    public static Properties baseConsumerProps(String groupId) {
        Properties p = new Properties();
        p.setProperty("group.id", groupId);
        p.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        p.setProperty("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        p.setProperty("auto.offset.reset", "earliest");
        return p;
    }
}
