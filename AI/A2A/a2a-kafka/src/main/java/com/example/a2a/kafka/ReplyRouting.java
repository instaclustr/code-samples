package com.example.a2a.kafka;

import com.example.a2a.kafka.model.MessageSendParams;

/**
 * Per-client reply routing for A2A over Kafka.
 * Clients set {@link #METADATA_REPLY_TOPIC} on message/send; agents publish updates to that topic.
 */
public final class ReplyRouting {

    public static final String METADATA_REPLY_TOPIC = "replyTopic";
    public static final String BENCH_REPLY_TOPIC_PREFIX = "a2a.updates.bench-";

    private ReplyRouting() {}

    /** Dedicated updates topic for benchmark harness worker {@code workerId}. */
    public static String benchReplyTopic(int workerId) {
        return BENCH_REPLY_TOPIC_PREFIX + workerId;
    }

    /** Reply topic from request metadata, or {@code defaultTopic} when absent. */
    public static String extractReplyTopic(MessageSendParams params, String defaultTopic) {
        if (params != null && params.getMetadata() != null) {
            Object value = params.getMetadata().get(METADATA_REPLY_TOPIC);
            if (value instanceof String topic && !topic.isBlank()) {
                return topic;
            }
        }
        return defaultTopic;
    }
}
